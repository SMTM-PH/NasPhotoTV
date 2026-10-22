package com.nasphoto.tv.data.fn

import java.net.URLEncoder
import java.security.SecureRandom

/**
 * `/p` 业务接口的 `authx` 头。
 *
 * sign = md5(SALT_path_nonce_timestamp_md5(参数串)_SECRET)
 *  - path 必须带 `/p` 前缀；
 *  - timestamp 是**毫秒**；
 *  - GET 参数按 key 排序。
 *
 * 参数哈希有两种历史规则：RAW（解码后的值）与 ENCODED（URLSearchParams 形态的
 * 编码串）。两种都有服务端在跑，所以由 [FnOsApi] 在收到业务码 5000 时自动换规则重试。
 */
internal object FnOsSigner {

    private const val SALT = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
    private const val SECRET = "EAECCF25-80A6-4666-A7C2-A76904A74AB6"

    private val random = SecureRandom()

    fun authHeader(
        path: String,
        params: List<Pair<String, String>>,
        mode: SignMode,
    ): String {
        val nonce = (100_000 + random.nextInt(900_000)).toString()
        val timestamp = System.currentTimeMillis().toString()
        val payload = payload(params, mode)
        val sign = FnOsCrypto.md5(
            listOf(SALT, path, nonce, timestamp, FnOsCrypto.md5(payload), SECRET).joinToString("_"),
        )
        return "nonce=$nonce&timestamp=$timestamp&sign=$sign"
    }

    fun payload(params: List<Pair<String, String>>, mode: SignMode): String =
        params
            .sortedBy { it.first }
            .joinToString("&") { (key, value) ->
                when (mode) {
                    SignMode.RAW -> "$key=$value"
                    SignMode.ENCODED -> "${encode(key)}=${encode(value)}"
                }
            }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

internal enum class SignMode {
    RAW,
    ENCODED,
    ;

    fun next(): SignMode = if (this == RAW) ENCODED else RAW
}
