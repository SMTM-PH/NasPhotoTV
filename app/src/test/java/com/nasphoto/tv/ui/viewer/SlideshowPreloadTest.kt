package com.nasphoto.tv.ui.viewer

import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.ui.common.effectiveImageWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.2.11：幻灯片换页前预加载下一张的纯逻辑回归。
 * 预加载尺寸必须和真正显示时一致，否则 Coil 内存缓存命中不了，白等一场。
 */
class SlideshowPreloadTest {

    private fun photo(id: String) = MediaItem(
        id = id, title = id, type = MediaType.PHOTO, url = "https://nas/$id.jpg",
    )

    private fun video(id: String) = MediaItem(
        id = id, title = id, type = MediaType.VIDEO, url = "https://nas/$id.mp4",
        thumbnailUrl = "https://nas/$id.jpg",
    )

    @Test
    fun effectiveWidthIsSharedByPreloadAndDisplay() {
        // 1080p 设计画布（density 2）：原样
        assertEquals(1280, effectiveImageWidth(1280, 2f))
        // 4K 面板（density 4）：等比放大但封顶 1920
        assertEquals(1920, effectiveImageWidth(1280, 4f))
        assertEquals(1920, effectiveImageWidth(1600, 4f))
        // 全屏 / 全景大图本来就 >=1920，不再按密度放大
        assertEquals(1920, effectiveImageWidth(1920, 4f))
        assertEquals(4096, effectiveImageWidth(4096, 4f))
        // 低密度设备不能把请求尺寸压得比目标还小
        assertEquals(640, effectiveImageWidth(640, 1f))
    }

    @Test
    fun peekNextPhotoSkipsVideosAndDoesNotMoveIndex() {
        val state = PhotoViewerState(
            items = listOf(photo("p0"), video("v1"), photo("p2")),
            initialIndex = 0,
        )
        assertEquals("p2", state.peekNextPhoto()?.id)
        assertEquals(0, state.index) // 只看不动
        state.advanceSlideshow()
        assertEquals(2, state.index)
    }

    @Test
    fun peekNextPhotoWrapsAroundAtTheEnd() {
        val state = PhotoViewerState(
            items = listOf(photo("p0"), video("v1")),
            initialIndex = 0,
        )
        // 到末尾循环回第一张照片，而不是停在视频封面上
        assertEquals("p0", state.peekNextPhoto()?.id)
        state.advanceSlideshow()
        assertEquals(0, state.index)
    }

    @Test
    fun allVideosHaveNoNextPhoto() {
        val state = PhotoViewerState(
            items = listOf(video("v0"), video("v1")),
            initialIndex = 0,
        )
        assertNull(state.peekNextPhoto())
        state.advanceSlideshow()
        assertEquals(0, state.index)
    }

    @Test
    fun emptyListIsSafe() {
        val state = PhotoViewerState(items = emptyList(), initialIndex = 0)
        assertNull(state.peekNextPhoto())
        state.advanceSlideshow()
        assertEquals(0, state.index)
    }
}
