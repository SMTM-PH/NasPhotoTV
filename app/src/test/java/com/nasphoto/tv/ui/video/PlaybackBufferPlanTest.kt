package com.nasphoto.tv.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缓冲预算的回归测试。
 *
 * 这些断言全部来自真实故障现场（4K 60fps 竖拍原片在 1080p 电视上周期性卡顿）：
 * 旧实现把目标字节数写死成 48 MB，130 Mbps 的素材只能缓冲约 3 秒，
 * `shouldContinueLoading` 每 3 秒就撞一次字节上限，于是画面每隔几秒停一次。
 */
class PlaybackBufferPlanTest {

    /** 用户反馈的那类素材：4K 60fps HEVC 10bit 竖拍，实测 120 Mbps。 */
    private val portrait4k60 = 120_000_000L

    /** 常见手机 1080p 30fps 视频，约 8 Mbps。 */
    private val phone1080p = 8_000_000L

    @Test
    fun highBitrateKeepsMaxBufferWithinByteBudget() {
        val plan = PlaybackBufferPlanner.plan(portrait4k60)

        // 关键不变量：最长缓冲必须能在字节上限内装满，否则会周期性重新缓冲。
        val bytesForMax = portrait4k60 / 8.0 * (plan.maxBufferMs / 1000.0)
        assertTrue(
            "maxBufferMs=${plan.maxBufferMs} 需要 ${bytesForMax.toLong()} 字节，" +
                "超出预算 ${plan.targetBufferBytes}",
            bytesForMax <= plan.targetBufferBytes,
        )
    }

    @Test
    fun highBitrateStillBuffersAtLeastAFewSeconds() {
        val plan = PlaybackBufferPlanner.plan(portrait4k60)
        // 3 秒是旧实现的水平；必须明显更好，否则「卡顿」没修掉。
        assertTrue("maxBufferMs 太短：${plan.maxBufferMs}", plan.maxBufferMs >= 4_000)
        assertTrue("minBufferMs 太短：${plan.minBufferMs}", plan.minBufferMs >= 1_500)
    }

    @Test
    fun lowBitrateGetsLongerBufferThanHighBitrate() {
        val low = PlaybackBufferPlanner.plan(phone1080p)
        val high = PlaybackBufferPlanner.plan(portrait4k60)
        assertTrue(
            "低码率应拿到更长缓冲：low=${low.maxBufferMs} high=${high.maxBufferMs}",
            low.maxBufferMs > high.maxBufferMs,
        )
    }

    @Test
    fun lowBitrateBudgetCoversItsFullBufferWindow() {
        val plan = PlaybackBufferPlanner.plan(phone1080p)
        val bytesForMax = phone1080p / 8.0 * (plan.maxBufferMs / 1000.0)
        assertTrue(
            "低码率也不该撞字节上限：需要 ${bytesForMax.toLong()} > ${plan.targetBufferBytes}",
            bytesForMax <= plan.targetBufferBytes,
        )
    }

    @Test
    fun playbackThresholdsStayBelowMinBuffer() {
        // 否则会「攒满才播」，起播反而变慢 —— 这是 4K 素材上最容易被忽略的回归点。
        listOf(phone1080p, portrait4k60, 250_000_000L).forEach { bitrate ->
            val plan = PlaybackBufferPlanner.plan(bitrate)
            assertTrue(
                "bitrate=$bitrate: bufferForPlayback=${plan.bufferForPlaybackMs} >= min=${plan.minBufferMs}",
                plan.bufferForPlaybackMs < plan.minBufferMs,
            )
            assertTrue(
                "bitrate=$bitrate: afterRebuffer=${plan.bufferForPlaybackAfterRebufferMs} > min=${plan.minBufferMs}",
                plan.bufferForPlaybackAfterRebufferMs <= plan.minBufferMs,
            )
            assertTrue(
                "bitrate=$bitrate: max=${plan.maxBufferMs} <= min=${plan.minBufferMs}",
                plan.maxBufferMs > plan.minBufferMs,
            )
        }
    }

    @Test
    fun targetBytesStaysWithinSafeHeapRange() {
        // 电视 Java 堆实测 192 MB；缓冲不能把堆打满，否则 GC 抖动会表现成掉帧。
        listOf(1_000_000L, phone1080p, portrait4k60, 400_000_000L).forEach { bitrate ->
            val plan = PlaybackBufferPlanner.plan(bitrate)
            assertTrue(
                "bitrate=$bitrate: target=${plan.targetBufferBytes}",
                plan.targetBufferBytes in (16 * 1024 * 1024)..(96 * 1024 * 1024),
            )
        }
    }

    @Test
    fun unknownBitrateFallsBackToLegacyPlan() {
        assertEquals(PlaybackBufferPlanner.FALLBACK, PlaybackBufferPlanner.plan(0L))
        assertEquals(PlaybackBufferPlanner.FALLBACK, PlaybackBufferPlanner.plan(-1L))
    }

    @Test
    fun estimateBitrateHandlesMissingMetadata() {
        assertEquals(0L, PlaybackBufferPlanner.estimateBitrateBps(0L, 5_000L))
        assertEquals(0L, PlaybackBufferPlanner.estimateBitrateBps(1_000L, 0L))
        assertEquals(0L, PlaybackBufferPlanner.estimateBitrateBps(-5L, -5L))
    }

    @Test
    fun estimateBitrateMatchesRealPortraitClip() {
        // 实测样本：DJI 竖拍 4K60，306,502,785 字节 / 19 秒。
        val bps = PlaybackBufferPlanner.estimateBitrateBps(306_502_785L, 19_000L)
        assertTrue("估算 ${bps / 1_000_000} Mbps 不在 100–160 之间", bps in 100_000_000L..160_000_000L)
    }
}
