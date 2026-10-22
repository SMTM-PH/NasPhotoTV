package com.nasphoto.tv.ui

/**
 * ================= 全局导航与页面状态（Lead 维护） =================
 */
sealed interface AppRoute {
    data object Library : AppRoute
    data object Settings : AppRoute
    data object ScreensaverPreview : AppRoute
}

/**
 * 媒体库页面的状态（由外壳从飞牛相册数据源加载后传给 LibraryScreen）。
 */
data class LibraryState(
    val recent: List<com.nasphoto.tv.data.model.MediaItem> = emptyList(),
    val albums: List<com.nasphoto.tv.data.model.Album> = emptyList(),
    val mediaByAlbum: Map<String, List<com.nasphoto.tv.data.model.MediaItem>> = emptyMap(),
    val selectedTabIndex: Int = 0,
    val connectionName: String = "Home-NAS",
    val isOnline: Boolean = false,
)

/**
 * 图片浏览器的入口参数。
 */
data class ViewerRequest(
    val items: List<com.nasphoto.tv.data.model.MediaItem>,
    val initialIndex: Int,
    val contextTitle: String,
)

/**
 * 视频播放器的入口参数。
 */
data class PlayerRequest(
    val items: List<com.nasphoto.tv.data.model.MediaItem>,
    val initialIndex: Int,
    val contextTitle: String,
)
