package com.nasphoto.tv.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.ui.common.FocusCard
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * ================= 底部工具按钮行 =================
 *
 * 左右翻图由画面方向键处理。这里只保留五个操作；横向滚动兜底超大字体，
 * 控件始终留有焦点缩放余量，UP 回照片、DOWN 进入当前缩略图。
 */
@Composable
fun ViewerToolBar(
    state: PhotoViewerState,
    onToggleInfo: () -> Unit,
    onToggleStar: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onToggleZoom: () -> Unit,
    onClose: () -> Unit,
    onOpenVideo: () -> Unit,
    onReturnToImage: () -> Unit,
    onEnterFilmStrip: () -> Unit,
    /** 「下键 / TAB / MENU 进工具行」的落点：挂在第一个按钮上 */
    tabFocus: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val item = state.current
    Row(
        modifier = modifier.fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onReturnToImage(); true }
                    Key.DirectionDown -> { onEnterFilmStrip(); true }
                    else -> false
                }
            }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = TvDimen.SpaceXs, vertical = TvDimen.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolChip(
            icon = if (state.isStarred(item)) Icons.Filled.Star else Icons.Filled.StarBorder,
            label = if (state.isStarred(item)) "已收藏" else "收藏",
            active = state.isStarred(item),
            activeColor = TvColor.Warn,
            onClick = onToggleStar,
            tabFocus = tabFocus,
        )
        ToolChip(
            icon = if (state.slideshowPlaying && item?.isVideo() != true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = if (item?.isVideo() == true) "播放视频" else if (state.slideshowPlaying) "暂停" else "播放照片",
            active = state.slideshowPlaying,
            onClick = if (item?.isVideo() == true) onOpenVideo else onToggleSlideshow,
        )
        ToolChip(
            icon = Icons.Filled.Info,
            label = "信息",
            active = state.infoExpanded,
            onClick = onToggleInfo,
        )
        ToolChip(
            icon = Icons.Filled.Fullscreen,
            label = if (state.zoomed) "适应屏幕" else "放大",
            active = state.zoomed,
            onClick = onToggleZoom,
        )
        ToolChip(
            icon = Icons.Filled.Close,
            label = "关闭",
            danger = true,
            onClick = onClose,
        )
    }
}

/**
 * 单个工具按钮。
 *
 * 直接把 requester 交给 FocusCard，保证程序化焦点与描边属于同一节点。
 */
@Composable
fun ToolChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = TvColor.Accent,
    danger: Boolean = false,
    tabFocus: FocusRequester? = null,
) {
    val accent = if (danger) TvColor.Danger else activeColor
    val latestOnClick by rememberUpdatedState(onClick)
    FocusCard(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            // 只补指针手势，不添加第二个 clickable/focusable 节点。
            .pointerInput(Unit) { detectTapGestures(onTap = { latestOnClick() }) },
        focusRequester = tabFocus,
        cornerRadius = TvDimen.RadiusS,
        focusedScale = TvDimen.FocusScale,
    ) { focused ->
        Row(
            modifier = Modifier
                .height(44.dp)
                .background(
                    when {
                        focused -> TvColor.Accent
                        active -> accent.copy(alpha = 0.16f)
                        else -> TvColor.SurfaceHigh.copy(alpha = 0.86f)
                    },
                )
                .padding(horizontal = TvDimen.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = when {
                    focused -> TvColor.Bg
                    active -> accent
                    else -> TvColor.TextPrimary
                },
            )
            Spacer(Modifier.width(TvDimen.SpaceXs))
            Text(
                text = label,
                color = if (focused) TvColor.Bg else TvColor.TextPrimary,
                fontSize = TvType.Caption,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** 键位小胶囊，例如 ◀ / ▶ / ESC */
@Composable
fun KeyHint(text: String, focused: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 30.dp, height = 22.dp)
            .background(
                color = if (focused) TvColor.Bg.copy(alpha = 0.18f) else TvColor.Divider,
                shape = RoundedCornerShape(TvDimen.RadiusS),
            )
            .border(
                width = 1.dp,
                color = if (focused) TvColor.Bg.copy(alpha = 0.35f) else TvColor.Divider,
                shape = RoundedCornerShape(TvDimen.RadiusS),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (focused) TvColor.Bg.copy(alpha = 0.75f) else TvColor.TextTertiary,
            fontSize = TvType.Tiny,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 圆形小按钮（幻灯片进度、提示圆点等复用） */
@Composable
fun CircleBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = TvColor.TextPrimary,
    background: Color = Color.Black.copy(alpha = 0.55f),
    size: Int = 44,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}
