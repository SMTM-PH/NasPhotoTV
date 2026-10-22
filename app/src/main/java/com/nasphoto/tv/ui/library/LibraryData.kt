package com.nasphoto.tv.ui.library

import com.nasphoto.tv.data.favoriteItems
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.AlbumType
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.ui.AppRoute
import com.nasphoto.tv.ui.LibraryState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ================= 媒体库页数据辅助（library-dev 维护） =================
 *
 * 硬性要求：数据源可能暂时为空 / 字段缺失，这里所有派生逻辑都必须空安全，
 * 返回空列表而不是抛异常；UI 侧只消费这些结果。
 */

/** 顶栏 Tab 定义（顺序即 selectedTabIndex 语义） */
enum class LibraryTab(val label: String) {
    ALL("全部照片"),
    CHRONO("年 · 月 · 日"),
    ALBUMS("相册"),
    RECENT("最近添加"),
    SMART("收藏 · 人物 · 地点"),
}

/** 一次横向行的数据模型：标题 + 媒体列表（用于「最近添加」等） */
data class MediaRow(val key: String, val title: String, val items: List<MediaItem>)

/**
 * 时间线分桶（年 / 月 / 日共用一套模型）。
 * [items] 保持调用方传入的时间倒序，卡片 ENTER 时整份交给看图页，
 * 于是「左右键」在一个自然日内天然连贯。
 */
data class ChronoBucket(
    val key: String,
    /** 卡片上的大字：年份数字 / 「5 月」/ 「12 日」 */
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val items: List<MediaItem>,
)

/** Hero 区数据：主图 + 展示用的完整信息（全部在派生阶段算好，避免 UI 里做空判断） */
data class HeroInfo(
    val item: MediaItem,
    val title: String,
    val dateText: String,
    val metaText: String,
    val actionLabel: String,
    /** 点击后打开的列表（Hero 所在上下文），保证 index 有效 */
    val items: List<MediaItem>,
    val index: Int,
)

/**
 * Hero 取最近列表里的头条项。
 * 10-foot UI 里 Hero 是整个首页最大的视觉锚点，不能只看"最新一条"：
 * 选择第一张照片作为主视觉；没有照片时退回第一条。
 */
fun buildHero(state: LibraryState): HeroInfo? {
    val list = state.recent
    if (list.isEmpty()) return null
    val idx = pickHeroIndex(list)
    val item = list.getOrNull(idx) ?: return null
    val isVideo = item.type == MediaType.VIDEO
    val dateText = formatHeroDate(item.dateTaken)
    // 日期已单独一行展示，meta 只放分辨率/大小
    val meta = listOfNotNull(
        item.resolution.takeIf { it.isNotBlank() },
        formatBytes(item.sizeBytes).takeIf { item.sizeBytes > 0 },
    ).joinToString("  ·  ")
    return HeroInfo(
        item = item,
        title = item.title.ifBlank { if (isVideo) "未命名的视频" else "未命名的照片" },
        dateText = dateText,
        metaText = meta,
        actionLabel = if (isVideo) "播放" else "查看",
        items = list,
        index = idx,
    )
}

/**
 * Hero 取最近列表里的第一张照片（照片比视频封面更适合做满屏主视觉）；
 * 全是视频时退回第一条。
 */
private fun pickHeroIndex(list: List<MediaItem>): Int {
    val photo = list.indexOfFirst { it.type == MediaType.PHOTO }
    return if (photo >= 0) photo else 0
}

/** 「最近添加」行：recent 在前 N 条（不足则全给） */
fun recentRow(state: LibraryState): List<MediaItem> = state.recent.take(24)

/** 全部照片：recent 与所有媒体池合并去重，按时间倒序（数据源为空时返回空列表） */
fun allPhotos(state: LibraryState): List<MediaItem> {
    val merged = LinkedHashMap<String, MediaItem>()
    state.recent.forEach { merged.putIfAbsent(it.id, it) }
    state.albums.forEach { album ->
        state.mediaByAlbum[album.id]?.forEach { item -> merged.putIfAbsent(item.id, item) }
    }
    return merged.values.sortedByDescending { it.dateTaken }
}

/**
 * ================= 年 / 月 / 日 时间线 =================
 * 三级都用同一份「已按时间倒序」的列表逐级收窄：年 → 该年的月 → 该月的日。
 * 键（key）在同一份数据里唯一，UI 只需保存三个 key 就能定位当前层级。
 */

/** 按年份分桶（倒序） */
fun chronoYears(items: List<MediaItem>): List<ChronoBucket> {
    val byYear = items.groupBy { it.dateTaken.year }
    return byYear.keys.sortedDescending().map { year ->
        val list = byYear.getValue(year)
        ChronoBucket(
            key = "y$year",
            title = year.toString(),
            subtitle = countText(list),
            coverUrl = chronoCover(list),
            items = list,
        )
    }
}

/** 某一年里按月分桶（倒序） */
fun chronoMonths(items: List<MediaItem>): List<ChronoBucket> {
    val byMonth = items.groupBy { it.dateTaken.monthValue }
    return byMonth.keys.sortedDescending().map { month ->
        val list = byMonth.getValue(month)
        ChronoBucket(
            key = "m$month",
            title = "$month 月",
            subtitle = countText(list),
            coverUrl = chronoCover(list),
            items = list,
        )
    }
}

/** 某个月里按日分桶（倒序） */
fun chronoDays(items: List<MediaItem>): List<ChronoBucket> {
    val byDay = items.groupBy { it.dateTaken }
    return byDay.keys.sortedDescending().map { date ->
        val list = byDay.getValue(date)
        ChronoBucket(
            key = "d$date",
            title = "${date.dayOfMonth} 日",
            subtitle = "${zhWeekday(date)} · ${countText(list)}",
            coverUrl = chronoCover(list),
            items = list,
        )
    }
}

/** 分桶封面：取该时段最新一张的缩略图 */
private fun chronoCover(items: List<MediaItem>): String =
    items.firstOrNull { it.thumbnailUrl.isNotBlank() || it.url.isNotBlank() }
        ?.let { it.thumbnailUrl.ifBlank { it.url } }
        ?: ""

/**
 * ================= 回忆（Memories） =================
 *
 * 不依赖 NAS 的人脸/地点识别：从已加载的时间线里派生两类回忆，
 * 返回的结构就是 [ChronoBucket]，直接复用时间线卡片。
 *  - 往年今日：同月同日、更早年份
 *  - 往年这个月：同月、更早年份（已出现在「今天」的年份不重复）
 *
 * 没有匹配时返回空列表，UI 不该编造内容。
 */
fun buildMemories(items: List<MediaItem>, today: LocalDate = LocalDate.now()): List<ChronoBucket> {
    val past = items.filter { it.dateTaken.year < today.year }
    if (past.isEmpty()) return emptyList()

    val out = LinkedHashMap<String, ChronoBucket>()
    val sameMonth = past.filter { it.dateTaken.monthValue == today.monthValue }

    sameMonth
        .filter { it.dateTaken.dayOfMonth == today.dayOfMonth }
        .groupBy { it.dateTaken.year }
        .entries.sortedByDescending { it.key }
        .forEach { (year, list) ->
            out["mem-today-$year"] = ChronoBucket(
                key = "mem-today-$year",
                title = "$year 年的今天",
                subtitle = "${today.monthValue} 月 ${today.dayOfMonth} 日 · ${countText(list)}",
                coverUrl = chronoCover(list),
                items = list,
            )
        }

    sameMonth
        .groupBy { it.dateTaken.year }
        .entries.sortedByDescending { it.key }
        .forEach { (year, list) ->
            if ("mem-today-$year" in out) return@forEach
            out["mem-month-$year"] = ChronoBucket(
                key = "mem-month-$year",
                title = "$year 年 ${today.monthValue} 月",
                subtitle = countText(list),
                coverUrl = chronoCover(list),
                items = list,
            )
        }

    return out.values.toList()
}

/** 普通相册（文件夹/回忆/收藏等非智能相册） */
fun folderAlbums(state: LibraryState): List<Album> = state.albums.filter { !it.isSmart }

/** 智能相册按类型分组：收藏（FAVORITES）、人物（SMART_PEOPLE）、地点（SMART_PLACE），回忆单列 */
fun smartAlbums(state: LibraryState, type: AlbumType): List<Album> =
    state.albums.filter { it.isSmart && it.type == type }

/** 收藏照片：FAVORITES 智能相册内容 ∪ NAS isCollect ∪ 本机星标 */
fun favoriteItems(state: LibraryState): List<MediaItem> {
    val out = LinkedHashMap<String, MediaItem>()
    state.albums.filter { it.type == AlbumType.FAVORITES }.forEach { album ->
        state.mediaByAlbum[album.id]?.forEach { out.putIfAbsent(it.id, it) }
    }
    state.recent.favoriteItems().forEach { out.putIfAbsent(it.id, it) }
    state.mediaByAlbum.values.flatten().favoriteItems().forEach { out.putIfAbsent(it.id, it) }
    return out.values.toList()
}

/** 某相册的媒体列表（相册 id 找不到时回退到按 albumId 字段过滤，再做空列表兜底） */
fun albumItems(state: LibraryState, album: Album): List<MediaItem> {
    state.mediaByAlbum[album.id]?.let { if (it.isNotEmpty()) return it }
    val fallback = state.recent.filter { it.albumId == album.id }
    if (fallback.isNotEmpty()) return fallback
    return state.mediaByAlbum.values.flatten().filter { it.albumId == album.id }
}

/** 相册封面 url（专辑自带 coverUrl 为空时用第一张缩略图兜底） */
fun albumCoverUrl(state: LibraryState, album: Album): String {
    album.coverUrl.takeIf { it.isNotBlank() }?.let { return it }
    return state.mediaByAlbum[album.id]?.firstOrNull { it.url.isNotBlank() || it.thumbnailUrl.isNotBlank() }
        ?.let { it.thumbnailUrl.ifBlank { it.url } }
        ?: ""
}

/** 统一入口：按媒体类型分发到 viewer / player，避免每个点击点重复判断 */
fun openMedia(
    item: MediaItem,
    items: List<MediaItem>,
    contextTitle: String,
    onOpenPhoto: (List<MediaItem>, Int, String) -> Unit,
    onOpenVideo: (List<MediaItem>, Int, String) -> Unit,
) {
    // 用 id 定位，避免调用方传入的 index 与列表不一致
    val list = if (items.isEmpty()) listOf(item) else items
    val idx = list.indexOfFirst { it.id == item.id }.let { if (it >= 0) it else 0 }
    if (item.type == MediaType.VIDEO) onOpenVideo(list, idx, contextTitle) else onOpenPhoto(list, idx, contextTitle)
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}

/** 视频时长：mm:ss（超过 1 小时用 h:mm:ss） */
fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

private val heroDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA)
private val shortDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val weekdayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)
private val shortWeekdayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.CHINA)

fun formatHeroDate(d: LocalDate): String = heroDateFmt.format(d)
fun formatShortDate(d: LocalDate): String = shortDateFmt.format(d)
fun formatWeekdayShort(d: LocalDate): String = shortWeekdayFmt.format(d)
private fun zhWeekday(d: LocalDate): String = weekdayFmt.format(d)

/** 数量文案：「128 张照片 · 6 个视频」 */
fun countText(items: List<MediaItem>): String {
    val photos = items.count { it.type == MediaType.PHOTO }
    val videos = items.count { it.type == MediaType.VIDEO }
    return when {
        photos == 0 && videos == 0 -> "暂无内容"
        videos == 0 -> "$photos 张照片"
        photos == 0 -> "$videos 个视频"
        else -> "$photos 张照片 · $videos 个视频"
    }
}

/** 相册卡片副标题：优先 album.subtitle，否则按类型给一句 */
fun albumSubtitle(album: Album): String = album.subtitle.ifBlank {
    when (album.type) {
        AlbumType.FOLDER -> "NAS 文件夹"
        AlbumType.SMART_PEOPLE -> "人物识别"
        AlbumType.SMART_PLACE -> "地点聚合"
        AlbumType.MEMORY -> "往年今日"
        AlbumType.FAVORITES -> "星标收藏"
    }
}

/** 智能相册类型 → 中文名（区块标题） */
fun smartGroupTitle(type: AlbumType): String = when (type) {
    AlbumType.FAVORITES -> "收藏"
    AlbumType.SMART_PEOPLE -> "人物"
    AlbumType.SMART_PLACE -> "地点"
    AlbumType.MEMORY -> "回忆"
    AlbumType.FOLDER -> "相册"
}

/** Tab 切换时给 onNavigate 用（目前库里没有对应路由，保留给 Lead 以后扩展） */
val LibraryDefaultRoute: AppRoute = AppRoute.Library

/**
 * ================= Preview 假数据（不依赖 MockData，永远非空） =================
 * 仅用于 @Preview；运行时数据一律来自参数 state。
 */
object LibraryPreviewData {

    private fun photo(id: String, title: String, url: String, day: Int, albumId: String, starred: Boolean = false) = MediaItem(
        id = id,
        title = title,
        type = MediaType.PHOTO,
        url = url,
        thumbnailUrl = url,
        dateTaken = LocalDate.of(2024, 9, day.coerceIn(1, 28)),
        resolution = "4032 × 3024",
        sizeBytes = 3_400_000L + day * 11_000L,
        albumId = albumId,
        starred = starred,
        extension = "JPG",
    )

    private fun video(id: String, title: String, url: String, day: Int, albumId: String, sec: Long) = MediaItem(
        id = id,
        title = title,
        type = MediaType.VIDEO,
        url = url,
        thumbnailUrl = url,
        dateTaken = LocalDate.of(2024, 9, day.coerceIn(1, 28)),
        durationMs = sec * 1000L,
        resolution = "3840 × 2160",
        sizeBytes = 480_000_000L,
        albumId = albumId,
        extension = "MP4",
    )

    val albums: List<Album> = listOf(
        Album("a1", "冰岛环岛", "raw/wall_aurora", 42, AlbumType.FOLDER, "2024 年 2 月 · 雷克雅未克"),
        Album("a2", "海边落日", "raw/wall_sunset", 28, AlbumType.FOLDER, "2024 年 8 月 · 舟山"),
        Album("a3", "深海蓝", "raw/wall_ocean", 19, AlbumType.FOLDER, "2024 年 7 月 · 冲绳"),
        Album("a4", "森林徒步", "raw/wall_forest", 33, AlbumType.FOLDER, "2024 年 5 月 · 莫干山"),
        Album("a5", "黑白街头", "raw/wall_mono", 12, AlbumType.FOLDER, "2024 年 3 月 · 上海"),
        Album("a6", "糖果色夏日", "raw/wall_candy", 24, AlbumType.FOLDER, "2024 年 6 月 · 厦门"),
        Album("a7", "山野记录", "raw/video_mountain", 7, AlbumType.FOLDER, "4K 视频合集"),
        Album("a8", "城市夜景", "raw/video_city", 9, AlbumType.FOLDER, "延时摄影"),
        Album("s1", "收藏", "raw/wall_aurora", 16, AlbumType.FAVORITES, "星标照片", isSmart = true),
        Album("s2", "人物", "raw/video_family", 11, AlbumType.SMART_PEOPLE, "3 位家人", isSmart = true),
        Album("s3", "地点", "raw/wall_sunset", 14, AlbumType.SMART_PLACE, "12 个地点", isSmart = true),
        Album("s4", "回忆", "raw/wall_forest", 8, AlbumType.MEMORY, "往年今日", isSmart = true),
    )

    val recent: List<MediaItem> = listOf(
        video("r1", "无人机航拍 · 山谷晨雾", "raw/video_mountain", 18, "a7", 214),
        photo("r2", "极光下的木屋", "raw/wall_aurora", 17, "a1", starred = true),
        photo("r3", "日落后的栈桥", "raw/wall_sunset", 16, "a2", starred = true),
        photo("r4", "海浪与礁石", "raw/wall_ocean", 15, "a3"),
        video("r5", "城市夜景延时", "raw/video_city", 14, "a8", 96),
        photo("r6", "雾中森林", "raw/wall_forest", 13, "a4"),
        photo("r7", "街角的黑白", "raw/wall_mono", 12, "a5"),
        photo("r8", "糖果色的夏天", "raw/wall_candy", 11, "a6"),
        video("r9", "家庭聚会片段", "raw/video_family", 10, "s2", 351),
        photo("r10", "冰湖倒影", "raw/wall_aurora", 9, "a1"),
        photo("r11", "沙滩上的脚印", "raw/wall_sunset", 8, "a2", starred = true),
        photo("r12", "蓝色深潜", "raw/wall_ocean", 7, "a3"),
    )

    val mediaByAlbum: Map<String, List<MediaItem>> = run {
        val pool = recent + listOf(
            photo("x1", "雪原", "raw/wall_aurora", 6, "a1"),
            photo("x2", "峡湾", "raw/wall_aurora", 5, "a1"),
            photo("x3", "岸边浪花", "raw/wall_sunset", 4, "a2"),
            photo("x4", "黄昏", "raw/wall_sunset", 3, "a2"),
            photo("x5", "浅滩", "raw/wall_ocean", 2, "a3"),
            photo("x6", "林间小路", "raw/wall_forest", 1, "a4"),
            photo("x7", "树影", "raw/wall_forest", 12, "a4"),
            photo("x8", "雨后霓虹", "raw/wall_mono", 13, "a5"),
            photo("x9", "糖果墙", "raw/wall_candy", 14, "a6"),
            video("x10", "山脊行走", "raw/video_mountain", 15, "a7", 128),
            video("x11", "车流", "raw/video_city", 16, "a8", 74),
        )
        albums.associate { album ->
            val list = when (album.type) {
                AlbumType.FAVORITES -> pool.filter { it.starred }
                AlbumType.SMART_PEOPLE -> pool.filter { it.id in setOf("r9", "r2", "r11") }
                AlbumType.SMART_PLACE -> pool.filter { it.albumId in setOf("a2", "a3") }
                else -> pool.filter { it.albumId == album.id }
            }
            album.id to list.ifEmpty { pool.take(4) }
        }
    }

    val fullState: LibraryState = LibraryState(
        recent = recent,
        albums = albums,
        mediaByAlbum = mediaByAlbum,
        selectedTabIndex = 0,
        connectionName = "Home-NAS",
        isOnline = true,
    )

    /** 空数据：验证不崩（MockData 尚未就绪时的真实状态） */
    val emptyState: LibraryState = LibraryState(
        recent = emptyList(),
        albums = emptyList(),
        mediaByAlbum = emptyMap(),
        selectedTabIndex = 0,
        connectionName = "Home-NAS",
        isOnline = false,
    )
}
