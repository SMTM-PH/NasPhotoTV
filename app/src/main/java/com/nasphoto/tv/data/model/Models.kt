package com.nasphoto.tv.data.model

import java.time.LocalDate

/**
 * 媒体类型。目前原型支持图片与视频；音频预留。
 */
enum class MediaType { PHOTO, VIDEO, AUDIO }

/**
 * NAS 上的一个媒体条目。url 为可直接加载的绝对地址（http/https），
 * 鉴权由 FnOsTransport 统一注入 AccessToken。
 */
data class MediaItem(
    val id: String,
    val title: String,
    val type: MediaType,
    val url: String,
    /** 视频封面 / 图片缩略图 */
    val thumbnailUrl: String = url,
    /** 实况照片（Live Photo / Motion Photo）：静态图之外还有一段动态片段 */
    val isLive: Boolean = false,
    /** 实况照片的动态片段地址（fnOS：isLive=1 时取 additional.thumbnail.videoUrl） */
    val liveUrl: String = "",
    /** 拍摄/创建时间 */
    val dateTaken: LocalDate = LocalDate.now(),
    /** 视频时长（毫秒），图片为 0 */
    val durationMs: Long = 0L,
    /** 文件大小（字节），用于信息面板 */
    val sizeBytes: Long = 0L,
    /** 像素尺寸，例如 1920x1080 */
    val resolution: String = "",
    /** 所属相册/文件夹 id */
    val albumId: String = "",
    /** 星标收藏（用于屏保优先展示 / 收藏页） */
    val starred: Boolean = false,
    /** 扩展名，如 JPG / MP4 / HEIC */
    val extension: String = "",
)

/** 常见 RAW 原始格式（相机直出） */
val RAW_EXTENSIONS: Set<String> = setOf(
    "CR2", "CR3", "NEF", "ARW", "DNG", "RAF", "ORF", "RW2", "PEF", "SRW", "3FR", "ERF", "KDC", "MOS", "MRW", "X3F",
)

/**
 * RAW 原始文件：Android 端侧没有这些格式的解码器，
 * 必须由 NAS 生成的高清 JPG 预览（`m` 尺寸）来显示。
 */
fun MediaItem.isRawPhoto(): Boolean =
    type == MediaType.PHOTO && extension.uppercase() in RAW_EXTENSIONS

/** 像素尺寸（来自 NAS 的 width×height），缺失或格式异常时返回 null */
fun MediaItem.pixelSize(): Pair<Int, Int>? {
    val parts = resolution.lowercase().split('x')
    if (parts.size != 2) return null
    val w = parts[0].trim().toIntOrNull() ?: return null
    val h = parts[1].trim().toIntOrNull() ?: return null
    return if (w > 0 && h > 0) w to h else null
}

/**
 * 全景 / 360°：靠宽高比识别。
 * 手机全景通常 3:1 以上，360 等距柱状图固定 2:1；普通 16:9 是 1.78，不会误判。
 */
fun MediaItem.isPanoramaPhoto(): Boolean {
    if (type != MediaType.PHOTO) return false
    val (w, h) = pixelSize() ?: return false
    return w.toFloat() / h >= 1.9f
}

/**
 * 相册（对应 NAS 上的一个文件夹，或按日期/地点聚合的虚拟相册）。
 */data class Album(
    val id: String,
    val name: String,
    /** 封面 url（通常取第一张媒体的缩略图） */
    val coverUrl: String,
    val itemCount: Int,
    val type: AlbumType = AlbumType.FOLDER,
    /** 「人像/地点/回忆」等智能相册在标题下显示的小字说明 */
    val subtitle: String = "",
    val isSmart: Boolean = false,
)

enum class AlbumType { FOLDER, SMART_PEOPLE, SMART_PLACE, MEMORY, FAVORITES }

/**
 * NAS 设备信息，用于设置页与状态栏显示。
 */
data class NasConnection(
    val name: String = "Home-NAS",
    val host: String = "",
    /** 飞牛 Web HTTPS 端口（默认 5667） */
    val port: Int = 5667,
    val username: String = "",
    /** 飞牛登录密码（保存在 SharedPreferences 里） */
    val password: String = "",
    /** 飞牛「访问码」网关口令，未启用时留空 */
    val accessCode: String = "",
    val connected: Boolean = false,
    val latencyMs: Int = 0,
) {
    /** 是否已经填好可以登录的必填项 */
    val isConfigured: Boolean
        get() = host.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /** 设置页展示的连接串 */
    val displayPath: String
        get() = "https://${username.ifBlank { "user" }}@${host.ifBlank { "NAS" }}:$port/p"
}

/**
 * 屏保（待机显示）配置，对应 iOS StandBy。
 */
data class ScreensaverSettings(
    val enabled: Boolean = true,
    /** 静止多久后进入屏保（秒） */
    val idleTimeoutSec: Int = 60,
    /** 单张照片停留时长（秒） */
    val photoIntervalSec: Int = 10,
    /** 画面模式：时钟待机 / 纯照片（全屏照片，无任何文字） */
    val mode: Mode = Mode.CLOCK,
    /** 照片轮播的来源：人物 / 最近 / 随机 / 相册 */
    val photoSource: PhotoSource = PhotoSource.RECENT,
    /** photoSource == ALBUM 时，只轮播这个相册 */
    val albumId: String = "",
    val albumName: String = "",
    /** 时钟样式 */
    val clockStyle: ClockStyle = ClockStyle.CINEMA,
    /** 背景模式：照片轮播 / 极光动态 / 纯黑极简（仅时钟待机模式） */
    val background: BackgroundStyle = BackgroundStyle.PHOTO,
    /** 是否显示农历/日期 */
    val showDate: Boolean = true,
    /** 夜间自动调暗 + 降低亮度（防止烧屏） */
    val nightDim: Boolean = true,
    /** Ken Burns 缓慢推拉动画 */
    val kenBurns: Boolean = true,
) {
    enum class Mode(val label: String) {
        CLOCK("时钟待机"),
        PHOTO_ONLY("纯照片"),
    }

    enum class PhotoSource(val label: String) {
        PEOPLE("人物"),
        RECENT("最近"),
        RANDOM("随机"),
        ALBUM("相册"),
    }

    enum class ClockStyle(val label: String) {
        CINEMA("巨幕时钟"),
        FLIP("翻页数字"),
        THIN("纤细极简"),
        MINIMAL("极简小钟"),
        DATE_FOCUS("日期日历"),
    }

    enum class BackgroundStyle(val label: String) {
        PHOTO("照片轮播"),
        AURORA("极光动态"),
        BLACK("纯黑极简"),
    }
}

/**
 * 播放相关设置。
 */
data class PlaybackSettings(
    /** 视频播放完成后的动作 */
    val autoPlayNext: Boolean = true,
    /** 默认播放模式 */
    val loopMode: Boolean = false,
)
