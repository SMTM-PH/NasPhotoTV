package com.nasphoto.tv.ui.video

import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播放器只接受视频。
 *
 * 故障现场：调用方传进来的是「当天/当相册」的混排列表（图片 + 视频），
 * 自动连播的下一条很可能是图片。实测把一张 PNG 截图丢给 ExoPlayer 后，
 * 进度恒为 `0:00 / 0:00` 并永久黑屏 —— 用户看到的是「播完这个视频就卡死」。
 */
class VideoPlaylistTest {

    private fun photo(id: String) = MediaItem(
        id = id,
        title = "IMG_$id",
        type = MediaType.PHOTO,
        url = "https://nas/p/api/v1/stream/p/t/$id/o/uuid",
    )

    private fun video(id: String) = MediaItem(
        id = id,
        title = "MOV_$id",
        type = MediaType.VIDEO,
        url = "https://nas/p/api/v1/stream/v/$id",
    )

    private fun startIndex(items: List<MediaItem>, initialIndex: Int): Int {
        val videos = items.filter { it.type == MediaType.VIDEO }
        if (videos.isEmpty()) return -1
        val target = items.getOrNull(initialIndex)
        val mapped = target?.let { t -> videos.indexOfFirst { it.id == t.id } } ?: -1
        return (if (mapped >= 0) mapped else initialIndex).coerceIn(0, videos.lastIndex)
    }

    @Test
    fun mixedListKeepsOnlyVideos() {
        val list = listOf(photo("1"), video("2"), photo("3"), video("4"), photo("5"))
        assertEquals(listOf("2", "4"), list.filter { it.type == MediaType.VIDEO }.map { it.id })
    }

    @Test
    fun startIndexMapsFromOriginalPosition() {
        // 原列表第 3 项是视频 "4"，过滤后它在下标 1
        val list = listOf(photo("1"), video("2"), photo("3"), video("4"), photo("5"))
        assertEquals(1, startIndex(list, 3))
    }

    @Test
    fun startIndexOnFirstVideo() {
        val list = listOf(video("2"), photo("3"), video("4"))
        assertEquals(0, startIndex(list, 0))
    }

    @Test
    fun startIndexFallsBackWhenTargetIsPhoto() {
        // 目标本身是图片时（理论上不该发生），退到原位再夹取，不会崩
        val list = listOf(video("1"), photo("2"), video("3"))
        val idx = startIndex(list, 1)
        assertEquals(true, idx in list.indices)
    }

    @Test
    fun allPhotosMeansNothingToPlay() {
        assertEquals(-1, startIndex(listOf(photo("1"), photo("2")), 0))
    }

    @Test
    fun emptyListMeansNothingToPlay() {
        assertEquals(-1, startIndex(emptyList(), 0))
    }

    @Test
    fun outOfRangeInitialIndexIsClamped() {
        val list = listOf(photo("1"), video("2"))
        assertEquals(0, startIndex(list, 99))
    }
}
