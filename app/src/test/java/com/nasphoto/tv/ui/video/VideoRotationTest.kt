package com.nasphoto.tv.ui.video

import com.nasphoto.tv.data.model.VideoRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.2.11：视频方向与手动旋转的纯计算回归。 */
class VideoRotationTest {

    @Test
    fun unappliedRotationIsNormalizedToFourSteps() {
        assertEquals(0, normalizeUnappliedRotation(0))
        assertEquals(90, normalizeUnappliedRotation(90))
        assertEquals(180, normalizeUnappliedRotation(180))
        assertEquals(270, normalizeUnappliedRotation(270))
        assertEquals(270, normalizeUnappliedRotation(-90))
        assertEquals(0, normalizeUnappliedRotation(360))
        assertEquals(90, normalizeUnappliedRotation(450))
        // 脏元数据不能把画面转歪
        assertEquals(0, normalizeUnappliedRotation(45))
        assertEquals(0, normalizeUnappliedRotation(Int.MIN_VALUE))
    }

    @Test
    fun quarterTurnSwapsCodedDimensionsForPortraitDetection() {
        assertEquals(1920 to 1080, displayVideoSize(1920, 1080, 0))
        assertEquals(1080 to 1920, displayVideoSize(1920, 1080, 90))
        assertEquals(1920 to 1080, displayVideoSize(1920, 1080, 180))
        assertEquals(1080 to 1920, displayVideoSize(1920, 1080, 270))
    }

    @Test
    fun invalidDimensionsGiveNoSignal() {
        assertNull(displayVideoSize(0, 1080, 90))
        assertNull(displayVideoSize(1920, -1, 0))
    }

    @Test
    fun rotationFitScaleKeepsRotatedFrameFullyOnScreen() {
        // 16:9 屏转 90° 后，画面缩到 9/16 才能完整放进屏幕
        assertEquals(1f, rotationFitScale(1920f, 1080f, 0), 1e-6f)
        assertEquals(0.5625f, rotationFitScale(1920f, 1080f, 90), 1e-6f)
        assertEquals(0.5625f, rotationFitScale(1920f, 1080f, 270), 1e-6f)
        assertEquals(1f, rotationFitScale(1920f, 1080f, 180), 1e-6f)
        // 竖屏面板 / 非法尺寸都不能算出 0 或负数（会把画面压没）
        assertEquals(0.5625f, rotationFitScale(1080f, 1920f, 90), 1e-6f)
        assertEquals(1f, rotationFitScale(0f, 1080f, 90), 1e-6f)
    }

    @Test
    fun manualRotationCyclesAndWraps() {
        assertEquals(VideoRotation.CW_90, VideoRotation.NONE.next())
        assertEquals(VideoRotation.CW_180, VideoRotation.CW_90.next())
        assertEquals(VideoRotation.CW_270, VideoRotation.CW_180.next())
        assertEquals(VideoRotation.NONE, VideoRotation.CW_270.next())
        assertEquals(0, VideoRotation.NONE.degrees)
        assertEquals(270, VideoRotation.CW_270.degrees)
    }

    @Test
    fun fromDegreesNormalizesAnyAngle() {
        assertEquals(VideoRotation.NONE, VideoRotation.fromDegrees(0))
        assertEquals(VideoRotation.CW_90, VideoRotation.fromDegrees(450))
        assertEquals(VideoRotation.CW_270, VideoRotation.fromDegrees(-90))
        assertEquals(VideoRotation.NONE, VideoRotation.fromDegrees(45))
    }
}
