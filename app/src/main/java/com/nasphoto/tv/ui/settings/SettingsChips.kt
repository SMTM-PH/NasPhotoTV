package com.nasphoto.tv.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * ================= 设置页的小控件（settings-dev 维护） =================
 *
 * 只有「值胶囊 / 静态胶囊 / 开关 / 进度条 / 小标题」——刻意不引 material3 的 Switch/Button，
 * 因为电视端的尺寸、焦点态和 10-foot 可观性都需要自定义。
 */

/**
 * 设置行的「值胶囊」：`‹  翻页数字  ›`。
 * 只有它在聚焦行里才出现左右箭头 —— 遥控器用户据此判断"这一行左右能调"。
 */
@Composable
fun ValueChip(
    text: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = TvColor.Accent,
    minWidth: Int = 132,
) {
    val bg = if (focused) accent.copy(alpha = 0.20f) else TvColor.SurfaceHigh.copy(alpha = 0.55f)
    val fg = if (focused) accent else TvColor.TextSecondary
    Row(
        modifier = modifier
            .height(42.dp)
            .widthIn(min = minWidth.dp)
            .clip(CircleShape)
            .background(bg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ArrowGlyph(visible = focused, pointingLeft = true, color = fg)
        AnimatedValue(text = text, color = fg)
        ArrowGlyph(visible = focused, pointingLeft = false, color = fg)
    }
}

/** 静态胶囊（只读信息，例如设备名称 / 版本号） */
@Composable
fun StaticChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TvColor.TextSecondary,
    accent: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(if (accent) TvColor.Accent.copy(alpha = 0.14f) else TvColor.SurfaceHigh.copy(alpha = 0.55f))
            .padding(horizontal = TvDimen.SpaceM),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = TvType.Caption,
            fontWeight = FontWeight.Medium,
            color = if (accent) TvColor.Accent else color,
            maxLines = 1,
        )
    }
}

/**
 * 值文本的轻量切换动画：旧值向左淡出、新值从右侧滑入（像"翻了一页"）。
 * target 是文本本身，只有真变了才播动画。
 */
@Composable
fun AnimatedValue(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TvType.Body,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInHorizontally(tween(180)) { h -> h / 3 } + fadeIn(tween(160))) togetherWith
                (slideOutHorizontally(tween(140)) { h -> -h / 3 } + fadeOut(tween(120)))
        },
        modifier = modifier,
        label = "settingsValue",
    ) { value ->
        Text(
            text = value,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** 极简的 ‹ › 字形（不引图标库：尺寸可控、任何字体下都垂直居中） */
@Composable
fun ArrowGlyph(visible: Boolean, pointingLeft: Boolean, color: Color) {
    Box(
        Modifier
            .width(26.dp)
            .height(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!visible) return@Box
        Text(
            text = if (pointingLeft) "‹" else "›",
            fontSize = TvType.CardTitle,
            fontWeight = FontWeight.Light,
            color = color.copy(alpha = 0.85f),
        )
    }
}

/**
 * 开关（胶囊轨道 + 滑块，开=强调色，关=中性灰）。
 * 尺寸固定，不因文字长短跳动；聚焦时两侧出现箭头。
 */
@Composable
fun TvSwitch(
    checked: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
    onColor: Color = TvColor.Accent,
) {
    val trackColor = when {
        checked && focused -> onColor.copy(alpha = 0.85f)
        checked -> onColor.copy(alpha = 0.45f)
        focused -> TvColor.SurfaceHigh
        else -> TvColor.SurfaceHigh.copy(alpha = 0.7f)
    }
    val thumbColor = if (checked) Color.White else TvColor.TextTertiary
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 33.dp else 3.dp,
        animationSpec = tween(160),
        label = "switchThumb",
    )

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ArrowGlyph(visible = focused, pointingLeft = true, color = TvColor.TextSecondary)
        Box(
            Modifier
                .size(width = 64.dp, height = 34.dp)
                .clip(CircleShape)
                .background(trackColor),
        ) {
            // 显式选 BoxScope 的 AnimatedVisibility 重载：在 Row 的 lambda 里直接写会被解析成 RowScope 版本
            androidx.compose.animation.AnimatedVisibility(
                visible = checked,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Text(
                    text = "开",
                    fontSize = TvType.Tiny,
                    color = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.padding(start = 9.dp),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !checked,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Text(
                    text = "关",
                    fontSize = TvType.Tiny,
                    color = TvColor.TextTertiary,
                    modifier = Modifier.padding(end = 9.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = thumbOffset)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
        ArrowGlyph(visible = focused, pointingLeft = false, color = TvColor.TextSecondary)
    }
}

