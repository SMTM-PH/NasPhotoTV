import Foundation

/// 媒体类型（与 Android 端 Models.kt 对齐；Apple TV 版首期只消费图片）。
enum MediaType: String {
    case photo
    case video
    case audio
}

/// NAS 上的一个媒体条目。url 为可直接加载的绝对地址（http/https），
/// 鉴权由 FnOsTransport 统一注入 AccessToken。
struct MediaItem: Identifiable, Hashable {
    var id: String
    var title: String
    var type: MediaType
    var url: String
    /// 视频封面 / 图片缩略图
    var thumbnailUrl: String
    /// 实况照片（Live Photo / Motion Photo）：静态图之外还有一段动态片段
    var isLive: Bool
    /// 实况照片的动态片段地址
    var liveUrl: String
    /// 拍摄/创建时间
    var dateTaken: Date?
    /// 视频时长（毫秒），图片为 0
    var durationMs: Int64
    /// 文件大小（字节），用于信息面板
    var sizeBytes: Int64
    /// 像素尺寸，例如 1920x1080
    var resolution: String
    /// 所属相册/文件夹 id
    var albumId: String
    /// 星标收藏（用于屏保优先展示 / 收藏页）
    var starred: Bool
    /// 扩展名，如 JPG / MP4 / HEIC
    var ext: String

    var isVideo: Bool { type == .video }

    /// 像素宽高（来自 NAS 的 width×height），缺失或格式异常时返回 nil
    var pixelSize: (Int, Int)? {
        let parts = resolution.lowercased().split(separator: "x")
        guard parts.count == 2, let w = Int(parts[0]), let h = Int(parts[1]), w > 0, h > 0 else { return nil }
        return (w, h)
    }

    /// 全景 / 360°：靠宽高比识别（手机全景通常 3:1 以上，360 等距柱状图固定 2:1）。
    var isPanorama: Bool {
        guard type == .photo, let (w, h) = pixelSize else { return false }
        return Double(w) / Double(h) >= 1.9
    }
}

/// 相册类型（对齐 Android `AlbumType`）。真实数据源只产出 folder 与 smartCategory，
/// 其余用于收藏/人物等虚拟分组的 UI 标识。
enum AlbumType: String {
    case folder
    case smartPeople
    case smartPlace
    case memory
    case favorites
    case smartCategory
}

/// 相册（对应 NAS 上的一个文件夹，或按日期/地点聚合的虚拟相册）。
struct Album: Identifiable, Hashable {
    var id: String
    var name: String
    /// 封面 url（通常取第一张媒体的缩略图）
    var coverUrl: String
    var itemCount: Int
    /// 「人像/地点/回忆」等智能相册在标题下显示的小字说明
    var subtitle: String
    var type: AlbumType = .folder
    var isSmart: Bool = false
}

/// AI 人物（对应 `ai-person/list` 一条）。
struct Person: Identifiable, Hashable {
    var id: String
    var name: String
    var coverUrl: String
    var itemCount: Int

    var displayName: String { name.isEmpty ? "未命名人物" : name }
    var initial: String { String(displayName.prefix(1)) }
}

/// 磁盘文件夹视图条目（对应 `photo/folder/list` 一条）。
struct NasFolder: Identifiable, Hashable {
    var id: String
    var path: String
    var name: String
    var photoCount: Int
    var videoCount: Int
    var coverUrl: String = ""
}

/// 飞牛登录会话（WebSocket 登录成功后的凭证）。
struct FnOsSession {
    var token: String
    var secret: String
}

/// 飞牛相册统计（连接测试用）。
struct FnOsStats: Hashable {
    var photoCount: Int
    var videoCount: Int
}

/// 两步验证挑战：账号已开启 2FA，密码正确后还需要一个 6 位动态码。
struct FnOsTwoFactorChallenge: Hashable {
    /// true = 账号已绑定验证器 App（TOTP）；false = 只绑了邮箱，客户端暂不支持
    var totpBound: Bool = true
}

/// 一次连接测试的结果。
struct FnOsProbeResult {
    var ok: Bool
    var latencyMs: Int = 0
    var stats: FnOsStats?
    var error: String?
    /// 非 nil = 账号已开启两步验证，需要用户输入动态码
    var twoFactor: FnOsTwoFactorChallenge?
}
