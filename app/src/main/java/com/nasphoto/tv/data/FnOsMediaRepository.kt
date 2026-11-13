package com.nasphoto.tv.data

import com.nasphoto.tv.data.fn.FnOsApi
import com.nasphoto.tv.data.fn.FnOsProbeResult
import com.nasphoto.tv.data.fn.FnOsStats
import com.nasphoto.tv.data.fn.toFnOsAccess
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.AlbumType
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.NasConnection
import com.nasphoto.tv.data.model.NasFolder
import com.nasphoto.tv.data.model.Person
import com.nasphoto.tv.data.model.RAW_EXTENSIONS
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.time.LocalDate

/**
 * ================= 飞牛相册数据源 =================
 *
 * 直接消费飞牛相册官方接口：
 *  - 时间线 / 相册 / 共享相册；
 *  - 服务端缩略图（m）与原图（o）；
 *  - 视频串流、实况照片动态片段（`additional.thumbnail.videoUrl`）。
 *
 * 所有图片/视频地址都是绝对 URL，鉴权由 [com.nasphoto.tv.data.fn.FnOsTransport]
 * 统一注入 AccessToken。
 */
class FnOsMediaRepository(
    override val connection: NasConnection,
) : MediaRepository, AutoCloseable {

    private val access = connection.toFnOsAccess()
    private val api = FnOsApi(access)
    private val baseUrl = access.baseUrl

    private var recentCache: List<MediaItem> = emptyList()
    private val albumCache = mutableMapOf<String, List<MediaItem>>()
    private val smartAlbums = FnOsSmartAlbums(api::getJson, api::postJson, ::parseMedia, ::absolute)

    suspend fun probe(): FnOsProbeResult = api.testConnection()

    /**
     * 账号开启两步验证时，密码登录只算「半程」：调用这里提交 6 位动态码，
     * 同一个 [FnOsApi] 会话（含未关闭的 WebSocket）随即转为已登录。
     */
    suspend fun verifyTwoFactor(code: String) = api.verifyTwoFactor(code)

    fun hasPendingTwoFactor(): Boolean = api.hasPendingTwoFactor()

    /** 放弃两步验证（用户按 BACK）：下次加载会重新走密码登录 */
    fun cancelTwoFactor() = api.cancelTwoFactor()

    override suspend fun ping(): Boolean = probe().ok

    suspend fun stats(): FnOsStats = api.stat()

    override suspend fun getRecent(limit: Int): List<MediaItem> {
        // 飞牛服务端单页最多 500 条：以前一次性传 limit=20000，只拿回最新 500 张，
        // 于是「图库」只显示最近几年。这里按 offset 逐页拉全（实测全库 5294 张，11 页）。
        val out = ArrayList<MediaItem>(minOf(limit, 8_000))
        var offset = 0
        while (out.size < limit && offset < MAX_GALLERY_ITEMS) {
            val data = api.getJson(
                path = GALLERY_LIST,
                params = listOf(
                    "start_time" to "1970:01:01 00:00:00",
                    "end_time" to "2099:12:31 23:59:59",
                    "limit" to GALLERY_PAGE_SIZE.toString(),
                    "offset" to offset.toString(),
                    "mode" to "index",
                ),
            )
            val page = parseMediaList(data)
            if (page.isEmpty()) break
            out += page
            offset += page.size
            val hasNext = data.optBoolean("hasNext", page.size >= GALLERY_PAGE_SIZE)
            if (!hasNext || page.size < GALLERY_PAGE_SIZE) break
        }
        val result = out.take(limit)
        recentCache = result
        return result
    }

    override suspend fun getAlbums(): List<Album> {
        val own = api.getJson(
            path = ALBUM_LIST,
            params = listOf(
                "sort_direction" to "desc",
                "sort_by" to "date_time",
                "offset" to "0",
                "limit" to "300",
            ),
        )
        // 共享相册是独立路由；失败不影响自己的相册
        val shared = runCatching {
            api.getJson(
                path = SHARED_ALBUMS,
                params = listOf(
                    "offset" to "0",
                    "limit" to "300",
                    "sort_by" to "share_mod_time",
                    "sort_direction" to "desc",
                ),
            )
        }.getOrNull()

        return (parseAlbums(own) + parseAlbums(shared)).distinctBy { it.id }
    }

    override suspend fun getSmartAlbums(): List<Album> = smartAlbums.getAlbums()

    override suspend fun getSmartAlbumMedia(albumId: String): List<MediaItem> = smartAlbums.getMedia(albumId)

    /**
     * 某个相册下的全部媒体（按拍摄时间倒序）。
     *
     * ⚠️ 真实相册可能远超单页上限：`album/photos` 单次最多返回一页，
     * 早先只取 `offset=0&limit=500`，导致 2000+ 张的大相册永远只显示前 500 项
     * （用户反馈「相册中只能显示前 500 张左右」）。这里按 offset 逐页拉全：
     *  - 优先用服务端返回的 `total` 判断终点；
     *  - `total` 缺失时以「空页」为终点（满页就继续，避免服务端单页上限 < PAGE_SIZE 时误停）；
     *  - 按 id 去重，防止分页边界重复导致死循环；
     *  - 保留 [MAX_ALBUM_ITEMS] 兜底，防止接口异常时无限拉取。
     */
    override suspend fun getMedia(albumId: String): List<MediaItem> {
        albumCache[albumId]?.let { return it }
        val collected = LinkedHashMap<String, MediaItem>()
        var offset = 0
        var total = -1
        while (true) {
            val data = api.getJson(
                path = ALBUM_PHOTOS,
                params = listOf(
                    "album_id" to albumId,
                    "sort_by" to "date_time",
                    "sort_direction" to "desc",
                    "offset" to offset.toString(),
                    "limit" to ALBUM_PAGE_SIZE.toString(),
                ),
            )
            // 只信任「> 0」的 total：部分版本对 album/photos 不返回 total 或默认 0，
            // 若把 0 当成终点会在第一页就误停。
            if (total <= 0 && data.has("total")) {
                data.optInt("total", -1).takeIf { it > 0 }?.let { total = it }
            }
            val page = parseMediaList(data).map { it.copy(albumId = albumId) }
            val before = collected.size
            for (item in page) collected.putIfAbsent(item.id, item)
            offset += page.size
            when {
                page.isEmpty() -> break                    // 已到末尾
                total > 0 && collected.size >= total -> break // 已取满服务端报告的总数
                collected.size == before -> break           // 整页都是重复项：边界异常，继续也无新增
                offset >= MAX_ALBUM_ITEMS -> break          // 兜底上限
            }
        }
        return collected.values.toList().also { albumCache[albumId] = it }
    }

    override suspend fun search(query: String): List<MediaItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val pool = recentCache.ifEmpty { getRecent(300) }
        return pool.filter { item ->
            item.title.lowercase().contains(q) ||
                item.dateTaken.toString().contains(q) ||
                item.extension.lowercase() == q
        }
    }

    /** 屏保照片池：最近的照片（不含视频），实况照片会带动态片段 */
    override suspend fun getScreensaverPhotos(): List<MediaItem> {
        val data = api.getJson(
            path = GALLERY_LIST,
            params = listOf(
                "start_time" to "1970:01:01 00:00:00",
                "end_time" to "2099:12:31 23:59:59",
                "limit" to "300",
                "offset" to "0",
                "mode" to "index",
            ),
        )
        return parseMediaList(data).filter { it.type == MediaType.PHOTO }
    }

    /**
     * 人物列表：`ai-person/list`（一次全量）。
     * 返回字段在端上做过防御：不同版本的飞牛用 `name`/`personName`、`coverUrl`/`cover` 等
     * 多种命名，这里按优先级依次尝试，全都没有就留空（UI 会画首字头像兜底）。
     */
    override suspend fun getPeople(): List<Person> {
        val data = api.getJson(
            path = PERSON_LIST,
            params = listOf("getAll" to "true", "limit" to "-1", "orderBy" to "0"),
        )
        val list = data.optJSONArray("list") ?: return emptyList()
        return buildList {
            for (i in 0 until list.length()) {
                val json = list.optJSONObject(i) ?: continue
                if (json.optInt("isHide", 0) == 1) continue
                val id = json.optLong("id", 0L)
                if (id <= 0L) continue
                val name = json.optString("name")
                    .ifBlank { json.optString("personName") }
                    .ifBlank { json.optString("tagName") }
                // ai-person 列表只返回 faceId，不带任何封面字段；人物头像就是
                // /p/api/v1/stream/face/{faceId} 这张人脸裁图（实测返回 image/jpeg）。
                val faceId = json.optLong("faceId", 0L)
                val cover = absolute(
                    json.optString("coverUrl")
                        .ifBlank { json.optString("cover") }
                        .ifBlank { json.optString("avatarUrl") }
                        .ifBlank { json.optString("posterUrl") }
                        .ifBlank { if (faceId > 0L) "/p/api/v1/stream/face/$faceId" else "" },
                )
                add(
                    Person(
                        id = id.toString(),
                        name = name,
                        coverUrl = cover,
                        itemCount = json.optInt("itemCount", json.optInt("photoCount", 0)),
                    ),
                )
            }
        }.sortedByDescending { it.itemCount }
    }

    /**
     * 人物照片池：取照片最多的前 8 个人，每人最近 3 天 → 逐日取图（屏保用）。
     * 真实接口的 photoLibrary/list 只接受单日范围，且需要 album_id（填 personId）
     * 与 camelCase/snake_case 两套时间参数，这里严格按该约束请求。
     */
    override suspend fun getPeoplePhotos(limit: Int): List<MediaItem> {
        val people = getPeople()
        if (people.isEmpty()) return emptyList()

        val pools = coroutineScope {
            people.take(8)
                .map { person -> async { fetchPersonPhotos(person.id, maxDays = 3, perDay = 60) } }
                .awaitAll()
        }
        return pools.flatten()
            .distinctBy { it.id }
            .sortedByDescending { it.dateTaken }
            .take(limit)
    }

    /**
     * 轻量版：只取时间线前 [maxDays] 天、每天最多 [perDay] 张，用于人物卡片封面聚合。
     * 详情页请用 [getPersonPhotos]（它会遍历完整时间线并翻页）。
     */
    private suspend fun fetchPersonPhotos(
        personId: String,
        maxDays: Int,
        perDay: Int,
        stopAt: Int = 0,
    ): List<MediaItem> {
        val timeline = runCatching {
            api.getJson(PERSON_TIMELINE, listOf("id" to personId))
        }.getOrNull() ?: return emptyList()
        val days = timeline.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<MediaItem>()
        for (i in 0 until minOf(days.length(), maxDays)) {
            if (stopAt > 0 && out.size >= stopAt) break
            val day = days.optJSONObject(i) ?: continue
            val ymd = "%04d:%02d:%02d".format(
                day.optInt("year", 0), day.optInt("month", 0), day.optInt("day", 0),
            )
            if (ymd.startsWith("0000")) continue
            val photos = runCatching {
                api.getJson(
                    path = PERSON_PHOTOS,
                    params = listOf(
                        "album_id" to personId,
                        "endTime" to "$ymd 23:59:59",
                        "end_time" to "$ymd 23:59:59",
                        "limit" to perDay.toString(),
                        "offset" to "0",
                        "personId" to personId,
                        "startTime" to "$ymd 00:00:00",
                        "start_time" to "$ymd 00:00:00",
                    ),
                )
            }.getOrNull() ?: continue
            out += parseMediaList(photos).filter { it.type == MediaType.PHOTO }
        }
        return out
    }

    /**
     * 某个人的照片：先拿他/她的时间线（按天聚合），再逐日拉取。
     *
     * 性能约束（实机踩过）：这里是**串行**请求，每天一次。早先写死「60 天 × 每天 200 张」，
     * 结果一个只有 16 张照片的人也要发满 60 次请求——电视端要等十几秒，模拟器直接 OOM。
     * 现在改成：逐日拉取，凑够 [limit] 就停；再留一个 30 天的硬上限兜底，
     * 保证「照片少的人很快返回、照片多的人拿到足够填满网格的量」。
     */
    override suspend fun getPersonPhotos(personId: String, limit: Int): List<MediaItem> {
        // 旧实现只看时间线前 30 天、每天最多 200 张，所以几千张的人物只显示出一两百张。
        // 现在遍历完整时间线，并在单日内按 offset 继续翻页，直到该天取完或达到上限。
        val timeline = runCatching {
            api.getJson(PERSON_TIMELINE, listOf("id" to personId))
        }.getOrNull() ?: return emptyList()
        val days = timeline.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<MediaItem>()
        for (i in 0 until days.length()) {
            if (out.size >= limit) break
            val day = days.optJSONObject(i) ?: continue
            val year = day.optInt("year", 0)
            val month = day.optInt("month", 0)
            val date = day.optInt("day", 0)
            if (year <= 0 || month <= 0 || date <= 0) continue
            // 服务端只接受冒号格式（"2026:10:24 00:00:00"）；用短横线会报 invalid params。
            val ymd = "%04d:%02d:%02d".format(year, month, date)
            val dayCount = day.optInt("itemCount", 0)
            var offset = 0
            var gotInDay = 0
            while (out.size < limit && (dayCount <= 0 || gotInDay < dayCount)) {
                val photos = runCatching {
                    api.getJson(
                        path = PERSON_PHOTOS,
                        params = listOf(
                            "album_id" to personId,
                            "endTime" to "$ymd 23:59:59",
                            "end_time" to "$ymd 23:59:59",
                            "limit" to PERSON_PAGE_SIZE.toString(),
                            "offset" to offset.toString(),
                            "personId" to personId,
                            "startTime" to "$ymd 00:00:00",
                            "start_time" to "$ymd 00:00:00",
                        ),
                    )
                }.getOrNull() ?: break
                val page = parseMediaList(photos).filter { it.type == MediaType.PHOTO }
                if (page.isEmpty()) break
                out += page
                offset += page.size
                gotInDay += page.size
                if (page.size < PERSON_PAGE_SIZE) break
            }
        }
        return out.sortedByDescending { it.dateTaken }
    }

    // ------------------------------------------------------------------ 文件夹视图

    @Volatile
    private var folderCache: List<NasFolder> = emptyList()

    override suspend fun getFolders(): List<NasFolder> {
        // photo/folder/list 带真实的 photoCount/videoCount（folder/view 里这两个字段恒为 0），
        // 卡片上的「N 项」需要它；失败再退回 folder/view 只取目录结构。
        val arr = runCatching {
            api.getJson(FOLDER_LIST, emptyList()).optJSONArray("list")
        }.getOrNull() ?: runCatching {
            api.getJson(FOLDER_VIEW, emptyList()).optJSONArray("myFile")
        }.getOrNull() ?: return emptyList()
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val path = o.optString("folderPath")
            NasFolder(
                id = o.optString("folderId"),
                path = path,
                name = path.trimEnd('/').substringAfterLast('/').ifBlank { path },
                photoCount = o.optInt("photoCount", 0),
                videoCount = o.optInt("videoCount", 0),
            )
        }
        folderCache = list
        return list
    }

    override suspend fun getFolderMedia(folderId: String, limit: Int): List<MediaItem> {
        val path = folderCache.firstOrNull { it.id == folderId }?.path
            ?: getFolders().firstOrNull { it.id == folderId }?.path
            ?: return emptyList()
        val out = ArrayList<MediaItem>()
        var offset = 0
        while (out.size < limit) {
            val data = api.getJson(
                FOLDER_FILES,
                listOf(
                    "folderId" to folderId,
                    "folderPath" to path,
                    "offset" to offset.toString(),
                    "limit" to FOLDER_PAGE_SIZE.toString(),
                ),
            )
            val page = parseMediaList(data)
            if (page.isEmpty()) break
            out += page
            offset += page.size
            val hasNext = data.optBoolean("hasNext", page.size >= FOLDER_PAGE_SIZE)
            if (!hasNext || page.size < FOLDER_PAGE_SIZE) break
        }
        return out.sortedByDescending { it.dateTaken }
    }

    override suspend fun getMediaItem(id: String): MediaItem? =
        recentCache.firstOrNull { it.id == id }
            ?: albumCache.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }

    override fun close() = api.close()

    // ------------------------------------------------------------------ 解析

    private fun parseMediaList(data: JSONObject): List<MediaItem> {
        val list = data.optJSONArray("list") ?: return emptyList()
        return buildList {
            for (i in 0 until list.length()) {
                list.optJSONObject(i)?.let(::parseMedia)?.let { add(it) }
            }
        }
    }

    private fun parseMedia(json: JSONObject): MediaItem? {
        val id = json.optLong("id", 0L)
        if (id <= 0L) return null

        val thumb = json.optJSONObject("additional")?.optJSONObject("thumbnail")
        val fileName = json.optString("fileName")
        val isVideo = json.optString("category").equals("video", ignoreCase = true)

        val mUrl = thumb?.optString("mUrl").orEmpty()
        val sUrl = thumb?.optString("sUrl").orEmpty()
        val xsUrl = thumb?.optString("xsUrl").orEmpty()
        val originalUrl = thumb?.optString("originalUrl").orEmpty()
        val videoUrl = thumb?.optString("videoUrl").orEmpty()

        val extension = fileName.substringAfterLast('.', "").uppercase()
        val isRaw = extension in RAW_EXTENSIONS

        val thumbUrl = absolute(mUrl.ifBlank { sUrl.ifBlank { xsUrl } })
        val fullUrl = when {
            isVideo -> absolute(videoUrl.ifBlank { "/p/api/v1/stream/v/$id" })
            // RAW 端侧没有解码器：直接看 NAS 生成的高清 JPG 预览（m = 1280px），
            // 原图（o）是相机原始文件，交给 Coil 只会得到一张占位图。
            isRaw -> absolute(mUrl.ifBlank { sUrl.ifBlank { originalUrl } })
            else -> absolute(originalUrl.ifBlank { mUrl })
        }
        val width = json.optInt("width", 0)
        val height = json.optInt("height", 0)
        val isLive = json.optInt("isLive", 0) == 1 && videoUrl.isNotBlank()

        return MediaItem(
            id = id.toString(),
            title = fileName.substringBeforeLast('.').ifBlank {
                if (isVideo) "未命名视频" else "未命名照片"
            },
            type = if (isVideo) MediaType.VIDEO else MediaType.PHOTO,
            url = fullUrl.ifBlank { thumbUrl },
            thumbnailUrl = thumbUrl.ifBlank { fullUrl },
            isLive = isLive,
            liveUrl = if (isLive) absolute(videoUrl) else "",
            dateTaken = parseDate(json.optString("dateTime").ifBlank { json.optString("photoDateTime") }),
            durationMs = json.optLong("durationMs", json.optLong("duration", 0L)),
            sizeBytes = json.optLong("fileSize", 0L),
            resolution = if (width > 0 && height > 0) "${width}x$height" else "",
            albumId = "",
            starred = json.optInt("isCollect", 0) == 1,
            extension = extension,
        )
    }

    private fun parseAlbums(data: JSONObject?): List<Album> {
        val list = data?.optJSONArray("list") ?: return emptyList()
        return buildList {
            for (i in 0 until list.length()) {
                val json = list.optJSONObject(i) ?: continue
                val id = json.optLong("albumId", 0L)
                if (id <= 0L) continue
                val owner = json.optString("ownerName")
                add(
                    Album(
                        id = id.toString(),
                        name = json.optString("albumName").ifBlank { "未命名相册" },
                        coverUrl = absolute(
                            json.optString("posterImgUrl").ifBlank { json.optString("posterUrl") },
                        ),
                        itemCount = json.optInt("photoCount", 0) + json.optInt("videoCount", 0),
                        type = AlbumType.FOLDER,
                        subtitle = if (owner.isNotBlank()) "来自 $owner" else "",
                    ),
                )
            }
        }
    }

    private fun parseDate(raw: String): LocalDate {
        if (raw.isBlank()) return LocalDate.now()
        val head = raw.trim().take(10).replace(':', '-')
        return runCatching { LocalDate.parse(head) }.getOrDefault(LocalDate.now())
    }

    private fun absolute(relative: String): String {
        if (relative.isBlank()) return ""
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        return baseUrl.trimEnd('/') + "/" + relative.trimStart('/')
    }

    private companion object {
        /** 相册分页的单页大小：实测服务端单页最多 500，沿用该值即可少走往返。 */
        const val ALBUM_PAGE_SIZE = 500

        /** 单个相册的分页兜底上限，防止服务端异常（如 total 缺失且永不返回空页）时无限拉取。 */
        const val MAX_ALBUM_ITEMS = 20_000

        const val GALLERY_PAGE_SIZE = 500
        const val MAX_GALLERY_ITEMS = 50_000
        const val PERSON_PAGE_SIZE = 200
        const val FOLDER_PAGE_SIZE = 200
        const val FOLDER_LIST = "/p/api/v1/photo/folder/list"
        const val FOLDER_VIEW = "/p/api/v1/photo/folder/view"
        const val FOLDER_FILES = "/p/api/v1/folder_view/getFileList"

        const val GALLERY_LIST = "/p/api/v1/gallery/getList"
        const val ALBUM_LIST = "/p/api/v1/album/list"
        const val ALBUM_PHOTOS = "/p/api/v1/album/photos"
        const val SHARED_ALBUMS = "/p/api/v1/album_grant/list_to_me"
        const val PERSON_LIST = "/p/api/v1/ai-person/list"
        const val PERSON_TIMELINE = "/p/api/v1/ai-person/photoLibrary/timeLine"
        const val PERSON_PHOTOS = "/p/api/v1/ai-person/photoLibrary/list"
    }
}
