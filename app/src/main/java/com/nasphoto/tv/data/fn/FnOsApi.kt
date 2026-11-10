package com.nasphoto.tv.data.fn

import android.util.Base64
import com.nasphoto.tv.data.model.NasConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.json.JSONArray
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 一次连接所需的配置（端口是飞牛的 Web HTTPS 端口，默认 5667） */
data class FnOsAccess(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val accessCode: String = "",
) {
    val baseUrl: String get() = "https://$host:$port"
    val wsUrl: String get() = "wss://$host:$port/websocket?type=main"
}

data class FnOsSession(val token: String, val secret: String)

data class FnOsStats(val photoCount: Int, val videoCount: Int)

data class FnOsProbeResult(
    val ok: Boolean,
    val latencyMs: Int = 0,
    val stats: FnOsStats? = null,
    val error: String? = null,
    /** 非 null = 账号已开启两步验证，需要用户输入动态码；调用 [FnOsApi.verifyTwoFactor] 完成登录 */
    val twoFactor: FnOsTwoFactorChallenge? = null,
)

/**
 * 密码正确，但当前设备不在信任列表里，fnOS 要求再输一次动态验证码。
 *
 * 这不是错误，而是一个中间态：WebSocket 必须保持打开，验证码要和登录用同一个会话提交。
 */
data class FnOsTwoFactorChallenge(
    /** true = 账号已绑定验证器 App（TOTP）；false = 只绑了邮箱，客户端暂不支持 */
    val totpBound: Boolean = true,
)

/** [FnOsApi] 内部抛出的「需要两步验证」信号，UI 据此弹出验证码输入框。 */
class FnOsTwoFactorRequired(val challenge: FnOsTwoFactorChallenge) :
    Exception("该账号已开启两步验证")

fun NasConnection.toFnOsAccess(): FnOsAccess = FnOsAccess(
    host = host.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/'),
    port = port,
    username = username.trim(),
    password = password,
    accessCode = accessCode.trim(),
)

internal class FnOsException(
    message: String,
    cause: Throwable? = null,
    /** 非 null = 这是网关层（nginx）返回的 HTTP 错误，不是业务 JSON 里的 code */
    val httpStatus: Int? = null,
) : Exception(message, cause)

/**
 * 飞牛相册 API 会话：
 *  - WebSocket 加密登录，socket 保持打开并定时 ping；
 *  - 业务请求带 `authx` 签名（raw/encoded 自动协商）；
 *  - AccessToken 失效时自动重登一次；
 *  - 所有图片/视频和 API 共用 [FnOsTransport] 的 OkHttp 客户端。
 */
class FnOsApi(private val access: FnOsAccess) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loginMutex = Mutex()

    @Volatile
    private var session: FnOsSession? = null

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var handshakeInbox: Channel<JSONObject>? = null

    private var pingJob: Job? = null
    private var signMode = SignMode.RAW

    /**
     * 等待两步验证的登录会话。非 null 时：
     *  - WebSocket 保持打开（验证码要在同一连接上提交）；
     *  - 任何请求都直接抛 [FnOsTwoFactorRequired]，避免并发协程反复重登、覆盖这个会话。
     */
    @Volatile
    private var pendingTwoFactor: PendingTwoFactor? = null

    // ------------------------------------------------------------------ 对外

    /** 登录 + stat：连接测试用（每次都重新登录，确保验证的是当前凭据） */
    suspend fun testConnection(): FnOsProbeResult {
        val started = System.nanoTime()
        return try {
            ensureSession(force = true)
            val stats = stat()
            FnOsProbeResult(
                ok = true,
                latencyMs = ((System.nanoTime() - started) / 1_000_000L).toInt(),
                stats = stats,
            )
        } catch (e: FnOsTwoFactorRequired) {
            // 不是失败：保留 WebSocket，等 UI 收到动态码后走 verifyTwoFactor
            FnOsProbeResult(ok = false, error = e.message, twoFactor = e.challenge)
        } catch (e: Exception) {
            FnOsProbeResult(ok = false, error = describe(e))
        }
    }

    suspend fun stat(): FnOsStats {
        val data = getJson("/p/api/v1/user_photo/stat")
        return FnOsStats(
            photoCount = data.optInt("photoCount", 0),
            videoCount = data.optInt("videoCount", 0),
        )
    }

    /** 带签名 GET；[params] 会按 key 排序后同时用于 URL 与签名 */
    suspend fun getJson(path: String, params: List<Pair<String, String>> = emptyList()): JSONObject =
        requestJson(path, params, body = null)

    /** 只读 AI 搜索也使用 POST；序列化一次，保证签名与发送的 JSON 完全一致。 */
    suspend fun postJson(path: String, body: JSONObject): JSONObject =
        requestJson(path, emptyList(), body.toString())

    private suspend fun requestJson(
        path: String,
        params: List<Pair<String, String>>,
        body: String?,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            var current = ensureSession(force = false)
            var authRetried = false
            var signRetried = false
            var accessCodeRetried = false
            while (true) {
                val result = try {
                    execute(current, path, params, body)
                } catch (e: FnOsException) {
                    // 访问码 cookie 过期：网关直接回 HTTP 401，此时还没有走到业务 JSON，
                    // 上面按 code 判定的重登分支永远看不到它。重新校验访问码后再试一次。
                    val expired = e.httpStatus == 401 && access.accessCode.isNotBlank()
                    if (!expired || accessCodeRetried) throw e
                    accessCodeRetried = true
                    withContext(Dispatchers.IO) { verifyAccessCode() }
                    current = ensureSession(force = true)
                    continue
                }
                when {
                    result.code == 401 || result.code == 5001 -> {
                        if (authRetried) throw FnOsException("AccessToken 已失效，请重新登录")
                        authRetried = true
                        current = ensureSession(force = true)
                    }

                    result.code == 5000 -> {
                        if (signRetried || body != null) throw FnOsException("authx 签名被拒绝")
                        signRetried = true
                        signMode = signMode.next()
                    }

                    result.code != 0 -> throw FnOsException(
                        result.message.ifBlank { "业务请求失败（code ${result.code}）" },
                    )

                    else -> return@withContext result.data
                }
            }
            @Suppress("UNREACHABLE_CODE")
            JSONObject()
        }

    override fun close() {
        pingJob?.cancel()
        runCatching { socket?.close(1000, "bye") }
        socket = null
        session = null
        pendingTwoFactor = null
        handshakeInbox = null
        scope.cancel()
    }

    // ------------------------------------------------------------------ 请求

    private fun execute(
        session: FnOsSession,
        path: String,
        params: List<Pair<String, String>>,
        requestBody: String?,
    ): ApiResult {
        // session 只用于保证调用前已登录；AccessToken 由 FnOsTransport 拦截器统一注入
        @Suppress("UNUSED_EXPRESSION")
        session
        val query = FnOsSigner.payload(params, SignMode.ENCODED)
        val url = access.baseUrl + path + if (query.isEmpty()) "" else "?$query"
        val builder = Request.Builder()
            .url(url)
            .header("AccessToken", session.token)
            .header("authx", if (requestBody == null) {
                FnOsSigner.authHeader(path, params, signMode)
            } else {
                FnOsSigner.authJsonHeader(path, requestBody)
            })
        val request = if (requestBody == null) {
            builder.get().build()
        } else {
            builder.post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        }
        FnOsTransport.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw FnOsException(
                    if (response.code == 401) "访问码已失效或未通过校验（HTTP 401）"
                    else "NAS 返回 HTTP ${response.code}",
                    httpStatus = response.code,
                )
            }
            val json = runCatching { JSONObject(body) }
                .getOrElse { throw FnOsException("NAS 返回的不是 JSON") }
            val code = if (json.has("code")) json.optInt("code") else json.optInt("errno", -1)
            if (code != 0) {
                return ApiResult(code, json.optString("msg").ifBlank { json.optString("result") })
            }
            // 智能分类在部分 fnOS 版本里直接返回 data 数组，统一包装为 list。
            val data = when (val value = json.opt("data")) {
                is JSONObject -> value
                is JSONArray -> JSONObject().put("list", value)
                else -> JSONObject()
            }
            return ApiResult(0, "", data)
        }
    }

    // ------------------------------------------------------------------ 登录

    private suspend fun ensureSession(force: Boolean): FnOsSession = loginMutex.withLock {
        // 卡在两步验证时不能重新登录：那会丢掉服务端下发的临时凭证和当前连接。
        pendingTwoFactor?.let { throw FnOsTwoFactorRequired(it.challenge) }
        if (!force) session?.let { return@withLock it }

        pingJob?.cancel()
        runCatching { socket?.close(1000, "relogin") }
        socket = null

        if (access.accessCode.isNotBlank()) verifyAccessCode()

        val newSession = withContext(Dispatchers.IO) { performLogin() }
        session = newSession
        FnOsTransport.token = newSession.token
        startPing()
        newSession
    }

    /** 必须在 IO 上调用 */
    private suspend fun performLogin(): FnOsSession {
        FnOsReqId.reset()
        val inbox = Channel<JSONObject>(Channel.UNLIMITED)
        handshakeInbox = inbox

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { JSONObject(text) }.getOrNull()?.let { handshakeInbox?.trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handshakeInbox?.close(t)
                if (socket === webSocket) {
                    socket = null
                    session = null
                }
            }
        }

        val ws = FnOsTransport.client.newWebSocket(
            Request.Builder().url(access.wsUrl).build(),
            listener,
        )
        socket = ws

        try {
            val hello = request(
                inbox,
                JSONObject().put("req", "util.crypto.getRSAPub").put("reqid", FnOsReqId.next()),
            )
            val helloErrno = hello.optInt("errno", 0)
            if (helloErrno != 0) throw FnOsException("登录握手被拒绝（errno $helloErrno）")
            // si 必须按字符串原样回传：当数字解析会丢精度，服务端回 errno 8192
            val pub = hello.optString("pub")
            val si = hello.optString("si")
            if (pub.isBlank() || si.isBlank()) throw FnOsException("NAS 没有返回 RSA 公钥")

            // 固定 did：服务端按它记住「信任此设备」，否则每次冷启动都要重输动态码
            val did = FnOsDeviceId.get()
            val payload = JSONObject()
                .put("req", "user.login")
                .put("reqid", FnOsReqId.next())
                .put("si", si)
                .put("user", access.username)
                .put("password", access.password)
                .put("stay", true)
                .put("deviceType", "AndroidTV")
                .put("deviceName", "NAS Photo TV")
                .put("did", did)

            val reply = request(inbox, encryptEnvelope(pub, payload))
            val errno = reply.optInt("errno", 0)
            val result = reply.optString("result")
            if (errno != 0 || result != "succ") throw FnOsException(loginErrorText(errno, result))

            when (val outcome = classifyLoginReply(reply)) {
                is FnOsLoginReply.Success -> {
                    FnOsReqId.setBackId(reply.optString("backId"))
                    pendingTwoFactor = null
                    return FnOsSession(token = outcome.token, secret = reply.optString("secret"))
                }

                is FnOsLoginReply.TwoFactor -> {
                    if (outcome.accessToken.isBlank()) {
                        throw FnOsException("NAS 要求两步验证，但没有返回临时凭证")
                    }
                    // 保留 socket / inbox：动态码必须和这次登录在同一连接上提交
                    val pending = PendingTwoFactor(
                        challenge = FnOsTwoFactorChallenge(totpBound = true),
                        accessToken = outcome.accessToken,
                        did = did,
                        pub = pub,
                        si = si,
                        inbox = inbox,
                    )
                    pendingTwoFactor = pending
                    throw FnOsTwoFactorRequired(pending.challenge)
                }

                is FnOsLoginReply.SetupRequired -> throw FnOsException(
                    if (outcome.emailOnly) {
                        "该账号使用邮箱两步验证，电视端收不到邮件：请在飞牛 App 的账号安全里改为验证器（动态码）后再连接"
                    } else {
                        "该账号被要求开启两步验证，请先在飞牛 App 中绑定验证器后再连接"
                    },
                )

                is FnOsLoginReply.MissingToken -> throw FnOsException(
                    buildString {
                        append("登录成功但 NAS 没有返回 AccessToken")
                        if (outcome.fields.isNotEmpty()) {
                            append("（响应字段：")
                            append(outcome.fields.joinToString(", "))
                            append("）")
                        }
                    },
                )
            }
        } catch (t: FnOsTwoFactorRequired) {
            // 连接要留给 verifyTwoFactor，不能走下面的断连清理
            throw t
        } catch (t: Throwable) {
            pendingTwoFactor = null
            runCatching { ws.cancel() }
            if (socket === ws) socket = null
            throw t
        } finally {
            if (pendingTwoFactor == null) handshakeInbox = null
        }
    }

    /**
     * 提交两步验证动态码，完成后本 API 即可正常请求。
     *
     * 与登录一样走 RSA+AES 加密信封，且必须在登录用的同一 WebSocket 上发送
     * （`accessToken` 只对该连接有效）。
     */
    suspend fun verifyTwoFactor(code: String): Unit = withContext(Dispatchers.IO) {
        val digits = code.filter(Char::isDigit)
        if (digits.length != 6) throw FnOsException("请输入 6 位动态验证码")
        loginMutex.withLock {
            val pending = pendingTwoFactor
                ?: throw FnOsException("两步验证会话已过期，请重新测试连接")
            val payload = JSONObject()
                .put("req", "user.2fa.loginVerify")
                .put("reqid", FnOsReqId.next())
                .put("si", pending.si)
                .put("accessToken", pending.accessToken)
                .put("code", digits)
                // 与飞牛 Web 端一致：勾选「信任此设备」时 stay=2，服务端按 did 记住本机
                .put("stay", 2)
                .put("isTrustedDevice", true)
                .put("deviceName", "NAS Photo TV")
                .put("deviceType", "AndroidTV")
                .put("did", pending.did)

            val reply = request(pending.inbox, encryptEnvelope(pending.pub, payload))
            val errno = reply.optInt("errno", 0)
            val result = reply.optString("result")
            if (errno != 0 || result != "succ") {
                throw FnOsException(
                    if (errno == ERRNO_INVALID_CREDENTIAL) "动态验证码错误或已过期，请重新输入"
                    else loginErrorText(errno, result),
                )
            }
            val token = reply.optString("token")
            if (token.isBlank()) throw FnOsException("两步验证通过，但 NAS 没有返回 AccessToken")
            FnOsReqId.setBackId(reply.optString("backId"))
            val newSession = FnOsSession(token = token, secret = reply.optString("secret"))
            session = newSession
            FnOsTransport.token = newSession.token
            pendingTwoFactor = null
            handshakeInbox = null
            startPing()
        }
    }

    /** 是否还有一个可以提交动态码的会话（验证失败后 UI 据此决定要不要让用户重试） */
    fun hasPendingTwoFactor(): Boolean = pendingTwoFactor != null

    /** 放弃等待中的两步验证：关掉半登录连接，API 本身仍可重新登录 */
    fun cancelTwoFactor() {
        pendingTwoFactor = null
        handshakeInbox = null
        runCatching { socket?.close(1000, "cancel 2fa") }
        socket = null
        session = null
    }

    /** 登录 / 两步验证共用的加密信封（AES-256-CBC + RSA 加密 AES key） */
    private fun encryptEnvelope(pub: String, payload: JSONObject): JSONObject {
        val aesKey = FnOsCrypto.randomAscii(32)
        val iv = FnOsCrypto.randomBytes(16)
        return JSONObject()
            .put("req", "encrypted")
            .put("iv", FnOsCrypto.base64(iv))
            .put("rsa", FnOsCrypto.base64(FnOsCrypto.rsaEncrypt(pub, aesKey.toByteArray(Charsets.UTF_8))))
            .put(
                "aes",
                FnOsCrypto.base64(
                    FnOsCrypto.aesCbcEncrypt(
                        payload.toString().toByteArray(Charsets.UTF_8),
                        aesKey,
                        iv,
                    ),
                ),
            )
    }

    private suspend fun request(inbox: Channel<JSONObject>, message: JSONObject): JSONObject {
        val ws = socket ?: throw FnOsException("登录连接已断开")
        if (!ws.send(message.toString())) throw FnOsException("登录连接已断开")
        return try {
            withTimeout(12_000) { inbox.receive() }
        } catch (e: TimeoutCancellationException) {
            throw FnOsException("等待 NAS 响应超时", e)
        }
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(25_000)
                val ws = socket ?: return@launch
                if (!ws.send("""{"req":"ping"}""")) return@launch
            }
        }
    }

    private suspend fun verifyAccessCode() = withContext(Dispatchers.IO) {
        val code = Base64.encodeToString(access.accessCode.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val request = Request.Builder()
            .url(access.baseUrl + "/access_code_verify")
            .header("x-access-code", code)
            .header("x-access-source", "web")
            .get()
            .build()
        FnOsTransport.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 401 = 访问码本身不对（或已被管理员改掉），其余状态码是网关/网络问题
                throw FnOsException(
                    if (response.code == 401) "访问码不正确，请在设置里重新填写"
                    else "访问码校验失败（HTTP ${response.code}）",
                    httpStatus = response.code,
                )
            }
        }
    }

    // ------------------------------------------------------------------ 文案

    private fun loginErrorText(errno: Int, result: String): String = when (errno) {
        401 -> "会话被拒绝：可能需要访问码，或上一次登录状态没有重置"
        8192 -> "登录参数被拒绝（si 必须以字符串回传）"
        131072 -> "账号或密码错误"
        // 连续输错密码后 fnOS 会临时封禁来源 IP（Web 端提示「当前 IP 已被封禁」）
        131089 -> "登录失败次数过多，NAS 已临时封禁本机 IP，请稍后再试"
        135168 -> "动态验证码错误或已过期"
        else -> result.takeIf { it.isNotBlank() && it != "succ" }
            ?.let { "登录失败：$it" }
            ?: "登录失败（errno $errno）"
    }

    private fun describe(t: Throwable): String = when (t) {
        is FnOsException -> t.message ?: "连接失败"
        is UnknownHostException -> "找不到主机 ${access.host}"
        is SocketTimeoutException -> "连接超时，检查地址与端口"
        is ConnectException -> "无法连接 ${access.host}:${access.port}（确认是 HTTPS 端口）"
        is SSLException -> if (FnOsTransport.tofu.pinMismatch) {
            "NAS 证书指纹已变化，已清除旧指纹，请再试一次新证书"
        } else {
            "TLS 握手失败（${t.message ?: "自签证书"}）"
        }

        is java.io.IOException -> "网络错误：${t.message ?: t.javaClass.simpleName}"
        else -> t.message ?: t.javaClass.simpleName
    }

    private class ApiResult(val code: Int, val message: String, val data: JSONObject = JSONObject())
}

/**
 * 登录响应分类。fnOS 在密码正确但需要两步验证时，`errno=0`、`result=succ`，
 * 但 `token` 为空，只给一个临时 `accessToken` —— 旧代码直接把它当成
 * 「登录成功但没返回 AccessToken」，用户看到的就是这句没有任何指导意义的报错。
 */
internal sealed interface FnOsLoginReply {
    data class Success(val token: String) : FnOsLoginReply
    data class TwoFactor(val accessToken: String) : FnOsLoginReply
    /** 需要两步验证，但账号没绑定验证器（只有邮箱验证码）：电视端拿不到邮箱，只能让用户改绑 */
    data class SetupRequired(val emailOnly: Boolean = false) : FnOsLoginReply
    data class MissingToken(val fields: List<String>) : FnOsLoginReply
}

internal fun classifyLoginReply(reply: JSONObject): FnOsLoginReply {
    val token = reply.optString("token").trim()
    if (token.isNotBlank()) return FnOsLoginReply.Success(token)

    val bound = reply.flag("isBindTwofaSecret")
    val enforced = reply.flag("isTwofaEnforced")
    val accessToken = reply.optString("accessToken").trim()
    // secureEmail 非空 = 走邮箱验证码流程，电视端收不到邮件，只能引导用户改绑验证器
    val emailOnly = reply.optString("secureEmail").isNotBlank()
    return when {
        bound -> FnOsLoginReply.TwoFactor(accessToken)
        // 管理员强制要求、但账号还没绑定验证器：客户端没有可验证的秘密，只能在飞牛端先开启
        enforced -> FnOsLoginReply.SetupRequired(emailOnly = emailOnly)
        else -> FnOsLoginReply.MissingToken(
            reply.keys().asSequence()
                .filterNot { it == "reqid" || it == "errno" || it == "result" }
                .sorted()
                .toList(),
        )
    }
}

/**
 * 服务端的布尔字段并不总是 JSON 布尔：不同版本会回 true / "true" / 1，
 * 用 optBoolean 读数字会得到 false，直接把两步验证误判成普通失败。
 */
private fun JSONObject.flag(name: String): Boolean {
    val raw = opt(name) ?: return false
    return when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> raw.equals("true", ignoreCase = true) || raw == "1"
        else -> false
    }
}

/** 等待动态码的登录会话：临时凭证、设备标识与握手公钥都要原样带进验证请求 */
private class PendingTwoFactor(
    val challenge: FnOsTwoFactorChallenge,
    val accessToken: String,
    val did: String,
    val pub: String,
    val si: String,
    val inbox: Channel<JSONObject>,
)

/** fnOS 的 E_INVALID_TOKEN：两步验证里表示动态码错误 / 已过期 */
private const val ERRNO_INVALID_CREDENTIAL = 135168
