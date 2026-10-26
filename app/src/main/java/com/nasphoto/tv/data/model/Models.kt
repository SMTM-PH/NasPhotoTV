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
 * ================= NAS 上的一个飞牛登录账号 =================
 *
 * 同一台 NAS 可以有多个账号（家庭里每人一个飞牛账号，各自能看到自己的照片）。
 * 所以 host/port 挂在 [NasConnection] 上共享，凭据挂在账号上：
 * 切换账号 == 换一套 username/password/accessCode，host 不变。
 *
 * [id] 是本机生成的稳定标识（不用 username 当主键：账号名可能改密码，也可能被删了重加）。
 */
data class NasAccount(
    val id: String,
    val username: String = "",
    /** 飞牛登录密码（保存在 SharedPreferences 里） */
    val password: String = "",
    /** 本机备注名（账号列表显示）；留空则显示 username */
    val label: String = "",
    /** 飞牛「访问码」网关口令，未启用时留空 */
    val accessCode: String = "",
) {
    /** 列表/顶栏展示的名字 */
    val displayName: String
        get() = label.ifBlank { username.ifBlank { "未命名账号" } }

    /** 头像占位用的首字（避免为头像再引一套图片资源） */
    val initial: String
        get() = displayName.take(1).uppercase()

    /** 凭据是否填全，能拿去登录 */
    val isConfigured: Boolean
        get() = username.isNotBlank() && password.isNotBlank()

    companion object {
        /** 生成一个不会与现有账号冲突的 id */
        fun newId(existing: Collection<NasAccount> = emptyList()): String {
            var n = existing.size + 1
            while (existing.any { it.id == "acc-$n" }) n++
            return "acc-$n"
        }
    }
}

/**
 * NAS 设备信息 + 该设备上的账号列表，用于设置页与状态栏显示。
 *
 * 兼容说明：`username` / `password` / `accessCode` 以前是构造参数，现在改成
 * 「当前账号」的派生只读属性 —— 调用方仍然写 `nas.username`，不用关心多账号，
 * 只有账号管理相关的代码才需要碰 [accounts] / [activeAccountId]。
 */
data class NasConnection(
    val name: String = "Home-NAS",
    val host: String = "",
    /** 飞牛 Web HTTPS 端口（默认 5667） */
    val port: Int = 5667,
    /** 这台 NAS 上保存过的账号（可能是空列表 = 还没配过） */
    val accounts: List<NasAccount> = emptyList(),
    /** 当前使用的账号 id；失效时自动退回第一个账号 */
    val activeAccountId: String = "",
    val connected: Boolean = false,
    val latencyMs: Int = 0,
) {
    /** 当前账号；没有账号时为 null */
    val activeAccount: NasAccount?
        get() = accounts.firstOrNull { it.id == activeAccountId } ?: accounts.firstOrNull()

    val username: String
        get() = activeAccount?.username.orEmpty()

    val password: String
        get() = activeAccount?.password.orEmpty()

    val accessCode: String
        get() = activeAccount?.accessCode.orEmpty()

    /** 是否有多个账号可切换（UI 据此决定是否显示切换入口） */
    val hasMultipleAccounts: Boolean
        get() = accounts.size > 1

    /** 是否已经填好可以登录的必填项 */
    val isConfigured: Boolean
        get() = host.isNotBlank() && activeAccount?.isConfigured == true

    /** 设置页展示的连接串 */
    val displayPath: String
        get() = "https://${username.ifBlank { "user" }}@${host.ifBlank { "NAS" }}:$port/p"

    fun account(id: String): NasAccount? = accounts.firstOrNull { it.id == id }

    /** 换掉某个账号（不存在则追加） */
    fun withAccount(account: NasAccount): NasConnection {
        val exists = accounts.any { it.id == account.id }
        return copy(
            accounts = if (exists) accounts.map { if (it.id == account.id) account else it } else accounts + account,
            activeAccountId = if (activeAccountId.isBlank()) account.id else activeAccountId,
        )
    }

    /** 删掉某个账号；删的是当前账号时自动切到剩下的第一个 */
    fun withoutAccount(id: String): NasConnection {
        val rest = accounts.filterNot { it.id == id }
        if (rest.isEmpty()) return copy(accounts = emptyList(), activeAccountId = "")
        return copy(
            accounts = rest,
            activeAccountId = if (activeAccountId == id) rest.first().id else activeAccountId,
        )
    }

    /** 切到某个账号（id 不存在则原样返回） */
    fun withActiveAccount(id: String): NasConnection =
        if (accounts.none { it.id == id }) this else copy(activeAccountId = id)

    /** 直接改当前账号的字段；没有账号时先建一个空账号再改 */
    fun updateActiveAccount(transform: (NasAccount) -> NasAccount): NasConnection {
        val current = activeAccount ?: NasAccount(id = NasAccount.newId(accounts))
        val next = transform(current)
        val exists = accounts.any { it.id == next.id }
        return copy(
            accounts = if (exists) accounts.map { if (it.id == next.id) next else it } else accounts + next,
            activeAccountId = next.id,
        )
    }

    companion object {
        /** 兼容旧存档：只有一个 username/password 的老配置也能装进新结构 */
        fun of(
            name: String = "Home-NAS",
            host: String = "",
            port: Int = 5667,
            username: String = "",
            password: String = "",
            accessCode: String = "",
        ): NasConnection {
            val list = if (username.isBlank() && password.isBlank()) {
                emptyList()
            } else {
                listOf(
                    NasAccount(
                        id = "acc-1",
                        username = username,
                        password = password,
                        accessCode = accessCode,
                    ),
                )
            }
            return NasConnection(
                name = name,
                host = host,
                port = port,
                accounts = list,
                activeAccountId = list.firstOrNull()?.id.orEmpty(),
            )
        }
    }
}

/**
 * NAS 人脸聚类出来的一个人（飞牛相册「人物」）。
 *
 * [id] 是 ai-person 接口返回的聚类 id，拿它去 ai-person 的 photoLibrary 系列接口
 * 在所有时间线里的照片。AI 相册没开启时列表为空 —— UI 必须显示空状态而不是空白。
 */
data class Person(
    val id: String,
    val name: String,
    /** 头像/封面（已转成绝对地址）；没有时为 "" */
    val coverUrl: String = "",
    /** 该人物下的照片数量（服务端给的 itemCount，取不到填 0） */
    val itemCount: Int = 0,
) {
    val displayName: String get() = name.ifBlank { "未命名人物" }
    val initial: String get() = displayName.take(1)
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
