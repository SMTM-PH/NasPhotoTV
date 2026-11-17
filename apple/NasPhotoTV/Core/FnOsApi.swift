import Foundation

/// 一次连接所需的配置（端口是飞牛的 Web HTTPS 端口，默认 5667）。
struct FnOsAccess: Equatable {
    var host: String
    var port: Int
    var username: String
    var password: String
    var accessCode: String = ""

    var baseUrl: String { "https://\(host):\(port)" }
    var wsUrl: String { "wss://\(host):\(port)/websocket?type=main" }

    init(host: String, port: Int, username: String, password: String, accessCode: String = "") {
        self.host = host
            .trimmingCharacters(in: .whitespaces)
            .removingPrefix("https://")
            .removingPrefix("http://")
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        self.port = port
        self.username = username.trimmingCharacters(in: .whitespaces)
        self.password = password
        self.accessCode = accessCode.trimmingCharacters(in: .whitespaces)
    }
}

private extension String {
    func removingPrefix(_ prefix: String) -> String {
        hasPrefix(prefix) ? String(dropFirst(prefix.count)) : self
    }
}

/// [FnOsApi] 抛出的「需要两步验证」信号，UI 据此弹出验证码输入框。
final class FnOsTwoFactorRequired: Error {
    let challenge: FnOsTwoFactorChallenge
    init(_ challenge: FnOsTwoFactorChallenge) { self.challenge = challenge }
}

/// 网关层（nginx）返回的 HTTP 错误，不是业务 JSON 里的 code。
struct FnOsHttpError: Error {
    let status: Int
    let message: String
}

/// 等待动态码的登录会话：临时凭证、设备标识与握手公钥都要原样带进验证请求。
private struct PendingTwoFactor {
    let challenge: FnOsTwoFactorChallenge
    let accessToken: String
    let did: String
    let pub: String
    let si: String
}

/// 飞牛相册 API 会话（Swift 移植自 Android 端 `FnOsApi.kt`）：
///  - WebSocket 加密登录，socket 保持打开并定时 ping；
///  - 业务请求带 `authx` 签名（raw/encoded 自动协商）；
///  - AccessToken 失效时自动重登一次；
///  - 所有图片/视频和 API 共用 `FnOsTransport` 的 URLSession。
actor FnOsApi {

    private let access: FnOsAccess

    private var session: FnOsSession?
    private var wsTask: URLSessionWebSocketTask?
    private var wsDelegate: FnOsWsDelegate?
    private var pingTask: Task<Void, Never>?
    private var signMode = FnOsSigner.SignMode.raw
    private var wsOpen = false

    /// WS 握手响应的缓冲（握手是严格串行的：一条请求等一条响应）。
    private var wsBuffer: [Json] = []

    /// 等待两步验证的登录会话。非 nil 时：
    ///  - WebSocket 保持打开（验证码要在同一连接上提交）；
    ///  - 任何请求都直接抛 `FnOsTwoFactorRequired`，避免并发请求反复重登。
    private var pendingTwoFactor: PendingTwoFactor?

    /// 进行中的登录任务（actor 串行 + 任务去重，等价于 Android 端的 loginMutex）
    private var loginTask: Task<FnOsSession, Error>?

    init(access: FnOsAccess) {
        self.access = access
    }

    deinit {
        pingTask?.cancel()
        wsTask?.cancel(with: .goingAway, reason: nil)
    }

    // MARK: - 对外

    /// 登录 + stat：连接测试用（每次都重新登录，确保验证的是当前凭据）
    func testConnection() async -> FnOsProbeResult {
        let started = Date()
        do {
            _ = try await ensureSession(force: true)
            let stats = try await stat()
            return FnOsProbeResult(
                ok: true,
                latencyMs: Int(Date().timeIntervalSince(started) * 1000),
                stats: stats,
                error: nil,
                twoFactor: nil,
            )
        } catch let e as FnOsTwoFactorRequired {
            // 不是失败：保留 WebSocket，等 UI 收到动态码后走 verifyTwoFactor
            return FnOsProbeResult(ok: false, stats: nil, error: nil, twoFactor: e.challenge)
        } catch {
            return FnOsProbeResult(
                ok: false,
                stats: nil,
                error: Self.describe(error, access: access),
                twoFactor: nil,
            )
        }
    }

    func stat() async throws -> FnOsStats {
        let data = try await getJson("/p/api/v1/user_photo/stat")
        return FnOsStats(photoCount: data.int("photoCount"), videoCount: data.int("videoCount"))
    }

    /// 带签名 GET；params 会按 key 排序后同时用于 URL 与签名
    func getJson(_ path: String, params: [KeyValue] = []) async throws -> Json {
        try await requestJson(path, params, body: nil)
    }

    /// 只读 AI 搜索也使用 POST；序列化一次，保证签名与发送的 JSON 完全一致。
    func postJson(_ path: String, body: Json) async throws -> Json {
        try await requestJson(path, [], body: Self.encode(body.raw))
    }

    func close() {
        pingTask?.cancel()
        pingTask = nil
        wsDelegate?.invalidate()
        wsTask?.cancel(with: .goingAway, reason: nil)
        wsTask = nil
        session = nil
        pendingTwoFactor = nil
    }

    func hasPendingTwoFactor() -> Bool {
        pendingTwoFactor != nil
    }

    /// 放弃等待中的两步验证：关掉半登录连接，API 本身仍可重新登录
    func cancelTwoFactor() {
        pendingTwoFactor = nil
        wsDelegate?.invalidate()
        wsTask?.cancel(with: .goingAway, reason: nil)
        wsTask = nil
        wsOpen = false
        session = nil
    }

    /// 提交两步验证动态码，完成后本 API 即可正常请求。
    ///
    /// 与登录一样走 RSA+AES 加密信封，且必须在登录用的同一 WebSocket 上发送
    /// （`accessToken` 只对该连接有效）。
    func verifyTwoFactor(code: String) async throws {
        let digits = code.filter(\.isNumber)
        guard digits.count == 6 else { throw FnOsError("请输入 6 位动态验证码") }
        guard let pending = pendingTwoFactor else {
            throw FnOsError("两步验证会话已过期，请重新测试连接")
        }
        let payload = Json([
            "req": "user.2fa.loginVerify",
            "reqid": FnOsReqId.shared.next(),
            "si": pending.si,
            "accessToken": pending.accessToken,
            "code": digits,
            // 与飞牛 Web 端一致：勾选「信任此设备」时 stay=2，服务端按 did 记住本机
            "stay": 2,
            "isTrustedDevice": true,
            "deviceName": Self.deviceName,
            "deviceType": Self.deviceType,
            "did": pending.did,
        ])
        let reply = try await wsRequest(try encryptEnvelope(pending.pub, payload))
        let errno = reply.int("errno")
        let result = reply.string("result")
        if errno != 0 || result != "succ" {
            if errno == Self.errnoInvalidCredential {
                throw FnOsError("动态验证码错误或已过期，请重新输入")
            }
            throw FnOsError(Self.loginErrorText(errno: errno, result: result))
        }
        let token = reply.string("token")
        guard !token.isEmpty else { throw FnOsError("两步验证通过，但 NAS 没有返回 AccessToken") }
        FnOsReqId.shared.setBackId(reply.string("backId"))
        let newSession = FnOsSession(token: token, secret: reply.string("secret"))
        session = newSession
        FnOsTransport.shared.token = newSession.token
        pendingTwoFactor = nil
        startPing()
    }

    // MARK: - 请求

    private func requestJson(
        _ path: String,
        _ params: [KeyValue],
        body: String?,
    ) async throws -> Json {
        var current = try await ensureSession(force: false)
        var authRetried = false
        var signRetried = false
        var accessCodeRetried = false
        while true {
            let result: ExecResult
            do {
                result = try await execute(current, path: path, params: params, body: body)
            } catch let e as FnOsHttpError {
                // 访问码 cookie 过期：网关直接回 HTTP 401，此时还没有走到业务 JSON，
                // 上面按 code 判定的重登分支永远看不到它。重新校验访问码后再试一次。
                let expired = e.status == 401 && !access.accessCode.isEmpty
                if !expired || accessCodeRetried { throw e }
                accessCodeRetried = true
                try await verifyAccessCode()
                current = try await ensureSession(force: true)
                continue
            }
            switch result.code {
            case 401, 5001:
                if authRetried { throw FnOsError("AccessToken 已失效，请重新登录") }
                authRetried = true
                current = try await ensureSession(force: true)

            case 5000:
                if signRetried || body != nil { throw FnOsError("authx 签名被拒绝") }
                signRetried = true
                signMode = signMode.next()

            case 0:
                return result.data

            default:
                throw FnOsError(
                    result.message.isEmpty
                        ? "业务请求失败（code \(result.code)）"
                        : result.message,
                )
            }
        }
    }

    private struct ExecResult {
        let code: Int
        let message: String
        let data: Json
    }

    private func execute(
        _ session: FnOsSession,
        path: String,
        params: [KeyValue],
        body: String?,
    ) async throws -> ExecResult {
        // session 只用于保证调用前已登录；AccessToken 由 FnOsTransport 统一注入
        _ = session
        let query = FnOsSigner.payload(params, mode: .encoded)
        let urlString = access.baseUrl + path + (query.isEmpty ? "" : "?\(query)")
        guard let url = URL(string: urlString) else { throw FnOsError("NAS 地址不合法：\(urlString)") }
        var request = URLRequest(url: url)
        request.timeoutInterval = 30
        request.setValue(session.token, forHTTPHeaderField: "AccessToken")
        request.setValue(
            body == nil
                ? FnOsSigner.authHeader(path: path, params: params, mode: signMode)
                : FnOsSigner.authJsonHeader(path: path, body: body ?? ""),
            forHTTPHeaderField: "authx",
        )
        if let body {
            request.httpMethod = "POST"
            request.httpBody = Data(body.utf8)
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        }
        let (data, response) = try await FnOsTransport.shared.session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw FnOsError("NAS 响应不是 HTTP") }
        guard (200..<300).contains(http.statusCode) else {
            throw FnOsHttpError(
                status: http.statusCode,
                message: http.statusCode == 401
                    ? "访问码已失效或未通过校验（HTTP 401）"
                    : "NAS 返回 HTTP \(http.statusCode)",
            )
        }
        guard let root = try? JSONSerialization.jsonObject(with: data) else {
            throw FnOsError("NAS 返回的不是 JSON")
        }
        let json = Json(root)
        let code = json.raw["code"] != nil ? json.int("code", -1) : json.int("errno", -1)
        if code != 0 {
            let message = json.string("msg").isEmpty ? json.string("result") : json.string("msg")
            return ExecResult(code: code, message: message, data: Json([:]))
        }
        // 智能分类在部分 fnOS 版本里直接返回 data 数组，统一包装为 list。
        let payloadData: Any
        switch json.raw["data"] {
        case let dict as [String: Any]: payloadData = dict
        case let list as [[String: Any]]: payloadData = ["list": list]
        default: payloadData = [String: Any]()
        }
        return ExecResult(code: 0, message: "", data: Json(payloadData))
    }

    // MARK: - 登录

    private func ensureSession(force: Bool) async throws -> FnOsSession {
        // 卡在两步验证时不能重新登录：那会丢掉服务端下发的临时凭证和当前连接。
        if let pending = pendingTwoFactor { throw FnOsTwoFactorRequired(pending.challenge) }
        if !force, let session { return session }
        if let loginTask {
            // 已有登录在途：等它结束即可（结果同样有效）
            return try await loginTask.value
        }
        let task = Task<FnOsSession, Error> {
            try await self.performLogin()
        }
        loginTask = task
        defer { loginTask = nil }
        let result = try await task.value
        session = result
        return result
    }

    private func performLogin() async throws -> FnOsSession {
        // 重登前关掉上一条 WebSocket，避免连接和 25s 心跳任务泄漏。
        pingTask?.cancel()
        pingTask = nil
        wsDelegate?.invalidate()
        wsTask?.cancel(with: .goingAway, reason: nil)
        wsTask = nil
        FnOsReqId.shared.reset()
        wsBuffer = []
        wsOpen = false

        if !access.accessCode.isEmpty {
            try await verifyAccessCode()
        }

        let delegate = FnOsWsDelegate()
        wsDelegate = delegate
        guard let wsUrl = URL(string: access.wsUrl) else { throw FnOsError("WSS 地址不合法") }
        let task = FnOsTransport.shared.session.webSocketTask(with: wsUrl)
        wsTask = task
        task.delegate = delegate
        delegate.onMessage = { [weak self] text in
            Task { await self?.handleWsMessage(text) }
        }
        delegate.onOpened = { [weak self] in
            Task { await self?.handleWsOpened() }
        }
        delegate.onClosed = { [weak self] in
            Task { await self?.handleWsClosed() }
        }
        task.resume()
        delegate.attach(to: task)

        do {
            let hello = try await wsRequest(Json([
                "req": "util.crypto.getRSAPub",
                "reqid": FnOsReqId.shared.next(),
            ]))
            let helloErrno = hello.int("errno", 0)
            guard helloErrno == 0 else {
                throw FnOsError("登录握手被拒绝（errno \(helloErrno)）")
            }
            // si 必须按字符串原样回传：当数字解析会丢精度，服务端回 errno 8192
            let pub = hello.string("pub")
            let si = hello.string("si")
            guard !pub.isEmpty, !si.isEmpty else { throw FnOsError("NAS 没有返回 RSA 公钥") }

            // 固定 did：服务端按它记住「信任此设备」，否则每次冷启动都要重输动态码
            let did = FnOsDeviceId.get()
            let payload = Json([
                "req": "user.login",
                "reqid": FnOsReqId.shared.next(),
                "si": si,
                "user": access.username,
                "password": access.password,
                "stay": true,
                "deviceType": Self.deviceType,
                "deviceName": Self.deviceName,
                "did": did,
            ])

            let reply = try await wsRequest(try encryptEnvelope(pub, payload))
            let errno = reply.int("errno", 0)
            let result = reply.string("result")
            guard errno == 0, result == "succ" else {
                throw FnOsError(Self.loginErrorText(errno: errno, result: result))
            }
            return try classifyLoginReply(reply, pub: pub, si: si, did: did)
        } catch let e as FnOsTwoFactorRequired {
            // 连接要留给 verifyTwoFactor，不能走下面的断连清理
            throw e
        } catch {
            pendingTwoFactor = nil
            delegate.invalidate()
            task.cancel(with: .goingAway, reason: nil)
            if wsTask === task { wsTask = nil }
            throw error
        }
    }

    /// 登录响应分类。fnOS 在密码正确但需要两步验证时，`errno=0`、`result=succ`，
    /// 但 `token` 为空，只给一个临时 `accessToken`。
    private func classifyLoginReply(_ reply: Json, pub: String, si: String, did: String) throws -> FnOsSession {
        let token = reply.string("token").trimmingCharacters(in: .whitespaces)
        if !token.isEmpty {
            FnOsReqId.shared.setBackId(reply.string("backId"))
            pendingTwoFactor = nil
            let newSession = FnOsSession(token: token, secret: reply.string("secret"))
            FnOsTransport.shared.token = newSession.token
            startPing()
            return newSession
        }

        let bound = reply.bool("isBindTwofaSecret")
        let enforced = reply.bool("isTwofaEnforced")
        let accessToken = reply.string("accessToken").trimmingCharacters(in: .whitespaces)
        // secureEmail 非空 = 走邮箱验证码流程，电视端收不到邮件，只能引导用户改绑验证器
        let emailOnly = !reply.string("secureEmail").isEmpty
        if bound {
            guard !accessToken.isEmpty else {
                throw FnOsError("NAS 要求两步验证，但没有返回临时凭证")
            }
            // 保留 socket / 缓冲状态：动态码必须和这次登录在同一连接上提交
            let pending = PendingTwoFactor(
                challenge: FnOsTwoFactorChallenge(totpBound: true),
                accessToken: accessToken,
                did: did,
                pub: pub,
                si: si,
            )
            pendingTwoFactor = pending
            throw FnOsTwoFactorRequired(pending.challenge)
        }
        if enforced {
            throw FnOsError(
                emailOnly
                    ? "该账号使用邮箱两步验证，电视端收不到邮件：请在飞牛 App 的账号安全里改为验证器（动态码）后再连接"
                    : "该账号被要求开启两步验证，请先在飞牛 App 中绑定验证器后再连接",
            )
        }
        throw FnOsError("登录成功但 NAS 没有返回 AccessToken（响应字段：\(reply.keys.joined(separator: ", "))）")
    }

    /// 登录 / 两步验证共用的加密信封（AES-256-CBC + RSA 加密 AES key）
    private func encryptEnvelope(_ pub: String, _ payload: Json) throws -> Json {
        let aesKey = FnOsCrypto.randomAscii(32)
        let iv = FnOsCrypto.randomBytes(16)
        let plain = Data(Self.encode(payload.raw).utf8)
        return Json([
            "req": "encrypted",
            "iv": iv.base64EncodedString(),
            "rsa": try FnOsCrypto.rsaEncrypt(spkiPem: pub, data: Data(aesKey.utf8)).base64EncodedString(),
            "aes": try FnOsCrypto.aesCbcEncrypt(plain, asciiKey: aesKey, iv: iv).base64EncodedString(),
        ])
    }

    /// 发送一条 WS 消息并等待下一条响应（12s 超时）。
    ///
    /// 握手是严格串行的，这里用「发送 + 轮询缓冲」拿到响应，避免跨 actor
    /// 的 continuation 管理（超时/关闭/重登三方竞争时极易泄漏）。
    private func wsRequest(_ message: Json) async throws -> Json {
        guard wsTask != nil else { throw FnOsError("登录连接已断开") }
        // task.resume() 之后 TLS 握手 + WS 升级是异步的：这里必须等 didOpen，
        // 否则在本地 NAS 上会随机出现「刚发消息就报连接已断开」的竞态。
        let openDeadline = Date().addingTimeInterval(12)
        while !wsOpen {
            guard wsTask != nil else { throw FnOsError("登录连接已断开") }
            if Date() >= openDeadline { throw FnOsError("等待 NAS WebSocket 连接超时") }
            try await Task.sleep(nanoseconds: 20_000_000)
        }
        guard let task = wsTask else { throw FnOsError("登录连接已断开") }
        let data = try JSONSerialization.data(withJSONObject: message.raw)
        guard let text = String(data: data, encoding: .utf8) else {
            throw FnOsError("WS 消息编码失败")
        }
        // 飞牛 NAS 只处理 text frame（opcode 0x1）；用 .data 发 binary frame 会被静默忽略。
        try await task.send(.string(text))
        // 用 reqid 精确匹配响应：NAS 会在同一连接上推心跳/通知，
        // 简单 removeFirst() 会把别人的消息当成本请求的响应。
        let requestReqid = message.string("reqid")
        let deadline = Date().addingTimeInterval(12)
        while true {
            if let index = wsBuffer.firstIndex(where: { reply in
                let rid = reply.string("reqid")
                if requestReqid.isEmpty { return true }
                return rid.isEmpty || rid == requestReqid
            }) {
                return wsBuffer.remove(at: index)
            }
            if !wsOpen { throw FnOsError("登录连接已断开") }
            if Date() >= deadline { throw FnOsError("等待 NAS 响应超时") }
            try await Task.sleep(nanoseconds: 50_000_000)
        }
    }

    private func handleWsOpened() {
        wsOpen = true
    }

    private func handleWsMessage(_ text: String) {
        guard let obj = try? JSONSerialization.jsonObject(with: Data(text.utf8)) else { return }
        let json = Json(obj)
        // 服务端主动推送（如 {"sysNotify":"tokenExpired"}）不是任何请求的响应，
        // 进缓冲会被 wsRequest 误当响应消费。这里直接丢弃。
        guard json.string("sysNotify").isEmpty else { return }
        wsBuffer.append(json)
    }

    private func handleWsClosed() {
        wsOpen = false
    }

    private func startPing() {
        pingTask?.cancel()
        guard wsTask != nil else { return }
        pingTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 25_000_000_000)
                guard let task = await self?.wsTask else { return }
                _ = try? await task.send(.string(#"{"req":"ping"}"#))
            }
        }
    }

    private func verifyAccessCode() async throws {
        let code = Data(access.accessCode.utf8).base64EncodedString()
        guard let url = URL(string: access.baseUrl + "/access_code_verify") else {
            throw FnOsError("NAS 地址不合法")
        }
        var request = URLRequest(url: url)
        request.timeoutInterval = 30
        request.setValue(code, forHTTPHeaderField: "x-access-code")
        request.setValue("web", forHTTPHeaderField: "x-access-source")
        let (_, response) = try await FnOsTransport.shared.session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw FnOsError("访问码校验失败") }
        guard (200..<300).contains(http.statusCode) else {
            // 401 = 访问码本身不对（或已被管理员改掉），其余状态码是网关/网络问题
            throw FnOsHttpError(
                status: http.statusCode,
                message: http.statusCode == 401
                    ? "访问码不正确，请在设置里重新填写"
                    : "访问码校验失败（HTTP \(http.statusCode)）",
            )
        }
    }

    // MARK: - 文案

    static let deviceType = "AndroidTV" // 与 Android 端一致，保证服务端兼容
    static let deviceName = "NAS Photo TV (Apple TV)"

    /// fnOS 的 E_INVALID_TOKEN：两步验证里表示动态码错误 / 已过期
    private static let errnoInvalidCredential = 135_168

    private static func loginErrorText(errno: Int, result: String) -> String {
        switch errno {
        case 401:
            return "会话被拒绝：可能需要访问码，或上一次登录状态没有重置"
        case 8192:
            return "登录参数被拒绝（si 必须以字符串回传）"
        case 131_072:
            return "账号或密码错误"
        // 连续输错密码后 fnOS 会临时封禁来源 IP（Web 端提示「当前 IP 已被封禁」）
        case 131_089:
            return "登录失败次数过多，NAS 已临时封禁本机 IP，请稍后再试"
        case 135_168:
            return "动态验证码错误或已过期"
        default:
            if !result.isEmpty && result != "succ" {
                return "登录失败：\(result)"
            }
            return "登录失败（errno \(errno)）"
        }
    }

    static func describe(_ error: Error, access: FnOsAccess) -> String {
        if let e = error as? FnOsError { return e.message }
        if let e = error as? FnOsHttpError { return e.message }
        if error is FnOsTwoFactorRequired { return "该账号已开启两步验证" }
        let ns = error as NSError
        switch ns.code {
        case NSURLErrorCannotFindHost, NSURLErrorBadURL:
            return "找不到主机 \(access.host)"
        case NSURLErrorTimedOut:
            return "连接超时，检查地址与端口"
        case NSURLErrorCannotConnectToHost:
            return "无法连接 \(access.host):\(access.port)（确认是 HTTPS 端口）"
        case NSURLErrorSecureConnectionFailed, NSURLErrorServerCertificateUntrusted:
            if FnOsTofuDelegate.shared.pinMismatch {
                return "NAS 证书指纹已变化，已清除旧指纹，请再试一次新证书"
            }
            return "TLS 握手失败（自签证书）"
        default:
            return ns.localizedDescription
        }
    }

    private static func encode(_ obj: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: obj) else { return "{}" }
        return String(data: data, encoding: .utf8) ?? "{}"
    }
}

/// URLSessionWebSocketTask 的消息转发器：把回调桥回 actor。
final class FnOsWsDelegate: NSObject, URLSessionWebSocketDelegate {

    var onMessage: ((String) -> Void)?
    var onOpened: (() -> Void)?
    var onClosed: (() -> Void)?

    private let lock = NSLock()
    private var receiveLoopStarted = false

    /// 启动接收循环（幂等）。
    func attach(to task: URLSessionWebSocketTask) {
        lock.lock()
        if receiveLoopStarted {
            lock.unlock()
            return
        }
        receiveLoopStarted = true
        lock.unlock()
        scheduleReceive(task)
    }

    private func scheduleReceive(_ task: URLSessionWebSocketTask) {
        task.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.onMessage?(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) { self.onMessage?(text) }
                @unknown default:
                    break
                }
                self.scheduleReceive(task)
            case .failure:
                self.onClosed?()
            }
        }
    }

    func invalidate() {
        onMessage = nil
        onOpened = nil
        onClosed = nil
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        onOpened?()
        attach(to: webSocketTask)
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        onClosed?()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if error != nil { onClosed?() }
    }
}
