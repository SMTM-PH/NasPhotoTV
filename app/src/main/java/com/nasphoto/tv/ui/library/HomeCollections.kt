package com.nasphoto.tv.ui.library

import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.ui.LibraryState

/**
 * 首页「客厅相册」的一项。
 *
 * 智能分类可能包含几百上千张照片，首页只拿分类名、数量和封面（分类接口自带 poster），
 * 真正的媒体列表等用户点开分类或点「播放这组照片」时再按需拉取 ——
 * 80 个分类若在启动时各拉一次 magic-search，既拖慢首屏也会把低内存电视压垮。
 */
internal data class HomeCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    /** 只有回忆类集合才预置媒体；智能分类为空，播放时再加载。 */
    val items: List<MediaItem> = emptyList(),
    /** 非空表示这是飞牛 AI 场景/事物分类，值为分类 id。 */
    val smartAlbumId: String? = null,
    val coverUrl: String = "",
) {
    /** 封面占位媒体：分类没有封面 URL 时也能走统一的占位样式。 */
    val cover: MediaItem
        get() = items.firstOrNull() ?: MediaItem(
            id = "cover:$id",
            title = title,
            type = MediaType.PHOTO,
            url = coverUrl,
            thumbnailUrl = coverUrl,
        )
}

/**
 * 首页展示飞牛 AI 识别的事物 / 场景分类（ai-smart-rec）。
 *
 * 不再依赖「客厅展示相册」手动授权：文件夹相册仍可在「相册」Tab 浏览，
 * 首页回归用户想要的「AI 帮我分好类的相册」。
 */
internal fun homeCollections(state: LibraryState): List<HomeCollection> =
    state.smartAlbums.map { album ->
        HomeCollection(
            id = "smart:${album.id}",
            title = album.name,
            subtitle = if (album.itemCount > 0) {
                "${album.itemCount} 项 · AI 场景事物"
            } else {
                "AI 场景事物"
            },
            smartAlbumId = album.id,
            coverUrl = album.coverUrl,
        )
    }
