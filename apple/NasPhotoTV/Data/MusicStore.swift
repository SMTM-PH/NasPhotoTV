import Foundation
import AVFAudio
import Security
import Darwin

// MARK: - 曲目存储（对标 Android SlideshowMusic）

final class MusicStore: ObservableObject {

    static let shared = MusicStore()

    @Published private(set) var trackNames: [String] = []

    let dir: URL = {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let target = base.appendingPathComponent("slideshow_music", isDirectory: true)
        try? FileManager.default.createDirectory(at: target, withIntermediateDirectories: true)
        return target
    }()

    private init() { refresh() }

    var latestName: String? { trackNames.first }

    var latestURL: URL? {
        guard let name = latestName else { return nil }
        return dir.appendingPathComponent(name)
    }

    func refresh() {
        let fm = FileManager.default
        let files = (try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
        trackNames = files
            .filter { url in
                guard let values = try? url.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey]) else { return false }
                return (values.isRegularFile ?? false) && (values.fileSize ?? 0) > 0
            }
            .sorted {
                let a = (try? $0.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate ?? .distantPast
                let b = (try? $1.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate ?? .distantPast
                return a > b
            }
            .map { $0.lastPathComponent }
    }

    static func sanitizeName(_ raw: String) -> String {
        var name = raw
            .split(separator: "/").last.map(String.init) ?? raw
        name = name.split(separator: "\\").last.map(String.init) ?? name
        let invalid = CharacterSet(charactersIn: "\\/:*?\"<>|").union(.controlCharacters)
        name = String(name.unicodeScalars.map { invalid.contains($0) ? "_" : Character($0) })
        return String(name.trimmingCharacters(in: .whitespaces).prefix(120))
    }
}

// MARK: - 上传服务（对标 Android MusicUploadServer：8899 起 10 个端口，token 路径鉴权）

final class MusicUploadServer: ObservableObject {

    private let token: String
    private var listenFd: Int32 = -1
    private var worker: Thread?
    @Published private(set) var urls: [String] = []

    init() {
        var bytes = [UInt8](repeating: 0, count: 6)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        token = bytes.map { String(format: "%02x", $0) }.joined()
    }

    func start() -> Bool {
        for port in 8899..<8909 {
            let fd = socket(AF_INET, SOCK_STREAM, 0)
            guard fd >= 0 else { continue }
            var reuse: Int32 = 1
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &reuse, socklen_t(MemoryLayout<Int32>.size))
            var addr = sockaddr_in()
            addr.sin_family = sa_family_t(AF_INET)
            addr.sin_port = UInt16(port).bigEndian
            addr.sin_addr.s_addr = UInt32(0).bigEndian
            let bound = withUnsafePointer(to: &addr) { ptr in
                ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sa in
                    bind(fd, sa, socklen_t(MemoryLayout<sockaddr_in>.size))
                }
            }
            if bound == 0, listen(fd, 50) == 0 {
                listenFd = fd
                urls = Self.lanAddresses().map { "http://\($0):\(port)/u/\(token)/" }
                let capturedFd = fd
                worker = Thread { Self.acceptLoop(fd: capturedFd, server: self) }
                worker?.name = "music-upload"
                worker?.start()
                return true
            }
            close(fd)
        }
        return false
    }

    func stop() {
        if listenFd >= 0 { close(listenFd); listenFd = -1 }
        worker = nil
    }

    private static func acceptLoop(fd: Int32, server: MusicUploadServer) {
        while server.listenFd >= 0 {
            let client = accept(fd, nil, nil)
            if client < 0 { break }
            DispatchQueue.global(qos: .userInitiated).async {
                server.handle(client: client)
                close(client)
            }
        }
    }

    private func handle(client: Int32) {
        var buffer = [UInt8]()
        var chunk = [UInt8](repeating: 0, count: 4096)
        var headerEnd: Int?
        while buffer.count < 8192 {
            let n = chunk.withUnsafeMutableBytes { recv(client, $0.baseAddress, 4096, 0) }
            if n <= 0 { break }
            buffer.append(contentsOf: chunk[0..<n])
            if let end = Self.headerEnd(in: buffer) {
                headerEnd = end
                break
            }
        }
        guard let end = headerEnd else { return }
        let headerData = Data(buffer[0..<end])
        guard let header = String(data: headerData, encoding: .utf8) else { return }
        let lines = header.split(separator: "\r\n").map(String.init)
        guard let first = lines.first else { return }
        let parts = first.split(separator: " ").map(String.init)
        guard parts.count >= 2 else { return }
        let method = parts[0]
        let path = parts[1]
        let contentLength = lines.dropFirst().compactMap { line -> Int? in
            guard line.lowercased().hasPrefix("content-length:") else { return nil }
            return Int(line.dropFirst("content-length:".count).trimmingCharacters(in: .whitespaces))
        }.first ?? 0

        let prefix = "/u/\(token)"
        guard path == prefix || path.hasPrefix(prefix + "/") else {
            respond(client: client, code: 404, type: "text/plain; charset=utf-8", body: "not found"); return
        }
        let route = String(path.dropFirst(prefix.count))

        switch (method, route) {
        case ("GET", ""), ("GET", "/"):
            respond(client: client, code: 200, type: "text/html; charset=utf-8", body: pageHtml)
        case ("GET", "/list"):
            let names = MusicStore.shared.trackNames
            let json = "[" + names.map { "\"\($0.replacingOccurrences(of: "\"", with: "\\\""))\"" }.joined(separator: ",") + "]"
            respond(client: client, code: 200, type: "application/json; charset=utf-8", body: json)
        case ("POST", _) where route.hasPrefix("/upload"):
            guard contentLength > 0 else {
                respond(client: client, code: 400, type: "text/plain; charset=utf-8", body: "empty"); return
            }
            guard contentLength <= 300 * 1024 * 1024 else {
                respond(client: client, code: 413, type: "text/plain; charset=utf-8", body: "too large"); return
            }
            var body = [UInt8](buffer[end...])
            while body.count < contentLength {
                let n = chunk.withUnsafeMutableBytes { recv(client, $0.baseAddress, 4096, 0) }
                if n <= 0 { break }
                body.append(contentsOf: chunk[0..<n])
            }
            guard body.count >= contentLength else {
                respond(client: client, code: 400, type: "text/plain; charset=utf-8", body: "incomplete"); return
            }
            let query = route.contains("?") ? String(route.split(separator: "?").last ?? "") : ""
            let rawName = query.split(separator: "&").compactMap { pair -> String? in
                guard pair.hasPrefix("name=") else { return nil }
                return String(pair.dropFirst(5)).removingPercentEncoding?.replacingOccurrences(of: "+", with: " ")
            }.first ?? "music.mp3"
            let name = MusicStore.sanitizeName(rawName.isEmpty ? "music.mp3" : rawName)
            let target = MusicStore.shared.dir.appendingPathComponent(name)
            let tmp = MusicStore.shared.dir.appendingPathComponent(name + ".uploading")
            try? FileManager.default.removeItem(at: tmp)
            FileManager.default.createFile(atPath: tmp.path, contents: nil)
            guard let handle = FileHandle(forWritingAtPath: tmp.path) else {
                respond(client: client, code: 500, type: "text/plain; charset=utf-8", body: "write failed"); return
            }
            handle.write(Data(body[0..<contentLength]))
            try? handle.close()
            try? FileManager.default.removeItem(at: target)
            try? FileManager.default.moveItem(at: tmp, to: target)
            DispatchQueue.main.async { MusicStore.shared.refresh() }
            respond(client: client, code: 200, type: "text/plain; charset=utf-8", body: "OK")
        default:
            respond(client: client, code: 404, type: "text/plain; charset=utf-8", body: "not found")
        }
    }

    private static func headerEnd(in bytes: [UInt8]) -> Int? {
        guard bytes.count >= 4 else { return nil }
        for i in 0...(bytes.count - 4) {
            if bytes[i] == 13, bytes[i + 1] == 10, bytes[i + 2] == 13, bytes[i + 3] == 10 {
                return i + 4
            }
        }
        return nil
    }

    private func respond(client: Int32, code: Int, type: String, body: String) {
        let bytes = [UInt8](body.utf8)
        let head = "HTTP/1.1 \(code) \(code == 200 ? "OK" : "ERR")\r\n"
            + "Content-Type: \(type)\r\nContent-Length: \(bytes.count)\r\nConnection: close\r\n\r\n"
        var data = [UInt8](head.utf8)
        data.append(contentsOf: bytes)
        _ = data.withUnsafeBytes { send(client, $0.baseAddress, data.count, 0) }
    }

    private static func lanAddresses() -> [String] {
        var result = Set<String>()
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return [] }
        defer { freeifaddrs(ifaddr) }
        var ptr: UnsafeMutablePointer<ifaddrs>? = first
        while let p = ptr {
            defer { ptr = p.pointee.ifa_next }
            let flags = p.pointee.ifa_flags
            guard (flags & UInt32(IFF_UP)) != 0, (flags & UInt32(IFF_LOOPBACK)) == 0 else { continue }
            guard let addr = p.pointee.ifa_addr, addr.pointee.sa_family == sa_family_t(AF_INET) else { continue }
            var sin = sockaddr_in()
            memcpy(&sin, UnsafeRawPointer(addr), MemoryLayout<sockaddr_in>.size)
            let cstr = inet_ntoa(sin.sin_addr)
            guard let cstr else { continue }
            let ip = String(cString: cstr)
            if isSiteLocal(ip) { result.insert(ip) }
        }
        return result.sorted()
    }

    private static func isSiteLocal(_ ip: String) -> Bool {
        if ip.hasPrefix("192.168.") || ip.hasPrefix("10.") { return true }
        if ip.hasPrefix("172.") {
            let second = ip.split(separator: ".").dropFirst().first.flatMap { Int($0) } ?? 0
            return (16...31).contains(second)
        }
        return false
    }

    /// 与 Android `pageHtml()` 文案一致（用浏览器打开后上传一个音频文件）。
    private var pageHtml: String {
        """
        <!doctype html><html lang="zh"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>NAS Photo TV · 上传背景音乐</title>
        <style>body{font-family:-apple-system,sans-serif;background:#06131e;color:#f5f7fa;padding:32px;max-width:640px;margin:auto}
        h1{font-size:26px}p{color:#a9b0be;line-height:1.7}button{background:#31b6ed;color:#06131e;border:0;border-radius:12px;
        padding:14px 28px;font-size:18px;font-weight:600;margin-top:16px}input{margin-top:16px}#s{margin-top:16px;color:#a9b0be}</style></head>
        <body><h1>上传背景音乐</h1>
        <p>选择手机里的一个音频文件（mp3 / m4a / aac / flac / wav），上传后会自动成为电视幻灯片背景音乐（循环播放）。</p>
        <input id="f" type="file" accept="audio/*"><br><button onclick="up()">上传</button><div id="s"></div>
        <div id="l"></div>
        <script>
        function show(t){document.getElementById('s').textContent=t}
        async function list(){try{const r=await fetch('./list');const a=await r.json();
        document.getElementById('l').innerHTML=a.length?'已有：<ul>'+a.map(x=>'<li>'+x+'</li>').join('')+'</ul>':'还没有上传过音乐'}catch(e){}}
        async function up(){const f=document.getElementById('f').files[0];if(!f){show('请先选择文件');return}
        show('上传中… '+(f.size/1048576).toFixed(1)+'MB');
        try{const r=await fetch('./upload?name='+encodeURIComponent(f.name),{method:'POST',body:f,headers:{'Content-Type':'application/octet-stream'}});
        if(!r.ok)throw new Error(await r.text());show('上传成功：'+f.name);list()}catch(e){show('失败：'+e.message)}}
        list()</script></body></html>
        """
    }
}

// MARK: - 循环播放（对标 Android SlideshowMusicEffect）

final class MusicPlayer: ObservableObject {

    private var player: AVAudioPlayer?

    func play(url: URL, volume: Double) {
        stop()
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        do {
            let p = try AVAudioPlayer(contentsOf: url)
            p.numberOfLoops = -1
            p.volume = Float(min(max(volume, 0), 1))
            p.prepareToPlay()
            p.play()
            player = p
        } catch {
            player = nil
        }
    }

    func stop() {
        player?.stop()
        player = nil
    }
}
