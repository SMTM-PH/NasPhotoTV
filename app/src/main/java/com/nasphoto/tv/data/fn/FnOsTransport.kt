package com.nasphoto.tv.data.fn

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

/**
 * 全局传输层：API、图片（Coil）与视频（ExoPlayer）共用同一套
 * 自签证书信任、cookie 与 AccessToken 注入。
 *
 * token 是动态读的 —— 重新登录后不需要重建任何客户端，
 * 正在排队的图片/视频请求会自动带上新 token。
 */
object FnOsTransport {

    @Volatile
    var token: String? = null

    internal val pinStore: FnOsPinStore by lazy { FnOsPinStore.fromAppContext() }
    internal val tofu: FnOsTofuTrust by lazy { FnOsTofuTrust(pinStore) }
    private val cookieJar = InMemoryCookieJar()

    val client: OkHttpClient by lazy {
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(tofu), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, tofu)
            .hostnameVerifier(tofu)
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val current = token
                val original = chain.request()
                // 已显式带 token 的请求（API 会话）不覆盖；图片/视频走这里注入当前 token
                val request = if (current.isNullOrBlank() || original.header("AccessToken") != null) {
                    original
                } else {
                    original.newBuilder().header("AccessToken", current).build()
                }
                chain.proceed(request)
            }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 视频响应会持续读取，缓冲足够时还会暂停读取，不能套用 API 的 30 秒整次请求超时。
     * 派生客户端保留连接/读取超时、鉴权、证书信任与连接池，只取消完整响应的总时限。
     */
    internal val mediaClient: OkHttpClient by lazy {
        client.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    /** ExoPlayer 数据源：http(s) 走带鉴权的流媒体客户端，android.resource 等仍走默认源 */
    fun mediaDataSourceFactory(context: Context): DataSource.Factory =
        DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(mediaClient).setUserAgent(USER_AGENT),
        )

    private const val USER_AGENT = "NASPhotoTV/1.0 (Android TV)"
}

/**
 * 进程内 cookie 表，按 host 分桶、按 cookie 名合并。
 *
 * 为什么不能整桶替换：开启「访问码」的 NAS 会在 `/access_code_verify` 下发
 * `os-access-code`（HttpOnly，网关级凭证）。它只在那一次响应里出现，之后的
 * 每个请求都靠它才被放行。如果后续任何一次响应带了别的 Set-Cookie 就把整张表
 * 覆盖掉，访问码凭证会静默丢失 —— 表现是媒体库突然全部 401，重启才好。
 * 所以这里按名字合并，只让同名 cookie 被更新。
 */
internal class InMemoryCookieJar : CookieJar {
    private val byHost = ConcurrentHashMap<String, Map<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        byHost.compute(url.host) { _, existing ->
            val merged = LinkedHashMap(existing.orEmpty())
            cookies.forEach { merged[it.name] = it }
            merged
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val stored = byHost[url.host]?.values?.toList().orEmpty()
        val usable = stored.filter { it.expiresAt > now && it.matches(url) }
        // 过期项顺手清掉：电视常年不关机，别让死 cookie 一直堆在表里
        if (usable.size != stored.size) {
            byHost.computeIfPresent(url.host) { _, existing ->
                existing.filterValues { it.expiresAt > now }
            }
        }
        return usable
    }

    /** 仅供测试：清空 */
    internal fun clearForTest() {
        byHost.clear()
    }
}
