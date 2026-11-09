package com.nasphoto.tv.ui

import com.nasphoto.tv.data.model.Person

/**
 * ================= 全局导航与页面状态（Lead 维护） =================
 *
 * 改这里的字段会影响多个页面，改动前先看清楚调用方（外壳 NasPhotoTvApp 与 LibraryScreen）。
 */
sealed interface AppRoute {
    data object Library : AppRoute
    data object Settings : AppRoute
    data object ScreensaverPreview : AppRoute
}

/**
 * 媒体库页面的状态（由外壳从飞牛相册数据源加载后传给 LibraryScreen）。
 *
 * [people] 是 NAS 人脸聚类出来的人物列表；NAS 没开 AI 相册时为空（UI 显示空状态）。
 * [peopleLoaded] 用来区分「还没加载完」和「加载完了但一个人都没有」，
 * 否则人脸接口慢的时候人物 Tab 会先闪一下「未开启人脸识别」。
 */
data class LibraryState(
    val recent: List<com.nasphoto.tv.data.model.MediaItem> = emptyList(),
    val albums: List<com.nasphoto.tv.data.model.Album> = emptyList(),
    val mediaByAlbum: Map<String, List<com.nasphoto.tv.data.model.MediaItem>> = emptyMap(),
    val selectedTabIndex: Int = 0,
    val connectionName: String = "Home-NAS",
    val isOnline: Boolean = false,
    /** 当前登录的飞牛账号显示名（顶栏账号胶囊） */
    val accountName: String = "",
    /** 这台 NAS 上保存了几个账号；>1 时顶栏胶囊显示切换提示 */
    val accountCount: Int = 1,
    val people: List<Person> = emptyList(),
    val peopleLoaded: Boolean = true,
    /** 飞牛 AI 识别的事物 / 场景分类，独立于用户创建的普通相册。 */
    val smartAlbums: List<com.nasphoto.tv.data.model.Album> = emptyList(),
    val smartAlbumsError: String? = null,
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
