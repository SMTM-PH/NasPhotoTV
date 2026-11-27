import Foundation

enum SaverDisplay: String, Codable, CaseIterable, Identifiable {
    case clock, photos, wall
    var id: String { rawValue }
    var title: String {
        switch self {
        case .clock: return "时钟待机"
        case .photos: return "纯照片"
        case .wall: return "照片墙"
        }
    }

    var next: Self {
        switch self {
        case .clock: return .photos
        case .photos: return .wall
        case .wall: return .clock
        }
    }
}

enum SlideshowMotion: String, CaseIterable, Codable {
    case dissolve, slide, cut
    var title: String {
        switch self {
        case .dissolve: return "叠化"
        case .slide: return "滑动"
        case .cut: return "直接切换"
        }
    }

    var next: Self {
        switch self {
        case .dissolve: return .slide
        case .slide: return .cut
        case .cut: return .dissolve
        }
    }
}

/// 屏保配置（对齐 Android `ScreensaverSettings` 的常用子集 + 时钟样式）。
struct ScreensaverSettings: Equatable, Codable {
    var enabled: Bool = false
    var display: SaverDisplay = .clock
    /// 静止多久后进入屏保（秒）：30 / 60 / 120 / 300 / 600 / 1800
    var idleTimeoutSec: Int = 60
    /// 单张照片停留时长（秒）：5 / 10 / 15 / 30 / 60
    var photoIntervalSec: Int = 10
    /// 是否显示日期
    var showDate: Bool = true
    var showLunarDate: Bool = false
    /// 夜间调暖色遮罩
    var nightDim: Bool = true
    /// Ken Burns 缓慢推拉（防烧屏）
    var kenBurns: Bool = true
    /// 幻灯片/屏保背景音乐开关
    var musicEnabled: Bool = true
    /// 背景音乐音量 0...1
    var musicVolume: Double = 0.7
    /// 时钟样式：cinema / flip / thin / minimal / dateFocus
    var clockStyle: String = "cinema"

    /// 旧版本持久化数据没有新增字段：decodeIfPresent 保证平滑升级。
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        display = try c.decodeIfPresent(SaverDisplay.self, forKey: .display) ?? .clock
        idleTimeoutSec = try c.decodeIfPresent(Int.self, forKey: .idleTimeoutSec) ?? 60
        photoIntervalSec = try c.decodeIfPresent(Int.self, forKey: .photoIntervalSec) ?? 10
        showDate = try c.decodeIfPresent(Bool.self, forKey: .showDate) ?? true
        showLunarDate = try c.decodeIfPresent(Bool.self, forKey: .showLunarDate) ?? false
        nightDim = try c.decodeIfPresent(Bool.self, forKey: .nightDim) ?? true
        kenBurns = try c.decodeIfPresent(Bool.self, forKey: .kenBurns) ?? true
        musicEnabled = try c.decodeIfPresent(Bool.self, forKey: .musicEnabled) ?? true
        musicVolume = try c.decodeIfPresent(Double.self, forKey: .musicVolume) ?? 0.7
        clockStyle = try c.decodeIfPresent(String.self, forKey: .clockStyle) ?? "cinema"
    }

    init() {}
}

/// 播放设置（对齐 Android `PlaybackSettings`）。
enum VideoFit: String, CaseIterable, Codable {
    case automatic, fit, fill

    var title: String {
        switch self {
        case .automatic: return "自动"
        case .fit: return "完整"
        case .fill: return "填满"
        }
    }

    var next: VideoFit {
        switch self {
        case .automatic: return .fit
        case .fit: return .fill
        case .fill: return .automatic
        }
    }
}

enum VideoRotation: Int, Codable {
    case upright = 0, clockwise = 90, inverted = 180, counterclockwise = 270

    var next: VideoRotation { VideoRotation(rawValue: (rawValue + 90) % 360) ?? .upright }
    var swapsAxes: Bool { self == .clockwise || self == .counterclockwise }
    var title: String { "\(rawValue)°" }
}

struct PlaybackSettings: Equatable, Codable {
    /// 播完自动播下一个
    var autoPlayNext: Bool = true
    /// 单条循环
    var loopMode: Bool = false
    var videoFit: VideoFit = .automatic
    var rotation: VideoRotation = .upright
    var photoMotion: SlideshowMotion = .dissolve
    var photoIntervalSec: Int = 5

    init() {}

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        autoPlayNext = try values.decodeIfPresent(Bool.self, forKey: .autoPlayNext) ?? true
        loopMode = try values.decodeIfPresent(Bool.self, forKey: .loopMode) ?? false
        videoFit = try values.decodeIfPresent(VideoFit.self, forKey: .videoFit) ?? .automatic
        rotation = try values.decodeIfPresent(VideoRotation.self, forKey: .rotation) ?? .upright
        photoMotion = try values.decodeIfPresent(SlideshowMotion.self, forKey: .photoMotion) ?? .dissolve
        photoIntervalSec = max(3, try values.decodeIfPresent(Int.self, forKey: .photoIntervalSec) ?? 5)
    }
}

enum TvAppearance: String, CaseIterable, Identifiable {
    case dark, light, system
    var id: String { rawValue }

    var title: String {
        switch self {
        case .dark: return "深色 · 影院黑"
        case .light: return "浅色 · 画廊白"
        case .system: return "跟随系统"
        }
    }
}

/// 连接配置持久化（单账号，迁移自 Android 端 SettingsStore 的最小子集）。
final class AppSettings: ObservableObject {

    static let shared = AppSettings()

    private let defaults = UserDefaults.standard

    @Published var host: String { didSet { defaults.set(host, forKey: "nas.host") } }
    @Published var port: Int { didSet { defaults.set(port, forKey: "nas.port") } }
    @Published var username: String { didSet { defaults.set(username, forKey: "nas.username") } }
    @Published var password: String { didSet { defaults.set(password, forKey: "nas.password") } }
    @Published var accessCode: String { didSet { defaults.set(accessCode, forKey: "nas.accessCode") } }
    /// 开机自动重连上次 NAS（默认关）
    @Published var autoReconnect: Bool {
        didSet { defaults.set(autoReconnect, forKey: "nas.autoReconnect") }
    }
    @Published var appearance: TvAppearance {
        didSet { defaults.set(appearance.rawValue, forKey: "tv.appearance") }
    }
    @Published var screensaver: ScreensaverSettings {
        didSet {
            if let data = try? JSONEncoder().encode(screensaver) {
                defaults.set(data, forKey: "screensaver.settings")
            }
        }
    }
    @Published var playback: PlaybackSettings {
        didSet {
            if let data = try? JSONEncoder().encode(playback) {
                defaults.set(data, forKey: "playback.settings")
            }
        }
    }

    private init() {
        host = defaults.string(forKey: "nas.host") ?? ""
        port = defaults.object(forKey: "nas.port") as? Int ?? 5667
        username = defaults.string(forKey: "nas.username") ?? ""
        password = defaults.string(forKey: "nas.password") ?? ""
        accessCode = defaults.string(forKey: "nas.accessCode") ?? ""
        autoReconnect = defaults.bool(forKey: "nas.autoReconnect")
        appearance = defaults.string(forKey: "tv.appearance").flatMap(TvAppearance.init(rawValue:)) ?? .dark
        if let data = defaults.data(forKey: "screensaver.settings"),
           let saved = try? JSONDecoder().decode(ScreensaverSettings.self, from: data) {
            screensaver = saved
        } else {
            screensaver = ScreensaverSettings()
        }
        if let data = defaults.data(forKey: "playback.settings"),
           let saved = try? JSONDecoder().decode(PlaybackSettings.self, from: data) {
            playback = saved
        } else {
            playback = PlaybackSettings()
        }
    }

    var isConfigured: Bool {
        !host.isEmpty && !username.isEmpty && !password.isEmpty
    }

    var access: FnOsAccess {
        FnOsAccess(host: host, port: port, username: username, password: password, accessCode: accessCode)
    }

    private var displayAlbumKey: String {
        let account = access
        return "displayAlbums.\(account.baseUrl)|\(account.username)"
    }

    func displayAlbumIDs() -> Set<String> {
        Set(defaults.stringArray(forKey: displayAlbumKey) ?? [])
    }

    func saveDisplayAlbumIDs(_ ids: Set<String>) {
        defaults.set(ids.sorted(), forKey: displayAlbumKey)
    }

    /// 待机进入时间下一档：30 秒 → 1/2/5/10/30 分钟循环（对齐 Android cycleIdleTimeout）。
    func cycleIdleTimeout() {
        let steps = [30, 60, 120, 300, 600, 1800]
        screensaver.idleTimeoutSec = steps.first { $0 > screensaver.idleTimeoutSec } ?? steps[0]
    }

    /// 单张停留下一档：5 / 10 / 15 / 30 / 60 秒循环。
    func cyclePhotoInterval() {
        let steps = [5, 10, 15, 30, 60]
        screensaver.photoIntervalSec = steps.first { $0 > screensaver.photoIntervalSec } ?? steps[0]
    }

    func formatIdleTimeout() -> String {
        let sec = screensaver.idleTimeoutSec
        if sec < 60 { return "\(sec) 秒" }
        if sec % 60 == 0 { return "\(sec / 60) 分钟" }
        return "\(sec / 60) 分 \(sec % 60) 秒"
    }
}
