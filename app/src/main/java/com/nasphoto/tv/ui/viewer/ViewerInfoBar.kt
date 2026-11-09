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
    enterToken: Int = 0,
    onReturnToToolbar: () -> Unit = {},
    /**
     * 按确认键选中某张缩略图后的动作。
     * 与 [onSelect]（焦点移动时的预览切图）分开：确认后要收起浮层、焦点还给全屏画面，
     * 否则浮层一直挂着、再按确认也只是重复选中，用户会觉得「退不出全屏」。
     */
    onConfirm: ((Int) -> Unit)? = null,
) {
    val items = state.items
    if (items.isEmpty()) return

    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(id: String) = requesters.getOrPut(id) { FocusRequester() }
    var wasFocused by remember { mutableStateOf(false) }

    // 自动播放或从画面翻页时只滚动。胶片条自己聚焦时交给 LazyRow 让目标可见。
    LaunchedEffect(state.index, listState.layoutInfo.viewportSize.width) {
        if (!wasFocused) listState.scrollToItem((state.index - 2).coerceAtLeast(0))
    }

    // DOWN 的单次请求：先组合当前项，再交给它焦点；切图不会重启这段恢复逻辑。
    LaunchedEffect(enterToken) {
        if (enterToken == 0) return@LaunchedEffect
        val current = state.current ?: return@LaunchedEffect
        listState.scrollToItem((state.index - 1).coerceAtLeast(0))
        repeat(3) {
            withFrameMillis { }
            if (runCatching { requester(current.id).requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                wasFocused = focusState.hasFocus
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onReturnToToolbar(); true }
                    Key.DirectionDown -> true
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
        contentPadding = PaddingValues(horizontal = TvDimen.ScreenPadding,
            vertical = TvDimen.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
            ThumbCell(
                item = item,
                index = i,
                active = i == state.index,
                starred = state.isStarred(item),
                onClick = { (onConfirm ?: onSelect)(i) },
                focusRequester = requester(item.id),
                onFocused = { onSelect(i) },
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
    onFocused: () -> Unit,
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
        modifier = Modifier.size(width = 112.dp, height = 64.dp),
        cornerRadius = TvDimen.RadiusM,
        focusedScale = TvDimen.FocusScale,
        shape = shape,
        focusRequester = focusRequester,
        onFocusChanged = { if (it) onFocused() },
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

/** 小型瞬时浮层；详细文件信息仅在用户主动展开时出现。 */
@Composable
fun ViewerHud(
    state: PhotoViewerState,
    contextTitle: String,
    visible: Boolean = state.chromeVisible,
    modifier: Modifier = Modifier,
) {
    val item = state.current ?: return
    val fileSize = rememberFileSizeText(item.sizeBytes)
    AnimatedVisibility(
        visible = visible || state.infoExpanded,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(140)),
        modifier = modifier,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp)
                .background(HudBackground, RoundedCornerShape(TvDimen.RadiusS))
                .padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceS),
        ) {
            Text(item.title.ifBlank { "未命名的照片" }, color = TvColor.TextPrimary,
                fontSize = TvType.CardTitle, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(TvDimen.SpaceXs))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(railDateText(item), color = TvColor.TextSecondary,
                    fontSize = TvType.Caption, maxLines = 1,
                    modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(TvDimen.SpaceM))
                Text(state.counterText, color = TvColor.TextSecondary,
                    fontSize = TvType.Caption, maxLines = 1)
            }
            if (state.infoExpanded) {
                Spacer(Modifier.height(TvDimen.SpaceS))
                if (contextTitle.isNotBlank()) {
                    RailMeta("相册", contextTitle)
                    Spacer(Modifier.height(TvDimen.SpaceXs))
                }
                RailMeta("尺寸", item.resolution.ifBlank { "—" })
                Spacer(Modifier.height(TvDimen.SpaceXs))
                RailMeta("文件", listOf(fileSize, item.extension.uppercase(),
                    if (item.isVideo()) formatDuration(item.durationMs) else "")
                    .filter { it.isNotBlank() }.joinToString(" · "))
                Spacer(Modifier.height(TvDimen.SpaceXs))
                HudBadges(item)
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
            modifier = Modifier.weight(1f),
        )
    }
}

private fun railDateText(item: MediaItem): String =
    "${item.dateTaken.year}年${item.dateTaken.monthValue}月${item.dateTaken.dayOfMonth}日"

/** 不改变布局的透明度（避免每帧重组，只有焦点/高亮变化时生效） */
private fun Modifier.graphicsLayerAlpha(a: Float): Modifier =
    this.graphicsLayer { alpha = a }
