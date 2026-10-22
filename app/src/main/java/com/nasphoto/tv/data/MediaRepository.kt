package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.NasConnection

/**
 * ================= NAS 媒体仓库抽象接口 =================
 *
 * 当前实现：[FnOsMediaRepository]（飞牛相册私有接口 + 鉴权图片/视频流）。
 * UI 始终只依赖本接口，替换数据源不需要改页面。
 *
 * 所有方法均为 suspend，真实实现应放到 Dispatchers.IO。
 */
interface MediaRepository {

    /** 连接信息 */
    val connection: NasConnection

    /** 健康检查：NAS 是否可达（设置页「测试连接」使用） */
    suspend fun ping(): Boolean

    /** 首页「最近添加」：按拍摄时间倒序 */
    suspend fun getRecent(limit: Int = 20): List<MediaItem>

    /** 所有相册（文件夹）+ 智能相册 */
    suspend fun getAlbums(): List<Album>

    /** 某个相册下的媒体，按拍摄时间倒序 */
    suspend fun getMedia(albumId: String): List<MediaItem>

    /** 搜索媒体（按标题/日期） */
    suspend fun search(query: String): List<MediaItem>

    /** 屏保用：可轮播的照片池（默认只取图片） */
    suspend fun getScreensaverPhotos(): List<MediaItem> = getRecent(50).filter { it.type == com.nasphoto.tv.data.model.MediaType.PHOTO }

    /** 屏保用：人物照片池（fnOS 人脸聚类）；AI 未开启或没有人物时返回空列表 */
    suspend fun getPeoplePhotos(limit: Int = 120): List<MediaItem> = emptyList()

    /** 获取单张媒体详情（可能补充 EXIF 等信息） */
    suspend fun getMediaItem(id: String): MediaItem? = null
}
