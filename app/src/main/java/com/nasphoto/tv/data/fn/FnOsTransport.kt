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

    /** ExoPlayer 数据源：http(s) 走带鉴权的 OkHttp，android.resource 等仍走默认源 */
    fun mediaDataSourceFactory(context: Context): DataSource.Factory =
        DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT),
        )

    private const val USER_AGENT = "NASPhotoTV/1.0 (Android TV)"
}

internal class InMemoryCookieJar : CookieJar {
    private val byHost = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        byHost[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = byHost[url.host].orEmpty()
}
