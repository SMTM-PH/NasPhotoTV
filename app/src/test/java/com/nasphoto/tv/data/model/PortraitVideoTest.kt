package com.nasphoto.tv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 竖屏视频识别。
 *
 * 判定依据是 NAS 返回的 width×height（fnOS 已经把旋转后的显示尺寸算进去了），
 * 不是容器里的 rotation 字段 —— 实测竖拍无人机素材 `rotation` 恒为 0，
 * 只有宽高比能区分方向。
 */
class PortraitVideoTest {

    private fun video(resolution: String) = MediaItem(
        id = "1",
        title = "clip",
        type = MediaType.VIDEO,
        url = "https://nas/p/api/v1/stream/v/1",
        resolution = resolution,
    )

    @Test
    fun dronePortrait4kIsPortrait() {
        // 实测样本 DJI_20260929142051_0237_D.MP4：2160×3840
        assertTrue(video("2160x3840").isPortraitVideo())
    }

    @Test
    fun phonePortrait1080pIsPortrait() {
        // 实测样本 IMG_0406.MOV：1080×1920
        assertTrue(video("1080x1920").isPortraitVideo())
    }

    @Test
    fun landscape4kIsNotPortrait() {
        assertFalse(video("3840x2160").isPortraitVideo())
    }

    @Test
    fun squareVideoIsNotPortrait() {
        assertFalse(video("1080x1080").isPortraitVideo())
    }

    @Test
    fun photoIsNeverPortraitVideo() {
        val photo = MediaItem(
            id = "2",
            title = "IMG_0001",
            type = MediaType.PHOTO,
            url = "https://nas/p/api/v1/stream/p/t/2/o/uuid",
            resolution = "1080x1920",
        )
        assertFalse(photo.isPortraitVideo())
    }

    @Test
    fun missingResolutionIsNotPortrait() {
        assertFalse(video("").isPortraitVideo())
        assertFalse(video("unknown").isPortraitVideo())
        assertFalse(video("0x0").isPortraitVideo())
    }

    @Test
    fun portraitFitDefaultsToAuto() {
        assertEquals(PortraitFit.AUTO, PlaybackSettings().portraitFit)
    }

    @Test
    fun portraitFitHasThreeDistinctLabels() {
        val labels = PortraitFit.entries.map { it.label }
        assertEquals(3, labels.size)
        assertEquals(labels.size, labels.toSet().size)
    }
}
