package com.nasphoto.tv.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设计画布归一化的核心计算。
 *
 * 真机反馈：4K/高密度电视上「图标超级大、一行只放两张卡、文字溢出」，
 * 根因是逻辑画布只有 480x270dp。这里把「任何物理分辨率都归一化到 960x540dp」
 * 的算法钉住。
 */
class TvDensityTest {

    @Test
    fun `1080p at 320dpi stays at design baseline`() {
        // 1920x1080 物理像素 @320dpi = 960x540dp，正好是设计基准，密度不变 (320/160=2)
        val scale = designDensityScale(1920f, 1080f, 960f, 540f, fallbackDensity = 2f)
        assertEquals(2f, scale, 0.001f)
    }

    @Test
    fun `high density tv is normalised back to baseline`() {
        // 1920x1080 @640dpi 原本只有 480x270dp（图标巨大）。归一化后密度应为 4（=1920/960? 否）
        // 960x540 设计 -> density = min(1920/960, 1080/540) = 2.0
        val scale = designDensityScale(1920f, 1080f, 960f, 540f, fallbackDensity = 4f)
        assertEquals(2f, scale, 0.001f)
        // 归一化后的逻辑宽度 = 物理宽 / 密度 = 960dp
        assertEquals(960f, 1920f / scale, 0.001f)
    }

    @Test
    fun `4k panel renders at design density`() {
        // 3840x2160 @ 320dpi：系统给 1920x1080dp，元素偏小；归一化到 density 4 -> 960x540dp
        val scale = designDensityScale(3840f, 2160f, 960f, 540f, fallbackDensity = 2f)
        assertEquals(4f, scale, 0.001f)
        assertEquals(960f, 3840f / scale, 0.001f)
        assertEquals(540f, 2160f / scale, 0.001f)
    }

    @Test
    fun `4 by 3 panel keeps design width and gains height`() {
        // 1024x768：min(1024/960, 768/540) = 1.0667 -> 宽度正好 960dp，高度 720dp
        val scale = designDensityScale(1024f, 768f, 960f, 540f, fallbackDensity = 1f)
        assertEquals(960f, 1024f / scale, 0.01f)
        assertEquals(720f, 768f / scale, 0.01f)
    }

    @Test
    fun `invalid size falls back to system density`() {
        assertEquals(2f, designDensityScale(0f, 0f, 960f, 540f, 2f), 0.001f)
        assertEquals(2f, designDensityScale(1920f, 1080f, 0f, 540f, 2f), 0.001f)
    }
}
