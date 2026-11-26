/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 视频时长解析回归测试。
 *
 * 故障背景：`mediaDuration`（秒）没被读取，全库视频时长恒为 0，
 * 卡片角标消失、按码率规划缓冲也失效。
 */
class MediaDurationTest {

    @Test
    fun readsFnOsMediaDurationAsSeconds() {
        // 实测响应片段：IMG_0406.MOV 是 11 秒
        assertEquals(11_000L, MediaDuration.fromJson(JSONObject("""{"mediaDuration":11}""")))
    }

    @Test
    fun readsLongClipFromRealLibrary() {
        // 实测：DJI_20260929142051_0237_D.MP4 是 19 秒
        assertEquals(19_000L, MediaDuration.fromJson(JSONObject("""{"mediaDuration":19}""")))
    }

    @Test
    fun explicitMillisecondFieldWins() {
        assertEquals(
            5_000L,
            MediaDuration.fromJson(JSONObject("""{"durationMs":5000,"mediaDuration":99}""")),
        )
    }

    @Test
    fun plainDurationIsTreatedAsSeconds() {
        assertEquals(30_000L, MediaDuration.fromJson(JSONObject("""{"duration":30}""")))
    }

    @Test
    fun millisecondScaleValueIsNotMultipliedAgain() {
        // 超过 24 小时的值只可能是毫秒口径，不能再乘 1000
        assertEquals(90_061_000L, MediaDuration.fromJson(JSONObject("""{"duration":90061000}""")))
    }

    @Test
    fun missingOrInvalidFieldsYieldZero() {
        assertEquals(0L, MediaDuration.fromJson(JSONObject("{}")))
        assertEquals(0L, MediaDuration.fromJson(JSONObject("""{"mediaDuration":0}""")))
        assertEquals(0L, MediaDuration.fromJson(JSONObject("""{"mediaDuration":-3}""")))
        assertEquals(0L, MediaDuration.fromJson(JSONObject("""{"mediaDuration":null}""")))
        assertEquals(0L, MediaDuration.fromJson(JSONObject("""{"mediaDuration":"abc"}""")))
    }

    @Test
    fun zeroDurationMeansBitrateCannotBeEstimated() {
        // 下游契约：时长 0 时估算码率必须是 0，让缓冲规划走兜底而不是算出无穷大
        assertEquals(0L, com.nasphoto.tv.ui.video.PlaybackBufferPlanner.estimateBitrateBps(1_000_000L, 0L))
    }
}
