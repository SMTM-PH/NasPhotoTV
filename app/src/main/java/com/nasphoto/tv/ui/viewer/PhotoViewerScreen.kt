package com.nasphoto.tv.ui.viewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.isPanoramaPhoto
import com.nasphoto.tv.data.model.pixelSize
import com.nasphoto.tv.ui.common.LivePhotoVideo
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.theme.NasPhotoTvTheme
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.delay
import java.time.LocalDate

/** 控制栏无操作自动隐藏时长 */
private const val ChromeAutoHideMs = 3000L

/** 幻灯片每张停留时长 */
private const val SlideshowIntervalMs = 5000L

/** 放大平移的步进（0.5 倍：一次方向键平移半张图） */
private const val PanStep = 0.5f

/** 放大查看的固定倍率：2× 时画面正好外扩一屏，±0.5 的平移量不会露出黑边 */
private const val ZoomFactor = 2f

/** 全屏舞台按 1080p 电视请求解码尺寸（竖屏素材以高度为准，Coil 会保比例降采样） */
private const val FullScreenTargetWidth = 1920

/** 全景平移步长：一次方向键挪三分之一屏 */
private const val PanoramaStep = 0.33f

/** 全景横向细节多，按 4K 宽度请求解码 */
private const val PanoramaTargetWidth = 4096

/** 图片区背景：上深下略亮的竖向渐变，保证图片区本身不会是一块「纯黑无内容」的舞台 */
private val StageBackground = Brush.verticalGradient(
    listOf(TvColor.Bg, Color(0xFF0E1016), TvColor.Bg),
)

/** 控制栏浮层的 Scrim：图片铺满整屏时，保证信息条 / 按钮 / 胶片条上的文字依然清晰可读 */
private val ChromeScrim = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.28f to Color.Black.copy(alpha = 0.55f),
    0.62f to Color.Black.copy(alpha = 0.88f),
    1.0f to TvColor.Bg,
)

/**
 * ================= 全屏图片浏览器（原 viewer-dev，现由 viewer-fixer 维护） =================
 *
 * 遥控器交互总览（图片全屏优先，信息都是瞬态浮层）：
 * ┌ 图片区（默认焦点在这里，整屏即画框）
 * │   LEFT / RIGHT 或 PREV/NEXT    上一张 / 下一张（带方向滑动动画，首尾循环）
 * │   ENTER                        视频 -> onOpenVideo；图片 -> 切换浮层显隐
 * │   UP                           展开 / 收起详细信息（尺寸、文件、ID）
 * │   DPAD_DOWN                    进入工具按钮行（同时弹出控制栏）
 * │   放大模式下 LEFT RIGHT UP DOWN 以 0.5 倍步进平移画面
 * ├ 按钮行（焦点在按钮之间自由移动）
 * │   LEFT RIGHT 工具之间移动；UP 回到图片区；ENTER 触发；BACK 关闭
 * └ 全局
 *      BACK                       关闭浏览器
 *      PLAY_PAUSE                 幻灯片播放/暂停
 *      任意方向键                  立刻暂停幻灯片
 *      任意按键                    重新显示控制栏（且同一个 key 事件继续执行原动作，不被吞掉）
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalAnimationApi::class)
@Composable
fun PhotoViewerScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    contextTitle: String,
    onClose: () -> Unit,
    onOpenVideo: (MediaItem, List<MediaItem>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    // ---- 空列表直接退出（不能崩） ----
    LaunchedEffect(items.isEmpty()) {
        if (items.isEmpty()) onClose()
    }
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize().background(TvColor.Bg))
        return
    }

    val state = rememberPhotoViewerState(items = items, initialIndex = initialIndex)
    val filmState = rememberLazyListState()
    val imageFocus = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }

    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenVideo by rememberUpdatedState(onOpenVideo)

    /**
     * 控制栏里的按钮 / 胶片条是否持有焦点：此时左右键交给 Compose 焦点系统处理，
     * 且 3 秒自动隐藏暂停（别把用户正在操作的按钮藏掉）。舞台自身不算。
     * 用布尔而非计数：onFocusChanged 首次回调会带一个初始 false，计数会减成负数。
     */
    var toolbarHasFocus by remember { mutableStateOf(false) }
    var filmHasFocus by remember { mutableStateOf(false) }
    val inToolbar = toolbarHasFocus || filmHasFocus

    // ---- 进入页面：焦点落到图片区 ----
    LaunchedEffect(Unit) {
        runCatching { imageFocus.requestFocus() }
    }

    // ---- 翻页后把焦点留在图片区 ----
    // AnimatedContent 换页会销毁旧舞台节点：若焦点正好在旧节点上，Compose 会把它丢给
    // 控制栏第一个按钮，之后左右键变成在按钮之间移动，看图就「卡住」了。
    // 工具栏主动翻页时（焦点在按钮行）不抢，避免用户按完「下一张」焦点又跳回画面。
    LaunchedEffect(state.index) {
        if (inToolbar) return@LaunchedEffect
        repeat(2) { withFrameMillis {} }
        runCatching { imageFocus.requestFocus() }
    }

    // ---- 下键 / TAB / MENU 进工具行 ----
    // 控制栏刚弹出的那几帧里 AnimatedVisibility 还没组合出按钮节点，等两帧再抢焦点，
    // 否则 requestFocus 必然抛异常（这正是「按下键进不去工具行」的原因）。
    var toolbarEnter by remember { mutableIntStateOf(0) }
    LaunchedEffect(toolbarEnter) {
        if (toolbarEnter == 0) return@LaunchedEffect
        repeat(2) { withFrameMillis {} }
        runCatching { tabFocus.requestFocus() }
    }

    // ---- 控制栏 3 秒自动隐藏 ----
    LaunchedEffect(state.chromeVisible, state.index, state.infoExpanded, state.slideshowPlaying, state.zoomed, inToolbar) {
        if (!state.chromeVisible) return@LaunchedEffect
        delay(ChromeAutoHideMs)
        // 焦点在按钮行上时不算「无操作」，不隐藏（否则用户看不到自己的焦点在哪）
        if (!inToolbar && !state.infoExpanded && !state.slideshowPlaying) {
            state.hideChromeIfIdle()
        }
    }

    // ---- 幻灯片自动播放：每 5 秒下一张，到末尾循环 ----
    LaunchedEffect(state.slideshowPlaying) {
        if (!state.slideshowPlaying) return@LaunchedEffect
        while (true) {
            delay(SlideshowIntervalMs)
            state.advanceSlideshow()
        }
    }

    // ---- 全局按键（预览阶段：任何按键先显示控制栏，再执行原动作） ----
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvColor.Bg)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // 1) 任意按键都重新显示控制栏 —— 但不吞事件
                if (!state.chromeVisible) state.showChrome()

                when (event.key) {
                    Key.Back, Key.Escape -> {
                        currentOnClose()
                        true
                    }

                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        state.toggleSlideshow()
                        true
                    }

                    else -> false
                }
            },
    ) {
        // ================= 全屏舞台：整屏都是画框，信息只以浮层出现 =================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(StageBackground),
            contentAlignment = Alignment.Center,
        ) {
            // 切换动画只负责「换页」：
            // - targetState 改成「当前项」这个对象本身，动画闭包里拿到的永远是要显示的这一张，
            //   不会再出现「闭包按旧 index 取 item、而 state.index 已经变了」的错位；
            // - 内容里不再自己叠加 graphicsLayer 的 alpha / 缩放动画，
            //   避免出现过一次 appear 值停在 0 就永远黑屏的舞台。
            AnimatedContent(
                targetState = state.current,
                // 固定成屏幕大小：AnimatedContent 默认按内容测量，全景舞台比屏幕宽时
                // 会把整个 Stage 撑大，舞台内的坐标随之偏移。
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    // 按切换方向从左右滑入滑出 + 淡入
                    val d = if (state.direction >= 0) 1 else -1
                    val spec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)
                    (slideInHorizontally(spec) { w -> d * w / 3 } + fadeIn(tween(220)))
                        .togetherWith(
                            slideOutHorizontally(spec) { w -> -d * w / 3 } + fadeOut(tween(160)),
                        )
                },
                contentAlignment = Alignment.Center,
                label = "viewerSlide",
            ) { item ->
                if (item == null) return@AnimatedContent
                StageContent(
                    item = item,
                    active = item == state.current,
                    state = state,
                    imageFocus = imageFocus,
                    onOpenVideo = { currentOnOpenVideo(item, items) },
                    onToggleChrome = { state.toggleChrome() },
                    onPauseSlideshow = { state.pauseSlideshow() },
                    onNext = { state.go(1) },
                    onPrev = { state.go(-1) },
                    onEnterToolbar = {
                        state.showChrome()
                        toolbarEnter += 1
                    },
                    onToggleInfo = { state.toggleInfo() },
                    onFocusChanged = { },
                    inToolbar = { inToolbar },
                )
            }

            // ================= 左上浮层：LIVE 徽标常驻，其余信息随控制栏出现 =================
            ViewerHud(
                state = state,
                contextTitle = contextTitle,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = TvDimen.ScreenPadding, top = TvDimen.SpaceL),
            )

            // ================= 右上：幻灯片状态 =================
            SlideshowIndicator(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = TvDimen.ScreenPadding, top = TvDimen.SpaceM),
            )
        }

        // ================= 底部浮层（按钮行 + 胶片条） =================
        // 浮层是瞬时的（3 秒自动隐藏 / 回车切换），全屏图片只在需要操作时被暂时盖住。
        AnimatedVisibilityCompat(
            visible = state.chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(ChromeScrim)
                    .padding(top = TvDimen.SpaceL),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TvDimen.ScreenPadding)
                        .onFocusChanged { toolbarHasFocus = it.hasFocus },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerToolBar(
                        state = state,
                        onPrev = { state.go(-1) },
                        onNext = { state.go(1) },
                        onToggleInfo = { state.toggleInfo() },
                        onToggleStar = { state.toggleStar() },
                        onToggleSlideshow = { state.toggleSlideshow() },
                        onToggleZoom = { state.toggleZoom() },
                        onClose = { currentOnClose() },
                        tabFocus = tabFocus,
                    )
                }
                Spacer(Modifier.height(TvDimen.SpaceM))
                FilmStrip(
                    state = state,
                    listState = filmState,
                    onSelect = { state.goTo(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .onFocusChanged { filmHasFocus = it.hasFocus },
                )
                Spacer(Modifier.height(TvDimen.SpaceM))
            }
        }

        // ================= 左缘返回（参考图 3 的圆形箭头） =================
        // 遥控器 BACK 仍走全局按键；这个圆形按钮服务于鼠标 / 触摸与"看得见"的退出口。
        // 跟着控制栏一起显隐，全屏看图时不留常驻元素。
        AnimatedVisibilityCompat(
            visible = state.chromeVisible,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            BackChevron(
                onClose = { currentOnClose() },
                modifier = Modifier.padding(start = 18.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 舞台内容：图片（全屏 Fit / 2× 放大平移）或视频封面
// ---------------------------------------------------------------------------

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StageContent(
    item: MediaItem,
    active: Boolean,
    state: PhotoViewerState,
    imageFocus: FocusRequester,
    onOpenVideo: () -> Unit,
    onToggleChrome: () -> Unit,
    onPauseSlideshow: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onEnterToolbar: () -> Unit,
    onToggleInfo: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    inToolbar: () -> Boolean,
) {
    val zoomed = active && state.zoomed
    val stage = Modifier
        .fillMaxSize()
        .then(if (active) Modifier.focusRequester(imageFocus) else Modifier)
        .onFocusChanged { onFocusChanged(it.isFocused) }
        .focusable()
        .onPreviewKeyEvent { event ->
            if (!active || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Tab, Key.Menu,
                -> Unit
                else -> return@onPreviewKeyEvent false
            }
            // 焦点在按钮行时，方向键交给 Compose 焦点系统（让按钮之间能走）
            if (inToolbar() && event.key != Key.DirectionCenter &&
                event.key != Key.Enter && event.key != Key.NumPadEnter &&
                event.key != Key.Tab && event.key != Key.Menu
            ) {
                return@onPreviewKeyEvent false
            }

            // 任意方向键：暂停幻灯片（同一按键继续执行切换动作）
            if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                event.key == Key.DirectionUp || event.key == Key.DirectionDown
            ) {
                onPauseSlideshow()
            }

            if (item.isVideo()) {
                // 视频：ENTER 打开播放器；左右仍可切到上一张/下一张（不能被视频卡住）。
                // 必须 return：否则同一个按键会继续落到下面的图片分支，一次 LEFT 跳两张。
                return@onPreviewKeyEvent when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onOpenVideo()
                        true
                    }

                    Key.DirectionLeft -> {
                        onPrev()
                        true
                    }

                    Key.DirectionRight -> {
                        onNext()
                        true
                    }

                    Key.DirectionDown -> {
                        onEnterToolbar()
                        true
                    }

                    Key.DirectionUp -> true
                    else -> false
                }
            }

            // 全景：左右键在画面内平移，贴边后再切上一张/下一张（不把用户卡住）
            val panoramic = item.isPanoramaPhoto()
            when (event.key) {
                Key.DirectionLeft -> {
                    when {
                        panoramic -> if (!state.panPanorama(-PanoramaStep)) onPrev()
                        zoomed -> state.panBy(-PanStep, 0f)
                        else -> onPrev()
                    }
                    true
                }

                Key.DirectionRight -> {
                    when {
                        panoramic -> if (!state.panPanorama(PanoramaStep)) onNext()
                        zoomed -> state.panBy(PanStep, 0f)
                        else -> onNext()
                    }
                    true
                }

                Key.DirectionUp -> {
                    if (zoomed && !panoramic) state.panBy(0f, -PanStep) else onToggleInfo()
                    true
                }

                Key.DirectionDown -> {
                    if (zoomed && !panoramic) {
                        state.panBy(0f, PanStep)
                        true
                    } else {
                        onEnterToolbar()
                        true
                    }
                }

                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    onToggleChrome()
                    true
                }

                Key.Tab, Key.Menu -> {
                    onEnterToolbar()
                    true
                }

                else -> false
            }
        }

    Box(stage, contentAlignment = Alignment.Center) {
        when {
            item.isVideo() -> VideoStage(item = item)

            item.isLive && item.liveUrl.isNotBlank() -> LivePhotoStage(
                item = item,
                active = active,
                zoomed = zoomed,
                panX = state.panX,
                panY = state.panY,
            )

            item.isPanoramaPhoto() -> PanoramaStage(
                item = item,
                offset = state.panoramaOffset,
            )

            else -> PhotoStage(item = item, zoomed = zoomed, panX = state.panX, panY = state.panY)
        }
    }
}

/**
 * 实况照片：静图铺底 + 动态片段静音循环自动播放。
 * 两者共用同一个全屏 Fit 画框，尺寸天然对齐；放大到 2× 时暂停片段、只留静图（平移查看仍有效）。
 */
@Composable
private fun LivePhotoStage(
    item: MediaItem,
    active: Boolean,
    zoomed: Boolean,
    panX: Float,
    panY: Float,
) {
    Box(Modifier.fillMaxSize()) {
        PhotoStage(item = item, zoomed = zoomed, panX = panX, panY = panY)
        if (!zoomed) {
            LivePhotoVideo(
                url = item.liveUrl,
                active = active,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * 图片舞台：整屏即画框。
 *
 * MediaImage 内部是 `Box(modifier) { Image(Modifier.fillMaxSize()) }`，尺寸完全由调用方给出 ——
 * 不给确定尺寸时内部 Image 会被量成 0x0（整片黑）。所以这里直接 `fillMaxSize()` + Fit：
 * 图片按屏幕比例放大到最大，竖构图也不会再被副框二次缩小。
 *
 * 「放大查看」用 graphicsLayer 做：倍率 2 时画面正好向外扩出一整屏，
 * panX/panY ∈ [-0.5, 0.5] 映射成 ±0.5 屏平移，拖到底也刚好不会露出黑边。
 */
@Composable
private fun PhotoStage(item: MediaItem, zoomed: Boolean, panX: Float, panY: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MediaImage(
            url = item.url,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = if (zoomed) ZoomFactor else 1f
                    scaleX = s
                    scaleY = s
                    translationX = panX * size.width
                    translationY = panY * size.height
                },
            contentScale = ContentScale.Fit,
            targetWidth = FullScreenTargetWidth,
        )
        if (zoomed) {
            ZoomHint(
                panX = panX,
                panY = panY,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = TvDimen.SpaceS),
            )
        }
    }
}

/**
 * 全景舞台：按高度铺满、按原始宽高比展开，左右键在画面内横向平移。
 * 360° 等距柱状图按平面展开浏览（端侧没有 GL 球面渲染，先把「能看完」做对）。
 * 普通照片不会走到这里：宽高比 < 1.9 的都在 [PhotoStage]。
 */
@Composable
private fun PanoramaStage(item: MediaItem, offset: Float) {
    val size = item.pixelSize()
    if (size == null) {
        PhotoStage(item = item, zoomed = false, panX = 0f, panY = 0f)
        return
    }
    val aspect = size.first.toFloat() / size.second
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val imageWidth = maxHeight * aspect
        if (imageWidth <= maxWidth) {
            PhotoStage(item = item, zoomed = false, panX = 0f, panY = 0f)
        } else {
            val travel = imageWidth - maxWidth
            // 用 Layout 显式摆放：Box 对齐对「比父级宽」的子项会把它居中，
            // 平移到底时右侧会露出半幅溢出量。这里自己算 x，边缘才对得齐。
            Layout(
                content = {
                    MediaImage(
                        url = item.url,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                        targetWidth = PanoramaTargetWidth,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val placeable = measurables.first().measure(
                    Constraints.fixed(imageWidth.roundToPx(), constraints.maxHeight),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((-travel * offset).roundToPx(), 0)
                }
            }
        }
    }
}

@Composable
private fun VideoStage(item: MediaItem) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MediaImage(
            url = item.thumbnailUrl,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            targetWidth = FullScreenTargetWidth,
        )
        // 播放遮罩
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(112.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(56.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放视频",
                    tint = TvColor.TextPrimary,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.height(TvDimen.SpaceM))
            Text(
                text = "按「回车」播放视频",
                color = TvColor.TextPrimary,
                fontSize = TvType.Body,
                fontWeight = FontWeight.Medium,
            )
            val dur = formatDuration(item.durationMs)
            if (dur.isNotEmpty()) {
                Spacer(Modifier.height(TvDimen.SpaceXs))
                Text(text = dur, color = TvColor.TextSecondary, fontSize = TvType.Caption)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 小部件
// ---------------------------------------------------------------------------

@Composable
private fun SlideshowIndicator(state: PhotoViewerState, modifier: Modifier = Modifier) {
    if (!state.slideshowPlaying) return
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(TvDimen.RadiusM))
            .padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Pause,
            contentDescription = "幻灯片播放中",
            tint = TvColor.Accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(TvDimen.SpaceXs))
        Text(
            text = "幻灯片播放中 · 按方向键暂停",
            color = TvColor.TextPrimary,
            fontSize = TvType.Caption,
            maxLines = 1,
            softWrap = false,
        )
        // 5 秒进度条
        SlideshowProgressBar()
    }
}

@Composable
private fun SlideshowProgressBar() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(SlideshowIntervalMs.toInt(), easing = LinearEasing))
        }
    }
    Spacer(Modifier.width(TvDimen.SpaceS))
    Box(
        Modifier
            .width(96.dp)
            .height(4.dp)
            .background(TvColor.Divider, RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.value)
                .height(4.dp)
                .background(TvColor.Accent, RoundedCornerShape(2.dp)),
        )
    }
}

// 注：原来的左上角 ContextBadge 已删除 —— 上下文信息并入 InfoBar 左侧，避免和标题叠字。
// 注：原来的 HintText 也并进 InfoBar 右侧（见 ViewerInfoBar.kt 的 KeyHintText），
//     否则它会和「★ 9 / 1 / 20 / ★」抢同一行剩余空间，被压成一列竖字。

@Composable
private fun ZoomHint(panX: Float, panY: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(TvDimen.RadiusM))
            .padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "2× 放大", color = TvColor.Accent, fontSize = TvType.Tiny, fontWeight = FontWeight.SemiBold)
        Text(
            text = "方向键平移 · 偏移 %.1f / %.1f".format(panX, panY),
            color = TvColor.TextSecondary,
            fontSize = TvType.Tiny,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 左缘圆形返回按钮（常驻，不参与焦点系统）：
 * 与参考图一致，贴屏幕左缘垂直居中；遥控器 BACK 走全局按键，这里服务鼠标 / 触摸。
 */
@Composable
private fun BackChevron(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = Color.White.copy(alpha = 0.84f),
            modifier = Modifier.size(24.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// 兼容小工具
// ---------------------------------------------------------------------------

/** AnimatedVisibility 的简易包装（避免与 AnimatedContent 作用域混用） */
@Composable
private fun AnimatedVisibilityCompat(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 4 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it / 4 },
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(widthDp = 1920, heightDp = 1080, showBackground = true, backgroundColor = 0xFF08090D)
@Composable
private fun PhotoViewerScreenPreview() {
    NasPhotoTvTheme {
        PhotoViewerScreen(
            items = previewItems(),
            initialIndex = 1,
            contextTitle = "大理洱海",
            onClose = {},
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080, showBackground = true, backgroundColor = 0xFF08090D)
@Composable
private fun PhotoViewerScreenVideoPreview() {
    NasPhotoTvTheme {
        PhotoViewerScreen(
            items = previewItems(),
            initialIndex = 3,
            contextTitle = "最近添加",
            onClose = {},
        )
    }
}

private fun previewItems(): List<MediaItem> = listOf(
    MediaItem(
        id = "p1", title = "大理洱海 · 日出", type = MediaType.PHOTO, url = "raw/wall_sunset",
        dateTaken = LocalDate.of(2025, 5, 12), sizeBytes = 4_865_000L, resolution = "4032x3024",
        albumId = "a_dali", extension = "JPG", starred = true,
    ),
    MediaItem(
        id = "p2", title = "苍山云海", type = MediaType.PHOTO, url = "raw/wall_aurora",
        dateTaken = LocalDate.of(2025, 5, 13), sizeBytes = 6_120_000L, resolution = "6000x4000",
        albumId = "a_dali", extension = "HEIC",
    ),
    MediaItem(
        id = "p3", title = "洱海边的栈桥", type = MediaType.PHOTO, url = "raw/wall_ocean",
        dateTaken = LocalDate.of(2025, 5, 14), sizeBytes = 3_210_000L, resolution = "3840x2160",
        albumId = "a_dali", extension = "JPG",
    ),
    MediaItem(
        id = "p4", title = "海边家庭视频", type = MediaType.VIDEO, url = "raw/video_family",
        thumbnailUrl = "raw/video_family", durationMs = 96_000L,
        dateTaken = LocalDate.of(2025, 5, 15), sizeBytes = 48_200_000L, resolution = "1920x1080",
        albumId = "a_dali", extension = "MOV", starred = true,
    ),
    MediaItem(
        id = "p5", title = "暮色森林", type = MediaType.PHOTO, url = "raw/wall_forest",
        dateTaken = LocalDate.of(2025, 4, 2), sizeBytes = 2_480_000L, resolution = "4032x3024",
        albumId = "a_forest", extension = "JPG",
    ),
    MediaItem(
        id = "p6", title = "黑白街景", type = MediaType.PHOTO, url = "raw/wall_mono",
        dateTaken = LocalDate.of(2025, 3, 21), sizeBytes = 1_980_000L, resolution = "4032x3024",
        albumId = "a_street", extension = "JPG",
    ),
    MediaItem(
        id = "p7", title = "糖果色山谷", type = MediaType.PHOTO, url = "raw/wall_candy",
        dateTaken = LocalDate.of(2025, 3, 1), sizeBytes = 3_880_000L, resolution = "4032x3024",
        albumId = "a_valley", extension = "PNG",
    ),
)
