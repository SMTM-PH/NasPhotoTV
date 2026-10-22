package com.nasphoto.tv.data.fn

import android.content.Context
import android.content.SharedPreferences
import com.nasphoto.tv.data.AppContextHolder
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/** 按主机名保存的自签证书指纹（SHA-256） */
internal class FnOsPinStore private constructor(private val prefs: SharedPreferences?) {

    fun pinFor(host: String): String? = prefs?.getString(host, null)

    fun savePin(host: String, pin: String) {
        prefs?.edit()?.putString(host, pin)?.apply()
    }

    fun clearPin(host: String) {
        prefs?.edit()?.remove(host)?.apply()
    }

    companion object {
        fun fromAppContext(): FnOsPinStore {
            val ctx = AppContextHolder.context
            val prefs = ctx?.getSharedPreferences("fn_os_cert_pins", Context.MODE_PRIVATE)
            return FnOsPinStore(prefs)
        }
    }
}

/**
 * 飞牛是自签证书（O=fnOS / CN=fnOS，SAN 只有 DNS:fnOS），证书链校验必然失败。
 *
 * 这里做「首次使用信任」（TOFU）：不校验证书链，只记住首次连接看到的叶证书指纹；
 * 之后指纹变化会被拒绝一次并清除旧指纹，下一次连接重新信任 —— 飞牛证书约三个月
 * 续期一次，直接把用户锁死会很难恢复。
 */
internal class FnOsTofuTrust(private val pins: FnOsPinStore) : X509TrustManager, HostnameVerifier {

    @Volatile
    private var lastLeafPin: String? = null

    /** 最近一次是否因指纹变化而拒绝（供连接测试给出可读原因） */
    @Volatile
    var pinMismatch: Boolean = false
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("certificate chain is empty")
        lastLeafPin = FnOsCrypto.sha256(leaf.encoded)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    override fun verify(hostname: String, session: SSLSession?): Boolean {
        val pin = lastLeafPin ?: return false
        val host = hostname.substringBefore(':')
        val stored = pins.pinFor(host)
        return when {
            stored == null -> {
                pins.savePin(host, pin)
                true
            }

            stored.equals(pin, ignoreCase = true) -> true

            else -> {
                pinMismatch = true
                pins.clearPin(host)
                false
            }
        }
    }
}
