package com.nasphoto.tv.ui.video

import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解码能力预检与诊断。
 *
 * 背景：飞牛相册 API 只返回原片、没有转码流，所以失败时要靠设备能力给出
 * 针对性原因。这里验证「分辨率硬超」「4K 10bit 高风险」两条判定的边界。
 */
class VideoCapabilityProbeTest {

    private fun item(resolution: String) = MediaItem(
        id = "1",
        title = "clip",
        type = MediaType.VIDEO,
        url = "https://nas/p/api/v1/stream/v/1",
        resolution = resolution,
    )

    private fun device(
        hevc10: Boolean = true,
        hevcSupported: Boolean = true,
        avcSupported: Boolean = true,
        maxPixels: Long = 8_294_400L, // 3840×2160
    ) = VideoCapabilityProbe.DeviceProfile(
        hevc = VideoCapabilityProbe.CodecProfile("hevc", hevcSupported, hevc10, maxPixels),
        avc = VideoCapabilityProbe.CodecProfile("avc", avcSupported, false, maxPixels),
        av1 = VideoCapabilityProbe.CodecProfile("av1", false, false, 0),
        vp9 = VideoCapabilityProbe.CodecProfile("vp9", true, false, maxPixels),
    )

    @Test
    fun normal1080pIsNeverRisky() {
        val a = VideoCapabilityProbe.assess(item("1920x1080"), device())
        assertFalse(a.risky)
        assertNull(a.reason)
    }

    @Test
    fun resolutionBeyondEveryDecoderIsRisky() {
        // 8K 竖屏原片（4320×7680 ≈ 33M）远超设备 4K 上限
        val a = VideoCapabilityProbe.assess(item("4320x7680"), device(maxPixels = 8_294_400L))
        assertTrue(a.risky)
        assertTrue(a.reason!!.contains("超过"))
    }

    @Test
    fun fourKWithoutHevc10BitIsRisky() {
        // 4K 竖屏（2160×3840）+ 设备不支持 10bit HEVC → 高风险（最常见的失败场景）
        val a = VideoCapabilityProbe.assess(
            item("2160x3840"),
            device(hevc10 = false, maxPixels = 33_177_600L), // 分辨率够大，但不支持 10bit
        )
        assertTrue(a.risky)
        assertTrue(a.reason!!.contains("10bit"))
    }

    @Test
    fun fourKWithHevc10BitSupportIsNotRisky() {
        val a = VideoCapabilityProbe.assess(
            item("3840x2160"),
            device(hevc10 = true),
        )
        assertFalse(a.risky)
    }

    @Test
    fun noDecoderAtAllIsRiskyFor4K() {
        val a = VideoCapabilityProbe.assess(
            item("3840x2160"),
            device(hevcSupported = false, avcSupported = false, maxPixels = 8_294_400L),
        )
        assertTrue(a.risky)
        assertTrue(a.reason!!.contains("没有可用"))
    }

    @Test
    fun unknownResolutionIsNeverRisky() {
        // 元数据缺失宽高时不猜，交给 ExoPlayer 的真实结果
        assertFalse(VideoCapabilityProbe.assess(item(""), device()).risky)
    }

    @Test
    fun decodeErrorUsesAssessmentReason() {
        val assessment = VideoCapabilityProbe.Assessment(true, "针对性诊断：这台电视不支持 10bit")
        val result = VideoCapabilityProbe.describeFailure(2010, "通用解码失败", assessment)
        assertEquals("针对性诊断：这台电视不支持 10bit", result)
    }

    @Test
    fun networkErrorIgnoresAssessmentAndUsesFallback() {
        val assessment = VideoCapabilityProbe.Assessment(true, "针对性诊断")
        val result = VideoCapabilityProbe.describeFailure(2005, "网络连接失败", assessment)
        assertEquals("网络连接失败", result)
    }

    @Test
    fun decodeErrorWithoutAssessmentUsesFallback() {
        val result = VideoCapabilityProbe.describeFailure(
            2001,
            "通用解码失败",
            VideoCapabilityProbe.Assessment(false, null),
        )
        assertEquals("通用解码失败", result)
    }

    @Test
    fun maxResolutionLabelIsReadable() {
        val label = VideoCapabilityProbe.formatMaxResolution(8_294_400L)
        assertTrue(label.contains("×"))
        assertTrue(label.startsWith("38") || label.startsWith("39"))
    }
}
