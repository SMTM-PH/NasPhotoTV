package com.nasphoto.tv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.Person

/**
 * 最近一次从 NAS 拉取到的媒体快照。
 *
 * 供设置页（屏保预览 / 播放摘要 / 关于）读取；媒体库与屏保本身由外壳
 * 直接持有 [com.nasphoto.tv.ui.LibraryState]，不依赖这里。
 *
 * 多账号：切换账号时必须 [clear]（外壳已经这么做了）。否则上一个账号的照片会
 * 短暂显示在另一个账号的界面里 —— 那是真实的隐私问题，不只是显示 bug。
 */
object MediaCache {
    var recent: List<MediaItem> by mutableStateOf(emptyList())
        private set
    var albums: List<Album> by mutableStateOf(emptyList())
        private set
    var screensaverPhotos: List<MediaItem> by mutableStateOf(emptyList())
        private set
    var people: List<Person> by mutableStateOf(emptyList())
        private set
    var photoCount: Int by mutableStateOf(0)
        private set
    var videoCount: Int by mutableStateOf(0)
        private set

    /** 当前缓存属于哪个账号（切换账号后用它判断是否需要重新拉取） */
    var ownerAccountId: String by mutableStateOf("")
        private set
    /** Full connection identity, including identity changes within the same local account id. */
    var ownerIdentityKey: String by mutableStateOf("")
        private set

    fun update(
        recent: List<MediaItem>,
        albums: List<Album>,
        screensaverPhotos: List<MediaItem>,
        photoCount: Int,
        videoCount: Int,
        people: List<Person> = this.people,
        ownerAccountId: String = this.ownerAccountId,
        ownerIdentityKey: String = "",
    ) {
        this.recent = recent
        this.albums = albums
        this.screensaverPhotos = screensaverPhotos
        this.photoCount = photoCount
        this.videoCount = videoCount
        this.people = people
        this.ownerAccountId = ownerAccountId
        this.ownerIdentityKey = ownerIdentityKey
    }

    fun clear() {
        recent = emptyList()
        albums = emptyList()
        screensaverPhotos = emptyList()
        people = emptyList()
        photoCount = 0
        videoCount = 0
        ownerAccountId = ""
        ownerIdentityKey = ""
    }
}
