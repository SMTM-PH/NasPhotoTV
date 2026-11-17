import Foundation

/// 本机的 fnOS 设备标识（登录请求里的 `did`）。
///
/// 为什么必须持久化：fnOS 的两步验证支持「信任此设备」——服务端按 `did` 记住设备，
/// 之后同一 `did` 登录不再要动态码。每次登录都随机生成 `did` 等于每次都换新设备，
/// 即使用户输入了验证码、勾了信任，下次启动仍然会被要求验证。
///
/// 12 字节随机数 = 24 位十六进制，与 Android 实现的长度一致，服务端接受。
enum FnOsDeviceId {

    private static let defaultsKey = "fnos_device.install_id"
    private static let length = 24
    private static let lock = NSLock()
    private static var cached: String?

    static func get() -> String {
        lock.lock(); defer { lock.unlock() }
        if let cached { return cached }
        let stored = UserDefaults.standard.string(forKey: defaultsKey)?.trimmingCharacters(in: .whitespaces) ?? ""
        let id: String
        if stored.count == length {
            id = stored
        } else {
            id = FnOsCrypto.randomHex(length / 2)
            UserDefaults.standard.set(id, forKey: defaultsKey)
        }
        cached = id
        return id
    }
}
