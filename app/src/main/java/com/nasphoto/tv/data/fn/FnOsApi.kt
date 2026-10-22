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
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
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
)

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

internal class FnOsException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
        withContext(Dispatchers.IO) {
            var current = ensureSession(force = false)
            var authRetried = false
            var signRetried = false
            while (true) {
                val result = execute(current, path, params)
                when {
                    result.code == 401 || result.code == 5001 -> {
                        if (authRetried) throw FnOsException("AccessToken 已失效，请重新登录")
                        authRetried = true
                        current = ensureSession(force = true)
                    }

                    result.code == 5000 -> {
                        if (signRetried) throw FnOsException("authx 签名被拒绝")
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
        scope.cancel()
    }

    // ------------------------------------------------------------------ 请求

    private fun execute(
        session: FnOsSession,
        path: String,
        params: List<Pair<String, String>>,
    ): ApiResult {
        // session 只用于保证调用前已登录；AccessToken 由 FnOsTransport 拦截器统一注入
        @Suppress("UNUSED_EXPRESSION")
        session
        val query = FnOsSigner.payload(params, SignMode.ENCODED)
        val url = access.baseUrl + path + if (query.isEmpty()) "" else "?$query"
        val request = Request.Builder()
            .url(url)
            .header("AccessToken", session.token)
            .header("authx", FnOsSigner.authHeader(path, params, signMode))
            .get()
            .build()
        FnOsTransport.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw FnOsException("NAS 返回 HTTP ${response.code}")
            val json = runCatching { JSONObject(body) }
                .getOrElse { throw FnOsException("NAS 返回的不是 JSON") }
            val code = if (json.has("code")) json.optInt("code") else json.optInt("errno", -1)
            if (code != 0) {
                return ApiResult(code, json.optString("msg").ifBlank { json.optString("result") })
            }
            return ApiResult(0, "", json.optJSONObject("data") ?: JSONObject())
        }
    }

    // ------------------------------------------------------------------ 登录

    private suspend fun ensureSession(force: Boolean): FnOsSession = loginMutex.withLock {
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

            val aesKey = FnOsCrypto.randomAscii(32)
            val iv = FnOsCrypto.randomBytes(16)
            val payload = JSONObject()
                .put("req", "user.login")
                .put("reqid", FnOsReqId.next())
                .put("si", si)
                .put("user", access.username)
                .put("password", access.password)
                .put("stay", true)
                .put("deviceType", "AndroidTV")
                .put("deviceName", "NAS Photo TV")
                .put("did", FnOsCrypto.randomHex(12))

            val envelope = JSONObject()
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

            val reply = request(inbox, envelope)
            val errno = reply.optInt("errno", 0)
            val result = reply.optString("result")
            if (errno != 0 || result != "succ") throw FnOsException(loginErrorText(errno, result))
            val token = reply.optString("token")
            if (token.isBlank()) throw FnOsException("登录成功但 NAS 没有返回 AccessToken")
            FnOsReqId.setBackId(reply.optString("backId"))
            return FnOsSession(token = token, secret = reply.optString("secret"))
        } catch (t: Throwable) {
            runCatching { ws.cancel() }
            if (socket === ws) socket = null
            throw t
        } finally {
            handshakeInbox = null
        }
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
                throw FnOsException("访问码校验失败（HTTP ${response.code}）")
            }
        }
    }

    // ------------------------------------------------------------------ 文案

    private fun loginErrorText(errno: Int, result: String): String = when (errno) {
        401 -> "会话被拒绝：可能需要访问码，或上一次登录状态没有重置"
        8192 -> "登录参数被拒绝（si 必须以字符串回传）"
        131072 -> "账号或密码错误"
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
