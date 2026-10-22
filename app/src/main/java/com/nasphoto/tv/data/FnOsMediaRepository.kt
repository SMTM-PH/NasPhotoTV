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

    suspend fun probe(): FnOsProbeResult = api.testConnection()

    override suspend fun ping(): Boolean = probe().ok

    suspend fun stats(): FnOsStats = api.stat()

    override suspend fun getRecent(limit: Int): List<MediaItem> {
        val data = api.getJson(
            path = GALLERY_LIST,
            params = listOf(
                "start_time" to "1970:01:01 00:00:00",
                "end_time" to "2099:12:31 23:59:59",
                "limit" to limit.toString(),
                "offset" to "0",
                "mode" to "index",
            ),
        )
        return parseMediaList(data).also { recentCache = it }
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

    override suspend fun getMedia(albumId: String): List<MediaItem> {
        albumCache[albumId]?.let { return it }
        val data = api.getJson(
            path = ALBUM_PHOTOS,
            params = listOf(
                "album_id" to albumId,
                "sort_by" to "date_time",
                "sort_direction" to "desc",
                "offset" to "0",
                "limit" to "500",
            ),
        )
        return parseMediaList(data)
            .map { it.copy(albumId = albumId) }
            .also { albumCache[albumId] = it }
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
     * 人物照片池：ai-person/list → 每个人最近 3 天 → 逐日取图。
     * 真实接口的 photoLibrary/list 只接受单日范围，且需要 album_id（填 personId）
     * 与 camelCase/snake_case 两套时间参数，这里严格按该约束请求。
     */
    override suspend fun getPeoplePhotos(limit: Int): List<MediaItem> {
        val data = api.getJson(
            path = PERSON_LIST,
            params = listOf("getAll" to "true", "limit" to "-1", "orderBy" to "0"),
        )
        val list = data.optJSONArray("list") ?: return emptyList()
        val people = buildList {
            for (i in 0 until list.length()) {
                val person = list.optJSONObject(i) ?: continue
                if (person.optInt("isHide", 0) == 1) continue
                val id = person.optLong("id", 0L)
                if (id > 0L) add(id to person.optInt("itemCount", 0))
            }
        }
        if (people.isEmpty()) return emptyList()

        val pools = coroutineScope {
            people.sortedByDescending { it.second }
                .take(8)
                .map { (personId, _) -> async { fetchPersonPhotos(personId) } }
                .awaitAll()
        }
        return pools.flatten()
            .distinctBy { it.id }
            .sortedByDescending { it.dateTaken }
            .take(limit)
    }

    private suspend fun fetchPersonPhotos(personId: Long): List<MediaItem> {
        val timeline = runCatching {
            api.getJson(PERSON_TIMELINE, listOf("id" to personId.toString()))
        }.getOrNull() ?: return emptyList()
        val days = timeline.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<MediaItem>()
        for (i in 0 until minOf(days.length(), 3)) {
            val day = days.optJSONObject(i) ?: continue
            val year = day.optInt("year", 0)
            val month = day.optInt("month", 0)
            val date = day.optInt("day", 0)
            if (year <= 0 || month <= 0 || date <= 0) continue
            val ymd = "%04d:%02d:%02d".format(year, month, date)
            val photos = runCatching {
                api.getJson(
                    path = PERSON_PHOTOS,
                    params = listOf(
                        "album_id" to personId.toString(),
                        "endTime" to "$ymd 23:59:59",
                        "end_time" to "$ymd 23:59:59",
                        "limit" to "60",
                        "offset" to "0",
                        "personId" to personId.toString(),
                        "startTime" to "$ymd 00:00:00",
                        "start_time" to "$ymd 00:00:00",
                    ),
                )
            }.getOrNull() ?: continue
            out += parseMediaList(photos).filter { it.type == MediaType.PHOTO }
        }
        return out
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
        const val GALLERY_LIST = "/p/api/v1/gallery/getList"
        const val ALBUM_LIST = "/p/api/v1/album/list"
        const val ALBUM_PHOTOS = "/p/api/v1/album/photos"
        const val SHARED_ALBUMS = "/p/api/v1/album_grant/list_to_me"
        const val PERSON_LIST = "/p/api/v1/ai-person/list"
        const val PERSON_TIMELINE = "/p/api/v1/ai-person/photoLibrary/timeLine"
        const val PERSON_PHOTOS = "/p/api/v1/ai-person/photoLibrary/list"
    }
}
