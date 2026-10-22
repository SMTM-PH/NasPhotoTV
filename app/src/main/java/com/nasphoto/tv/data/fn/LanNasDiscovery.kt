package com.nasphoto.tv.data.fn

import android.content.Context
import android.net.ConnectivityManager
import com.nasphoto.tv.data.AppContextHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** 局域网里扫描到的候选 NAS */
data class LanNasDevice(
    val host: String,
    val port: Int = LanNasDiscovery.DEFAULT_PORT,
    /** 证书 CN/O 带 fnOS 字样：基本可以确定是飞牛 */
    val fnOsCertificate: Boolean = false,
)

/**
 * 首次连接用的局域网发现：探测本机所在 /24 网段的 HTTPS 端口，
 * 用 TLS 证书里的 fnOS 标识区分飞牛设备（兼容自签证书）。
 * 只读取证书，不发送账号、密码或 AccessToken。
 */
object LanNasDiscovery {

    const val DEFAULT_PORT = 5667

    private const val CONNECT_TIMEOUT_MS = 350
    private const val HANDSHAKE_TIMEOUT_MS = 900
    private const val PARALLEL_PROBES = 64
    private val LAST_OCTET_RANGE = 1..254

    suspend fun discover(): List<LanNasDevice> = withContext(Dispatchers.IO) {
        val self = localIpv4() ?: return@withContext emptyList()
        val base = self.hostAddress?.substringBeforeLast('.') ?: return@withContext emptyList()
        val own = self.hostAddress
        val candidates = LAST_OCTET_RANGE
            .map { "$base.$it" }
            .filter { it != own }

        val found = mutableListOf<LanNasDevice>()
        for (chunk in candidates.chunked(PARALLEL_PROBES)) {
            coroutineScope {
                chunk.map { host -> async { probe(host) } }.awaitAll()
            }.filterNotNullTo(found)
        }
        found.sortedWith(
            compareByDescending<LanNasDevice> { it.fnOsCertificate }
                .thenBy { ipSortKey(it.host) },
        )
    }

    private fun localIpv4(): Inet4Address? {
        val context = AppContextHolder.context ?: return null
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = connectivity.activeNetwork ?: return null
        val link = connectivity.getLinkProperties(network) ?: return null
        return link.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
    }

    private fun probe(host: String): LanNasDevice? = try {
        Socket().use { raw ->
            raw.connect(InetSocketAddress(host, DEFAULT_PORT), CONNECT_TIMEOUT_MS)
            val tls = tlsFactory.createSocket(raw, host, DEFAULT_PORT, true) as SSLSocket
            tls.use { socket ->
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                socket.startHandshake()
                val leaf = socket.session.peerCertificates.firstOrNull() as? X509Certificate
                val subject = leaf?.subjectX500Principal?.name.orEmpty()
                LanNasDevice(
                    host = host,
                    fnOsCertificate = subject.contains("fnOS", ignoreCase = true),
                )
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }

    private fun ipSortKey(host: String): Long = host.split('.').fold(0L) { acc, octet ->
        (acc shl 8) or (octet.toLongOrNull() ?: 0)
    }

    // 只用于确认「5667 后面是不是 TLS 服务、证书写的是不是 fnOS」；这个探测不发送任何数据。
    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val tlsFactory by lazy {
        SSLContext.getInstance("TLS")
            .apply { init(null, arrayOf(TrustAll), SecureRandom()) }
            .socketFactory
    }
}
