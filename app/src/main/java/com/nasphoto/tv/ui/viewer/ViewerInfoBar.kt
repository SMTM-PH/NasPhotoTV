package com.nasphoto.tv.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.isPanoramaPhoto
import com.nasphoto.tv.data.model.isRawPhoto
import com.nasphoto.tv.ui.common.FocusCard
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * ================= 底部缩略图胶片条 =================
 *
 * - 当前图片居中高亮（青色描边 + 星标/播放角标）
 * - 切换图片时用 animateScrollToItem 把当前项滚到中间
 * - 方向键移动焦点即切换主图（无需 ENTER）；ENTER / 点击同样跳转
 */
@Composable
fun FilmStrip(
    state: PhotoViewerState,
    listState: LazyListState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = state.items
    if (items.isEmpty()) return

    val activeThumbFocus = remember { FocusRequester() }
    var pendingFocus by remember { mutableStateOf<Int?>(null) }
    var wasFocused by remember { mutableStateOf(false) }

    // 当前项变化 → 居中滚动
    LaunchedEffect(state.index, listState.layoutInfo.viewportSize.width) {
        val target = (state.index - VisibleThumbCount / 2).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }

    // 进入胶片条时焦点先落回当前项，避免因默认焦点落在别的缩略图上而误切主图；
    // 左右切换后焦点跟随新选中项，让描边、序号和主图始终指向同一张。
    LaunchedEffect(pendingFocus) {
        val target = pendingFocus ?: return@LaunchedEffect
        repeat(2) { withFrameMillis {} }
        if (state.index == target) runCatching { activeThumbFocus.requestFocus() }
        pendingFocus = null
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.hasFocus && !wasFocused) pendingFocus = state.index
                wasFocused = focusState.hasFocus
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val delta = if (event.key == Key.DirectionRight) 1 else -1
                        val next = (state.index + delta).coerceIn(0, items.lastIndex)
                        if (next != state.index) {
                            onSelect(next)
                            pendingFocus = next
                        }
                        true
                    }

                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
        // 左右各留 1/2 屏宽，保证第一张/最后一张也能滚到正中间
        contentPadding = PaddingValues(horizontal = HalfViewportPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
            ThumbCell(
                item = item,
                index = i,
                active = i == state.index,
                starred = state.isStarred(item),
                onClick = { onSelect(i) },
                focusRequester = if (i == state.index) activeThumbFocus else null,
            )
        }
    }
}

/** 当前项高亮：青色/白色描边 + 轻微放大 */
@Composable
private fun ThumbCell(
    item: MediaItem,
    index: Int,
    active: Boolean,
    starred: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val borderWidth by animateDpAsState(
        targetValue = if (active) 3.dp else 0.dp,
        animationSpec = tween(180),
        label = "thumbBorder",
    )
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.62f,
        animationSpec = tween(180),
        label = "thumbAlpha",
    )
    val shape = remember { RoundedCornerShape(TvDimen.RadiusM - 2.dp) }

    FocusCard(
        onClick = onClick,
        modifier = Modifier.size(width = 132.dp, height = 78.dp),
        cornerRadius = TvDimen.RadiusM,
        focusedScale = TvDimen.FocusScale,
        shape = shape,
        focusRequester = focusRequester,
    ) { focused ->
        Box(Modifier.fillMaxSize()) {
            MediaImage(
                url = item.thumbnailUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .graphicsLayerAlpha(if (focused) 1f else alpha),
                targetWidth = 320,
            )
            // 视频角标
            if (item.isVideo()) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp),
                )
            }
            // 收藏角标
            if (starred) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = TvColor.Warn,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(14.dp),
                )
            }
            // 当前项描边
            if (borderWidth > 0.dp) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(BorderStroke(borderWidth, TvColor.Accent), shape),
                )
            }
            // 序号（左下，仅当前项显示，避免整条太吵）
            if (active) {
                Text(
                    text = "${index + 1}",
                    color = TvColor.Bg,
                    fontSize = TvType.Tiny,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .background(TvColor.Accent, RoundedCornerShape(TvDimen.RadiusS - 2.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 左上浮层信息（全屏优先：图片占满整屏，信息随控制栏显隐）
// ---------------------------------------------------------------------------

/** 浮层底：上重下轻的竖向渐变，压住图片高光又不至于糊成一整块黑 */
private val HudBackground = Brush.verticalGradient(
    listOf(Color.Black.copy(alpha = 0.58f), Color.Black.copy(alpha = 0.26f)),
)

/**
 * 看图页左上角浮层：
 *  - LIVE 徽标常驻（它是内容属性，需要在任何构图下都能认出实况）；
 *  - 状态句 / 来源 / 拍摄 / 标题 / 序号随控制栏淡入淡出；
 *  - 「信息」按钮（或方向键上）展开时补上尺寸、文件、ID。
 */
@Composable
fun ViewerHud(
    state: PhotoViewerState,
    contextTitle: String,
    modifier: Modifier = Modifier,
) {
    val item = state.current ?: return
    val starred = state.isStarred(item)
    val fileSize = rememberFileSizeText(item.sizeBytes)

    Column(modifier) {
        HudBadges(item)

        AnimatedVisibility(
            visible = state.chromeVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
        ) {
            // 排版约束（沿用 InfoBar 时代踩过的坑）：文案一律单行 + 省略，
            // 否则电视端窄容器会把字压成一列竖排。
            Column(
                Modifier
                    .background(HudBackground, RoundedCornerShape(TvDimen.RadiusM))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .widthIn(max = 760.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (starred) TvColor.Warn else TvColor.TextTertiary.copy(alpha = 0.8f)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = if (starred) "你喜欢这张照片。" else "正在浏览这组照片。",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(18.dp))

                RailMeta("来源", "Home-NAS" + if (contextTitle.isNotBlank()) " · $contextTitle" else "")
                Spacer(Modifier.height(8.dp))
                RailMeta("拍摄", railDateText(item))

                if (state.infoExpanded) {
                    Spacer(Modifier.height(8.dp))
                    RailMeta("尺寸", item.resolution.ifBlank { "—" })
                    Spacer(Modifier.height(8.dp))
                    RailMeta(
                        "文件",
                        buildString {
                            append(fileSize)
                            if (item.extension.isNotBlank()) append(" · ").append(item.extension.uppercase())
                            if (item.isVideo()) {
                                val d = formatDuration(item.durationMs)
                                if (d.isNotEmpty()) append(" · ").append(d)
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    RailMeta("ID", item.id)
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = item.title,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = (state.index + 1).toString().padStart(2, '0'),
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Thin,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = " / ${state.items.size.toString().padStart(2, '0')}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "方向键切换 · 上键信息 · 下键工具 · 返回关闭",
                    color = Color.White.copy(alpha = 0.32f),
                    fontSize = TvType.Tiny,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** 画面左上角的 LIVE 徽标（与 Apple 相册的实况角标同义） */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    HudBadge(text = "LIVE · 实况", dotColor = TvColor.Accent, modifier = modifier)
}

/**
 * 内容属性徽标，常驻显示（不随控制栏消失）：实况 / RAW / 全景。
 * 这些是「这张是什么」而不是「这张的元数据」，所以不违反屏保/全屏的克制原则。
 */
@Composable
private fun HudBadges(item: MediaItem) {
    val badges = buildList {
        if (item.isLive && item.liveUrl.isNotBlank()) add("LIVE · 实况" to TvColor.Accent)
        if (item.isRawPhoto()) add("RAW · 高清预览" to TvColor.Warn)
        if (item.isPanoramaPhoto()) add("全景 · 左右键平移" to TvColor.AccentAlt)
    }
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs)) {
        badges.forEach { (text, dotColor) -> HudBadge(text = text, dotColor = dotColor) }
    }
    Spacer(Modifier.height(TvDimen.SpaceS))
}

@Composable
private fun HudBadge(text: String, dotColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(TvDimen.RadiusS))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = TvType.Tiny,
            letterSpacing = 1.sp,
        )
    }
}

/** 「标签：值」一行，值单行省略 */
@Composable
private fun RailMeta(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label：",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = TvType.Caption,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = TvType.Caption,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 620.dp),
        )
    }
}

private fun railDateText(item: MediaItem): String =
    "${item.dateTaken.year}年${item.dateTaken.monthValue}月${item.dateTaken.dayOfMonth}日"

// 常量与工具
private const val VisibleThumbCount = 9

/** 左右各留 1/2 屏宽，保证第一张/最后一张也能滚到正中间 */
private val HalfViewportPadding = 560.dp

/** 不改变布局的透明度（避免每帧重组，只有焦点/高亮变化时生效） */
private fun Modifier.graphicsLayerAlpha(a: Float): Modifier =
    this.graphicsLayer { alpha = a }
