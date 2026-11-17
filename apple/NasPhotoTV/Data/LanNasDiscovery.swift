import Foundation
import Darwin
import Security

/// 局域网内飞牛 NAS 发现（对标 Android `LanNasDiscovery`）：
/// 取本机 site-local IPv4 的 /24，逐 IP 探测 TCP 5667（350ms 超时），
/// 对开放端口再做一次 TLS 握手读叶子证书主题，含 "fnOS" 则标记为飞牛 NAS。
struct LanNasDevice: Identifiable, Hashable {
    let host: String
    let port: Int
    let fnOsCertificate: Bool
    var id: String { "\(host):\(port)" }
}

enum LanNasDiscovery {

    static let defaultPort = 5667

    static func discover() async -> [LanNasDevice] {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                continuation.resume(returning: scanSync())
            }
        }
    }

    /// 同步扫描体：整段跑在后台队列，不占用主线程。
    private static func scanSync() -> [LanNasDevice] {
        guard let localIP = localIPv4() else { return [] }
        let parts = localIP.split(separator: ".").map(String.init)
        guard parts.count == 4 else { return [] }
        let prefix = parts.prefix(3).joined(separator: ".")
        let selfLast = Int(parts[3]) ?? 0

        let lock = NSLock()
        var found: [LanNasDevice] = []
        let group = DispatchGroup()
        let queue = DispatchQueue.global(qos: .userInitiated)

        for last in 1...254 where last != selfLast {
            group.enter()
            queue.async {
                defer { group.leave() }
                let host = "\(prefix).\(last)"
                guard tcpReachable(host: host, port: defaultPort, timeoutMs: 350) else { return }
                let isFnOS = certLooksLikeFnOS(host: host, port: defaultPort)
                lock.lock()
                found.append(LanNasDevice(host: host, port: defaultPort, fnOsCertificate: isFnOS))
                lock.unlock()
            }
        }
        group.wait()
        return found.sorted { lhs, rhs in
            if lhs.fnOsCertificate != rhs.fnOsCertificate { return lhs.fnOsCertificate }
            return ipValue(lhs.host) < ipValue(rhs.host)
        }
    }

    // MARK: - 本机 IPv4

    private static func localIPv4() -> String? {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return nil }
        defer { freeifaddrs(ifaddr) }
        var ptr: UnsafeMutablePointer<ifaddrs>? = first
        while let p = ptr {
            defer { ptr = p.pointee.ifa_next }
            let flags = p.pointee.ifa_flags
            guard (flags & UInt32(IFF_UP)) != 0, (flags & UInt32(IFF_LOOPBACK)) == 0 else { continue }
            guard let addr = p.pointee.ifa_addr, addr.pointee.sa_family == sa_family_t(AF_INET) else { continue }
            var sin = sockaddr_in()
            memcpy(&sin, UnsafeRawPointer(addr), MemoryLayout<sockaddr_in>.size)
            guard let cstr = inet_ntoa(sin.sin_addr) else { continue }
            let ip = String(cString: cstr)
            if isSiteLocal(ip) { return ip }
        }
        return nil
    }

    private static func isSiteLocal(_ ip: String) -> Bool {
        if ip.hasPrefix("192.168.") || ip.hasPrefix("10.") { return true }
        if ip.hasPrefix("172.") {
            let second = ip.split(separator: ".").dropFirst().first.flatMap { Int($0) } ?? 0
            return (16...31).contains(second)
        }
        return false
    }

    private static func ipValue(_ ip: String) -> UInt32 {
        ip.split(separator: ".").compactMap { UInt32($0) }.reduce(UInt32(0)) { ($0 << 8) | $1 }
    }

    // MARK: - TCP 连通性（非阻塞 connect + poll 超时）

    private static func tcpReachable(host: String, port: Int, timeoutMs: Int) -> Bool {
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { return false }
        defer { close(fd) }
        _ = fcntl(fd, F_SETFL, O_NONBLOCK)
        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = UInt16(port).bigEndian
        addr.sin_addr.s_addr = inet_addr(host)
        let rc = withUnsafePointer(to: &addr) { ptr in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sa in
                connect(fd, sa, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        if rc == 0 { return true }
        guard errno == EINPROGRESS else { return false }
        var pfd = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
        let ready = poll(&pfd, 1, Int32(timeoutMs))
        guard ready > 0 else { return false }
        var socketError: Int32 = 0
        var len = socklen_t(MemoryLayout<Int32>.size)
        getsockopt(fd, SOL_SOCKET, SO_ERROR, &socketError, &len)
        return socketError == 0
    }

    // MARK: - TLS 证书识别

    private static func certLooksLikeFnOS(host: String, port: Int) -> Bool {
        guard let url = URL(string: "https://\(host):\(port)/") else { return false }
        let delegate = CertProbeDelegate()
        let session = URLSession(configuration: .ephemeral, delegate: delegate, delegateQueue: nil)
        var request = URLRequest(url: url)
        request.timeoutInterval = 1.5
        let semaphore = DispatchSemaphore(value: 0)
        let task = session.dataTask(with: request) { _, _, _ in semaphore.signal() }
        task.resume()
        _ = semaphore.wait(timeout: .now() + 2)
        session.invalidateAndCancel()
        return delegate.leafSubject.lowercased().contains("fnos")
    }
}

private final class CertProbeDelegate: NSObject, URLSessionDelegate {

    private let lock = NSLock()
    private var _leafSubject = ""

    var leafSubject: String {
        lock.lock(); defer { lock.unlock() }
        return _leafSubject
    }

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void,
    ) {
        if let trust = challenge.protectionSpace.serverTrust,
           let leaf = SecTrustGetCertificateAtIndex(trust, 0) {
            let name = (SecCertificateCopySubjectSummary(leaf) as? String) ?? ""
            lock.lock(); _leafSubject = name; lock.unlock()
        }
        completionHandler(.cancelAuthenticationChallenge, nil)
    }
}
