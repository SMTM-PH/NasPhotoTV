package com.nasphoto.tv.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.2.9：第二行卡片贴屏幕下沿时，必须真的把内容上移，不能再是「滚到第 0 项」的空操作。 */
class GridScrollTest {

    @Test
    fun secondRowClippedAtBottomScrollsUpByTheOverflow() {
        // 1080p 设计画布下，第二行卡片顶部约 600px、高 300px，底部提示占掉 112px
        val delta = gridScrollDeltaPx(top = 600, height = 300, usableHeight = 856, canScrollBack = false)
        assertTrue("第二行被裁时必须有正向滚动，旧实现这里是 0", delta > 0f)
        assertEquals(300 + 36 + 600 - 856, delta.toInt())
    }

    @Test
    fun fullyVisibleCardDoesNotMoveTheGrid() {
        assertEquals(
            0f,
            gridScrollDeltaPx(top = 100, height = 300, usableHeight = 856, canScrollBack = false),
        )
    }

    @Test
    fun firstRowAtTopDoesNotScrollBackward() {
        assertEquals(
            0f,
            gridScrollDeltaPx(top = 8, height = 300, usableHeight = 856, canScrollBack = false),
        )
    }

    @Test
    fun itemClippedAtTopScrollsBackwardWhenPossible() {
        val delta = gridScrollDeltaPx(top = -40, height = 300, usableHeight = 856, canScrollBack = true)
        assertTrue(delta < 0f)
    }

    @Test
    fun marginIsAtLeastSixteenPixelsForSmallCards() {
        val delta = gridScrollDeltaPx(top = 700, height = 100, usableHeight = 810, canScrollBack = false)
        assertEquals(700 + 100 + 16 - 810, delta.toInt())
    }
}
