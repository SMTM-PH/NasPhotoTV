/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.video

import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 竖屏判定的兜底路径。
 *
 * 故障现场：`IMG_1217` 这类素材在 NAS 元数据里没有 width/height，
 * 只按 `MediaItem.resolution` 判断会漏掉整批竖屏视频 —— 它们仍然左右黑边，
 * 用户看到的依旧是「不支持竖屏」。解码器回报的真实尺寸是权威值。
 */
class PortraitPlaybackTest {

    private fun item(type: MediaType = MediaType.VIDEO, resolution: String = "") = MediaItem(
        id = "1",
        title = "clip",
        type = type,
        url = "https://nas/p/api/v1/stream/v/1",
        resolution = resolution,
    )

    @Test
    fun metadataWinsWhenPresent() {
        assertTrue(isPortraitPlayback(item(resolution = "1080x1920"), null))
        assertFalse(isPortraitPlayback(item(resolution = "3840x2160"), null))
    }

    @Test
    fun decodedSizeRescuesMissingMetadata() {
        // 元数据缺失 → 解码器说是竖屏 → 按竖屏处理
        assertTrue(isPortraitPlayback(item(resolution = ""), 1080 to 1920))
    }

    @Test
    fun decodedSizeWinsOverStaleMetadata() {
        // 解码器回报的是真正渲染出来的画面（已含旋转），比 NAS 元数据更权威。
        // 实测有素材元数据写 3840x2160、实际按竖屏渲染。
        assertTrue(isPortraitPlayback(item(resolution = "3840x2160"), 1080 to 1920))
        assertFalse(isPortraitPlayback(item(resolution = "1080x1920"), 3840 to 2160))
    }

    @Test
    fun decodedSquareIsNotPortrait() {
        assertFalse(isPortraitPlayback(item(), 1080 to 1080))
    }

    @Test
    fun noSignalAtAllDefaultsToLandscape() {
        // 宁可漏判，也不要把普通横屏视频错当成竖屏去做背景填充
        assertFalse(isPortraitPlayback(item(), null))
        assertFalse(isPortraitPlayback(null, 1080 to 1920))
        assertFalse(isPortraitPlayback(null, null))
    }

    @Test
    fun invalidDecodedSizeIsIgnored() {
        assertFalse(isPortraitPlayback(item(), 0 to 0))
        assertFalse(isPortraitPlayback(item(), -1 to -1))
    }
}
