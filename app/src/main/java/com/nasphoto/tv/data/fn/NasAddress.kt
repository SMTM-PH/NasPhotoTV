package com.nasphoto.tv.data.fn

import java.util.Locale

/**
 * 一次地址解析的结果。
 *
 * 地址栏既接受「192.168.1.8」这种纯主机，也接受完整 URL：
 * `https://nas.example.com:5667/fnos`。解析后统一拆成
 * scheme / host / port / basePath 四段，传输层再拼回实际请求地址。
 */
data class ParsedNasAddress(
    val scheme: String,
    val host: String,
    /** 地址文本里显式写出的端口；null = 用配置里的端口 */
    val port: Int?,
    /** 反代子路径，如 "/fnos"；根路径为空串 */
    val basePath: String,
    val valid: Boolean,
    val error: String? = null,
)

/**
 * 飞牛地址解析。只认 http / https，其他 scheme 直接判非法，避免用户把
 * `ftp://`、`wss://` 之类写进去后报一堆看不懂的 DNS 错误。
 *
 * 注意：这里的 host 永远**不含** scheme、端口和路径；端口与子路径单独存。
 */
object NasAddressParser {

    fun parse(
        raw: String,
        defaultScheme: String = "https",
        defaultPort: Int? = null,
        defaultBasePath: String = "",
    ): ParsedNasAddress {
        var text = raw.trim()
        if (text.isEmpty()) {
            return ParsedNasAddress(defaultScheme, "", defaultPort, normalizeBasePath(defaultBasePath), true)
        }

        var scheme = defaultScheme.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") scheme = "https"

        // 协议前缀 / 协议相对地址（//host/path）
        val sep = text.indexOf("://")
        if (sep >= 0) {
            val candidate = text.substring(0, sep).lowercase(Locale.ROOT)
            if (candidate != "http" && candidate != "https") {
                return invalid("仅支持 http / https 地址")
            }
            scheme = candidate
            text = text.substring(sep + 3)
        } else if (text.startsWith("//")) {
            text = text.removePrefix("//")
        }

        // userinfo 不能进 host（拼 URL 时会破坏 authority）
        val at = text.lastIndexOf('@')
        if (at >= 0) text = text.substring(at + 1)

        val cut = text.indexOfAny(charArrayOf('/', '?', '#'))
        val authority = if (cut >= 0) text.substring(0, cut) else text
        val tail = if (cut >= 0) text.substring(cut) else ""
        val pathOnly = tail.substringBefore('?').substringBefore('#')

        var host = authority
        var port = defaultPort
        if (authority.startsWith("[")) {
            // IPv6 字面量：[::1] 或 [::1]:5667
            val close = authority.indexOf(']')
            if (close <= 0) return invalid("IPv6 地址缺少右方括号")
            host = authority.substring(0, close + 1)
            val rest = authority.substring(close + 1)
            if (rest.startsWith(":")) {
                val value = rest.substring(1).toIntOrNull()
                    ?: return invalid("端口不是数字")
                if (value !in 1..65535) return invalid("端口超出 1–65535")
                port = value
            }
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon >= 0) {
                val maybePort = authority.substring(colon + 1)
                // 只有":"后面全是数字才当端口；否则按裸 IPv6 / 非法字符处理
                if (maybePort.isNotEmpty() && maybePort.all { it.isDigit() }) {
                    val value = maybePort.toInt()
                    if (value !in 1..65535) return invalid("端口超出 1–65535")
                    host = authority.substring(0, colon)
                    port = value
                }
            }
        }

        if (host.isBlank()) return invalid("缺少主机名")
        if (host.any { it.isWhitespace() }) return invalid("地址不能包含空格")

        return ParsedNasAddress(
            scheme = scheme,
            host = host,
            port = port,
            basePath = if (pathOnly.isBlank()) normalizeBasePath(defaultBasePath) else normalizeBasePath(pathOnly),
            valid = true,
        )
    }

    /** "/fnos/" → "/fnos"，"fnos" → "/fnos"，"/" → "" */
    fun normalizeBasePath(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "/") return ""
        val withSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return withSlash.trimEnd('/')
    }

    private fun invalid(message: String) = ParsedNasAddress(
        scheme = "https",
        host = "",
        port = null,
        basePath = "",
        valid = false,
        error = message,
    )
}
