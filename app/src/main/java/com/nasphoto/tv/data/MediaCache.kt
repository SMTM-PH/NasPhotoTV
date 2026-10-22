package com.nasphoto.tv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.MediaItem

/**
 * 最近一次从 NAS 拉取到的媒体快照。
 *
 * 供设置页（屏保预览 / 播放摘要 / 关于）读取；媒体库与屏保本身由外壳
 * 直接持有 [com.nasphoto.tv.ui.LibraryState]，不依赖这里。
 */
object MediaCache {
    var recent: List<MediaItem> by mutableStateOf(emptyList())
        private set
    var albums: List<Album> by mutableStateOf(emptyList())
        private set
    var screensaverPhotos: List<MediaItem> by mutableStateOf(emptyList())
        private set
    var photoCount: Int by mutableStateOf(0)
        private set
    var videoCount: Int by mutableStateOf(0)
        private set

    fun update(
        recent: List<MediaItem>,
        albums: List<Album>,
        screensaverPhotos: List<MediaItem>,
        photoCount: Int,
        videoCount: Int,
    ) {
        this.recent = recent
        this.albums = albums
        this.screensaverPhotos = screensaverPhotos
        this.photoCount = photoCount
        this.videoCount = videoCount
    }

    fun clear() {
        recent = emptyList()
        albums = emptyList()
        screensaverPhotos = emptyList()
        photoCount = 0
        videoCount = 0
    }
}
