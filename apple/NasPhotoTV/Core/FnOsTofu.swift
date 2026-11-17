import Foundation
import Security

/// 按主机名保存的自签证书指纹（SHA-256），存 UserDefaults。
final class FnOsPinStore {

    static let shared = FnOsPinStore()

    private let defaults = UserDefaults.standard
    private let keyPrefix = "fnos_cert_pin."

    private init() {}

    func pin(for host: String) -> String? {
        defaults.string(forKey: keyPrefix + host)
    }

    func savePin(_ pin: String, for host: String) {
        defaults.set(pin, forKey: keyPrefix + host)
    }

    func clearPin(for host: String) {
        defaults.removeObject(forKey: keyPrefix + host)
    }
}

/// 飞牛是自签证书（O=fnOS / CN=fnOS，SAN 只有 DNS:fnOS），证书链校验必然失败。
///
/// 这里做「首次使用信任」（TOFU）：不校验证书链，只记住首次连接看到的叶证书指纹；
/// 之后指纹变化会被拒绝一次并清除旧指纹，下一次连接重新信任 —— 飞牛证书约三个月
/// 续期一次，直接把用户锁死会很难恢复。（移植自 Android 端 `FnOsTofu.kt`）
final class FnOsTofuDelegate: NSObject, URLSessionDelegate {

    static let shared = FnOsTofuDelegate()

    private let pins = FnOsPinStore.shared
    private let lock = NSLock()
    private var _pinMismatch = false

    /// 最近一次是否因指纹变化而拒绝（供连接测试给出可读原因）
    var pinMismatch: Bool {
        get { lock.lock(); defer { lock.unlock() }; return _pinMismatch }
        set { lock.lock(); _pinMismatch = newValue; lock.unlock() }
    }

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void,
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust,
              let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
              let leaf = chain.first else {
            completionHandler(.performDefaultHandling, nil)
            return
        }
        let pin = FnOsCrypto.sha256Hex(SecCertificateCopyData(leaf) as Data)
        let host = challenge.protectionSpace.host
        if let stored = pins.pin(for: host) {
            if stored.caseInsensitiveCompare(pin) == .orderedSame {
                completionHandler(.useCredential, URLCredential(trust: trust))
            } else {
                pinMismatch = true
                pins.clearPin(for: host)
                completionHandler(.cancelAuthenticationChallenge, nil)
            }
        } else {
            pins.savePin(pin, for: host)
            completionHandler(.useCredential, URLCredential(trust: trust))
        }
    }
}

/// 全局传输层：API、图片（与后续视频）共用同一套
/// 自签证书 TOFU 信任、cookie 与 AccessToken 注入。
///
/// token 是动态读的 —— 重新登录后不需要重建任何会话，
/// 正在排队的图片请求会自动带上新 token。
final class FnOsTransport {

    static let shared = FnOsTransport()

    /// 网络会话（TOFU 信任 + 系统级 cookie 存储）
    let session: URLSession

    private let lock = NSLock()
    private var _token: String?

    private init() {
        let config = URLSessionConfiguration.default
        config.httpCookieStorage = HTTPCookieStorage.shared
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 30
        config.httpAdditionalHeaders = ["User-Agent": "NASPhotoTV/1.0 (Apple TV)"]
        session = URLSession(configuration: config, delegate: FnOsTofuDelegate.shared, delegateQueue: nil)
    }

    var token: String? {
        get { lock.lock(); defer { lock.unlock() }; return _token }
        set { lock.lock(); _token = newValue; lock.unlock() }
    }
}
