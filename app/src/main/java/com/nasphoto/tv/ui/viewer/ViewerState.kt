package com.nasphoto.tv.ui.viewer

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nasphoto.tv.data.FavoritesStore
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType

/**
 * ================= 全屏图片浏览器的 UI 状态（viewer-dev 维护） =================
 *
 * 所有遥控器动作都收敛到这里，保证「KEY 事件」与「按钮点击」走同一条路径。
 * 焦点本身不放在这里：焦点交给 Compose 的焦点系统（DPAD 在按钮之间自由移动）。
 */
@Stable
class PhotoViewerState(
    val items: List<MediaItem>,
    initialIndex: Int,
    /** 最近一次切换的方向：1=下一张，-1=上一张，0=直接跳转。用于决定滑动动画方向。 */
    initialDirection: Int = 0,
) {
    /** 当前索引（已 clamp 在 0..size-1） */
    var index by mutableStateOf(initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        private set

    var direction by mutableStateOf(initialDirection)
        private set

    /** 控制栏（底部信息条 + 按钮行）是否显示 */
    var chromeVisible by mutableStateOf(true)

    /** 信息面板展开（标题/日期/分辨率/大小/扩展名） */
    var infoExpanded by mutableStateOf(false)

    /** 幻灯片自动播放 */
    var slideshowPlaying by mutableStateOf(false)
        private set

    /** 放大查看：全屏适应 <-> 2× 放大 */
    var zoomed by mutableStateOf(false)

    /** 放大后的平移偏移，单位为「相对一屏的比例」，范围 [-0.5, 0.5] */
    var panX by mutableStateOf(0f)
        private set
    var panY by mutableStateOf(0f)
        private set

    /** 全景横向偏移：0 = 最左，1 = 最右；非全景照片恒为 0 */
    var panoramaOffset by mutableStateOf(0f)
        private set

    val current: MediaItem? get() = items.getOrNull(index)
    val hasPrev: Boolean get() = index > 0
    val hasNext: Boolean get() = index < items.lastIndex
    val counterText: String get() = "${index + 1} / ${items.size}"

    /**
     * 收藏 = NAS 的 isCollect ∪ 本机星标。
     * fnOS 没有收藏写接口，本地部分由 [FavoritesStore] 持久化，退出/重启都不丢。
     */
    fun isStarred(item: MediaItem?): Boolean =
        item != null && (item.starred || FavoritesStore.isFavorite(item.id))

    /** 收藏总数（浮层展示），随本地切换实时变化 */
    val starredCount: Int
        get() = items.count { isStarred(it) }

    // ---------------- 切换 ----------------

    fun go(delta: Int) {
        val n = items.size
        if (n <= 0) return
        val next = index + delta
        // 到边界时循环（幻灯片无缝滚动、手动切换也不「卡住」）
        index = ((next % n) + n) % n
        direction = if (delta >= 0) 1 else -1
        resetPan()
    }

    fun goTo(target: Int, animateDirectional: Boolean = true) {
        val n = items.size
        if (n <= 0) return
        val clamped = target.coerceIn(0, n - 1)
        if (clamped == index) return
        direction = if (animateDirectional) (if (clamped > index) 1 else -1) else 0
        index = clamped
        resetPan()
    }

    // ---------------- 控制栏 ----------------

    fun showChrome() {
        chromeVisible = true
    }

    fun toggleChrome() {
        chromeVisible = !chromeVisible
    }

    /** 3 秒无操作后隐藏控制栏 */
    fun hideChromeIfIdle() {
        chromeVisible = false
    }

    fun toggleInfo() {
        infoExpanded = !infoExpanded
        chromeVisible = true
    }

    fun toggleStar() {
        current?.let { FavoritesStore.toggle(it.id) }
    }

    fun toggleZoom() {
        zoomed = !zoomed
        if (!zoomed) resetPan()
    }

    // ---------------- 幻灯片 ----------------

    fun toggleSlideshow() {
        slideshowPlaying = !slideshowPlaying
        chromeVisible = true
    }

    fun pauseSlideshow(): Boolean {
        if (!slideshowPlaying) return false
        slideshowPlaying = false
        return true
    }

    /** 幻灯片 tick：自动下一张 */
    fun advanceSlideshow() = go(1)

    // ---------------- 放大平移 ----------------

    /** 方向键在放大模式下平移，步进 0.5 倍（0.5 表示半屏），返回是否消费 */
    fun panBy(dx: Float, dy: Float): Boolean {
        if (!zoomed) return false
        panX = (panX + dx).coerceIn(-0.5f, 0.5f)
        panY = (panY + dy).coerceIn(-0.5f, 0.5f)
        return true
    }

    fun resetPan() {
        panX = 0f
        panY = 0f
        panoramaOffset = 0f
    }

    /**
     * 全景平移：dx 是一屏的比例（0.33 = 一次一格）。
     * 返回 false 表示已经贴边，调用方可以顺势切到上一张/下一张，不把用户卡住。
     */
    fun panPanorama(dx: Float): Boolean {
        val next = (panoramaOffset + dx).coerceIn(0f, 1f)
        if (next == panoramaOffset) return false
        panoramaOffset = next
        return true
    }

    companion object {
        fun saver(): Saver<PhotoViewerState, Any> = Saver(
            save = { listOf(it.index, it.zoomed, it.infoExpanded, it.slideshowPlaying) },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val v = saved as List<Any>
                PhotoViewerState(emptyList(), 0).apply {
                    index = (v[0] as? Int) ?: 0
                    zoomed = (v[1] as? Boolean) ?: false
                    infoExpanded = (v[2] as? Boolean) ?: false
                    slideshowPlaying = (v[3] as? Boolean) ?: false
                }
            },
        )
    }
}

/**
 * remember 一个 PhotoViewerState。
 * 注意：配置变更/进程恢复时 items 由外部重新传入，这里用 saver 只恢复轻量 UI 状态。
 */
@Composable
fun rememberPhotoViewerState(
    items: List<MediaItem>,
    initialIndex: Int,
): PhotoViewerState = rememberSaveable(
    saver = PhotoViewerState.saver(),
    key = "viewerState",
) {
    PhotoViewerState(items = items, initialIndex = initialIndex)
}.also { state ->
    // items 变化时（例如 Lead 换了数据源）保持索引合法
    if (items.isNotEmpty() && state.index > items.lastIndex) {
        state.goTo(items.lastIndex)
    }
}

/** 文件大小格式化：1.2 MB / 856 KB ... */
@Composable
fun rememberFileSizeText(bytes: Long): String {
    val context = LocalContext.current
    return if (bytes <= 0L) "—" else Formatter.formatShortFileSize(context, bytes)
}

/** 视频时长 mm:ss */
fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/** 图片是否为视频 */
fun MediaItem.isVideo(): Boolean = type == MediaType.VIDEO
