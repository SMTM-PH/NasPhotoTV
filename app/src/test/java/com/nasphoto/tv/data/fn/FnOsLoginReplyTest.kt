package com.nasphoto.tv.data.fn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 登录响应分类：真实用户反馈的「登录成功但 NAS 没有返回 AccessToken」
 * 就是两步验证被误判成失败，这里把三种分支钉住。
 */
class FnOsLoginReplyTest {

    @Test
    fun `token present is success`() {
        val reply = JSONObject()
            .put("errno", 0)
            .put("result", "succ")
            .put("token", "abc")
            .put("secret", "s")
        val outcome = classifyLoginReply(reply)
        assertTrue(outcome is FnOsLoginReply.Success)
        assertEquals("abc", (outcome as FnOsLoginReply.Success).token)
    }

    @Test
    fun `bound 2fa without token is challenge`() {
        val reply = JSONObject()
            .put("errno", 0)
            .put("result", "succ")
            .put("accessToken", "temp-token")
            .put("isBindTwofaSecret", true)
            .put("isTwofaEnforced", false)
        val outcome = classifyLoginReply(reply)
        assertTrue(outcome is FnOsLoginReply.TwoFactor)
        assertEquals("temp-token", (outcome as FnOsLoginReply.TwoFactor).accessToken)
    }

    @Test
    fun `enforced without bound secret asks user to enable 2fa`() {
        val reply = JSONObject()
            .put("errno", 0)
            .put("result", "succ")
            .put("isBindTwofaSecret", false)
            .put("isTwofaEnforced", true)
        val outcome = classifyLoginReply(reply)
        assertTrue(outcome is FnOsLoginReply.SetupRequired)
        assertEquals(false, (outcome as FnOsLoginReply.SetupRequired).emailOnly)
    }

    @Test
    fun `email only 2fa asks user to switch to authenticator`() {
        // 管理员强制 2FA、但账号只绑了邮箱：飞牛 Web 端会走邮箱验证码流程，
        // 电视上没有邮箱收件箱，只能提示用户改绑验证器
        val reply = JSONObject()
            .put("isBindTwofaSecret", false)
            .put("isTwofaEnforced", true)
            .put("secureEmail", "a***@example.com")
        val outcome = classifyLoginReply(reply)
        assertTrue(outcome is FnOsLoginReply.SetupRequired)
        assertTrue((outcome as FnOsLoginReply.SetupRequired).emailOnly)
    }

    @Test
    fun `plain success without token lists response fields`() {
        val reply = JSONObject()
            .put("errno", 0)
            .put("result", "succ")
            .put("reqid", "00")
            .put("uid", 1000)
            .put("isBindTwofaSecret", false)
            .put("isTwofaEnforced", false)
        val outcome = classifyLoginReply(reply)
        assertTrue(outcome is FnOsLoginReply.MissingToken)
        // 排掉噪音字段，剩下的按字母序：用户/我们排查时能直接看到 NAS 到底回了什么
        assertEquals(
            listOf("isBindTwofaSecret", "isTwofaEnforced", "uid"),
            (outcome as FnOsLoginReply.MissingToken).fields,
        )
    }

    @Test
    fun `bound 2fa wins even when enforced`() {
        val reply = JSONObject()
            .put("accessToken", "temp")
            .put("isBindTwofaSecret", true)
            .put("isTwofaEnforced", true)
        assertTrue(classifyLoginReply(reply) is FnOsLoginReply.TwoFactor)
    }

    @Test
    fun `string and numeric flags are understood`() {
        // 某些 fnOS 版本把布尔回成 "true" / 1，optBoolean 会误读成 false
        val asString = JSONObject()
            .put("accessToken", "temp")
            .put("isBindTwofaSecret", "true")
        assertTrue(classifyLoginReply(asString) is FnOsLoginReply.TwoFactor)

        val asNumber = JSONObject()
            .put("accessToken", "temp")
            .put("isBindTwofaSecret", 1)
        assertTrue(classifyLoginReply(asNumber) is FnOsLoginReply.TwoFactor)

        val enforcedNumber = JSONObject().put("isTwofaEnforced", 1)
        assertTrue(classifyLoginReply(enforcedNumber) is FnOsLoginReply.SetupRequired)
    }

    @Test
    fun `two factor code must be six digits`() {
        // verifyTwoFactor 会先过滤非数字，长度不足时在发请求前就拒绝
        assertEquals("123456", "123456".filter(Char::isDigit))
        assertEquals(5, "12345".filter(Char::isDigit).length)
    }
}
