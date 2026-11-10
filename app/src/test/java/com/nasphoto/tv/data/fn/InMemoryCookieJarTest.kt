package com.nasphoto.tv.data.fn

import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 访问码网关的 cookie 必须能跨请求存活。
 *
 * 真实故障场景：开启访问码后，`/access_code_verify` 下发 `os-access-code`，
 * 之后的 WebSocket / API / 图片请求全靠它。旧实现按 host 整桶替换 cookie，
 * 只要后续任意一个响应带了别的 Set-Cookie，访问码凭证就被覆盖，
 * 表现为媒体库突然全部 401、重启 App 才好。
 */
class InMemoryCookieJarTest {

    private val url: HttpUrl = "https://192.168.31.184:5667/websocket?type=main".toHttpUrl()
    private val otherUrl: HttpUrl = "https://192.168.31.184:5667/p/api/v1/user_photo/stat".toHttpUrl()

    private fun cookie(name: String, value: String, url: HttpUrl = this.url): Cookie =
        Cookie.Builder()
            .name(name)
            .value(value)
            .domain(url.host)
            .secure()
            .path("/")
            .build()

    @Test
    fun `access code cookie survives an unrelated set-cookie`() {
        val jar = InMemoryCookieJar()
        jar.saveFromResponse(url, listOf(cookie("os-access-code", "v1|abc")))
        // 后续某个响应只带自己的 cookie，不能把访问码挤掉
        jar.saveFromResponse(otherUrl, listOf(cookie("trim-session", "sess")))

        val sent = jar.loadForRequest(url).associateBy { it.name }
        assertEquals("v1|abc", sent["os-access-code"]?.value)
        assertEquals("sess", sent["trim-session"]?.value)
    }

    @Test
    fun `same name is updated in place`() {
        val jar = InMemoryCookieJar()
        jar.saveFromResponse(url, listOf(cookie("os-access-code", "old")))
        jar.saveFromResponse(url, listOf(cookie("os-access-code", "new")))
        assertEquals(listOf("new"), jar.loadForRequest(url).map { it.value })
    }

    @Test
    fun `expired cookie is not sent`() {
        val jar = InMemoryCookieJar()
        val expired = Cookie.Builder()
            .name("os-access-code")
            .value("dead")
            .domain(url.host)
            .secure()
            .path("/")
            .expiresAt(System.currentTimeMillis() - 60_000)
            .build()
        jar.saveFromResponse(url, listOf(expired))
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `cookies are scoped per host`() {
        val jar = InMemoryCookieJar()
        jar.saveFromResponse(url, listOf(cookie("os-access-code", "a")))
        val otherHost = "https://10.0.0.5:5667/websocket?type=main".toHttpUrl()
        assertTrue(jar.loadForRequest(otherHost).isEmpty())
    }
}
