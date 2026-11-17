import Foundation

/// 屏保配置（Apple TV 版首期保留最常用的几项）。
struct ScreensaverSettings: Equatable, Codable {
    var enabled: Bool = true
    /// 静止多久后进入屏保（秒）
    var idleTimeoutSec: Int = 60
    /// 单张照片停留时长（秒）
    var photoIntervalSec: Int = 10
    /// 是否显示日期
    var showDate: Bool = true
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
    @Published var screensaver: ScreensaverSettings {
        didSet {
            if let data = try? JSONEncoder().encode(screensaver) {
                defaults.set(data, forKey: "screensaver.settings")
            }
        }
    }

    private init() {
        host = defaults.string(forKey: "nas.host") ?? ""
        port = defaults.object(forKey: "nas.port") as? Int ?? 5667
        username = defaults.string(forKey: "nas.username") ?? ""
        password = defaults.string(forKey: "nas.password") ?? ""
        accessCode = defaults.string(forKey: "nas.accessCode") ?? ""
        if let data = defaults.data(forKey: "screensaver.settings"),
           let saved = try? JSONDecoder().decode(ScreensaverSettings.self, from: data) {
            screensaver = saved
        } else {
            screensaver = ScreensaverSettings()
        }
    }

    var isConfigured: Bool {
        !host.isEmpty && !username.isEmpty && !password.isEmpty
    }

    var access: FnOsAccess {
        FnOsAccess(host: host, port: port, username: username, password: password, accessCode: accessCode)
    }
}
