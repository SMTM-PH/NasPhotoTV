package com.nasphoto.tv.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.ui.common.FocusCard
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * ================= 底部工具按钮行 =================
 *
 * 「◀ 上一张」「▶ 下一张」「ℹ 信息」「★ 收藏」「⏵ 幻灯片」「⛶ 放大」「✕ 关闭」
 * 全部通过 FocusCard 实现：聚焦时放大 + 白描边 + 青光晕（契约要求「不能只靠颜色变化」）。
 */
@Composable
fun ViewerToolBar(
    state: PhotoViewerState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleInfo: () -> Unit,
    onToggleStar: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onToggleZoom: () -> Unit,
    onClose: () -> Unit,
    /** 「下键 / TAB / MENU 进工具行」的落点：挂在第一个按钮上 */
    tabFocus: androidx.compose.ui.focus.FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val item = state.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolChip(
            icon = Icons.Filled.ArrowBackIosNew,
            label = "上一张",
            hint = "◀",
            onClick = onPrev,
            tabFocus = tabFocus,
        )
        ToolChip(
            icon = Icons.Filled.ArrowForwardIos,
            label = "下一张",
            hint = "▶",
            onClick = onNext,
        )
        ToolChip(
            icon = Icons.Filled.Info,
            label = "信息",
            hint = "ⓘ",
            active = state.infoExpanded,
            onClick = onToggleInfo,
        )
        ToolChip(
            icon = if (state.isStarred(item)) Icons.Filled.Star else Icons.Filled.StarBorder,
            label = if (state.isStarred(item)) "已收藏" else "收藏",
            hint = "★",
            active = state.isStarred(item),
            activeColor = TvColor.Warn,
            onClick = onToggleStar,
        )
        ToolChip(
            icon = if (state.slideshowPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = if (state.slideshowPlaying) "暂停" else "幻灯片",
            hint = "⏵",
            active = state.slideshowPlaying,
            onClick = onToggleSlideshow,
        )
        ToolChip(
            icon = Icons.Filled.Fullscreen,
            label = if (state.zoomed) "适应屏幕" else "放大查看",
            hint = "⛶",
            active = state.zoomed,
            onClick = onToggleZoom,
        )
        ToolChip(
            icon = Icons.Filled.Close,
            label = "关闭",
            hint = "ESC",
            danger = true,
            onClick = onClose,
        )
    }
}

/**
 * 单个工具按钮。
 *
 * 用裸 `Box + focusable + onFocusChanged` 驱动焦点态（不用 FocusCard），
 * 这样才能在 [tabFocus] 里挂 FocusRequester 供「Tab / MENU 键跳进按钮行」使用。
 */
@Composable
fun ToolChip(
    icon: ImageVector,
    label: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = TvColor.Accent,
    danger: Boolean = false,
    tabFocus: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val accent = if (danger) TvColor.Danger else activeColor
    FocusCard(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .then(tabFocus?.let { Modifier.focusRequester(it) } ?: Modifier),
        cornerRadius = 28.dp,
        focusedScale = TvDimen.FocusScaleLarge,
    ) { focused ->
        Row(
            modifier = Modifier
                .height(56.dp)
                .background(
                    when {
                        focused -> TvColor.Accent
                        active -> accent.copy(alpha = 0.16f)
                        else -> TvColor.SurfaceHigh.copy(alpha = 0.86f)
                    },
                )
                .padding(horizontal = TvDimen.SpaceM),
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
            )
            Spacer(Modifier.width(TvDimen.SpaceXs))
            // 键位提示：电视遥控器上对应哪个键
            KeyHint(text = hint, focused = focused)
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
