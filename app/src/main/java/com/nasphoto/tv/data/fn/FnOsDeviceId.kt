package com.nasphoto.tv.data.fn

import android.content.Context
import com.nasphoto.tv.data.AppContextHolder

/**
 * 本机的 fnOS 设备标识（登录请求里的 `did`）。
 *
 * 为什么必须持久化：fnOS 的两步验证支持「信任此设备」——服务端按 `did` 记住设备，
 * 之后同一 `did` 登录不再要动态码。之前每次登录都随机生成 `did`，等于每次都换新设备，
 * 即使用户输入了验证码、勾了信任，下次启动仍然会被要求验证。
 *
 * 12 字节随机数 = 24 位十六进制，与旧实现的长度一致，服务端接受。
 */
internal object FnOsDeviceId {

    private const val PREFS = "fnos_device"
    private const val KEY = "install_id"
    private const val LENGTH = 24

    @Volatile
    private var cached: String? = null

    fun get(): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = AppContextHolder.context
                ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = prefs?.getString(KEY, null)?.trim().orEmpty()
            val id = if (stored.length == LENGTH) {
                stored
            } else {
                FnOsCrypto.randomHex(LENGTH / 2).also { generated ->
                    prefs?.edit()?.putString(KEY, generated)?.apply()
                }
            }
            cached = id
            return id
        }
    }

    /** 仅供单元测试重置缓存 */
    internal fun resetForTest() {
        cached = null
    }
}
