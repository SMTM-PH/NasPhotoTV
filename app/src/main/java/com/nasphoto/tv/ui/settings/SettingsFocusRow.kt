package com.nasphoto.tv.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen

/**
 * ================= 设置页的可聚焦行（settings-dev 维护） =================
 *
 * 与媒体库的 TvFocusBox 的差异（故意为之）：
 *  - 设置页是"纵列长列表"，放大 1.08 会让相邻行抖动 → 只做 1.0 → [SETTINGS_FOCUS_SCALE](1.02) 的极轻推近；
 *  - 焦点态靠「行底色 TvColor.SurfaceHigh + 白色 3dp 描边」表达，符合契约 5「不能只靠颜色变化」；
 *  - 行内部只拦截 LEFT/RIGHT 调值；UP/DOWN 一律放行，交给焦点系统换行。
 *    否则值行会把上下键吞掉，长列表里根本走不出去。
 */

/** 设置行聚焦时的放大比（≤1.02，避免纵向列表抖动；契约要求 1.0~1.02） */
const val SETTINGS_FOCUS_SCALE = 1.02f

/**
 * 一行设置项。
 *
 * @param onConfirm ENTER / DPAD_CENTER：执行（跳转 / 预览 / 单项切换）
 * @param onAdjust 左右键回调。LEFT/RIGHT 一律拦截（含边界情况，避免焦点乱飞到右侧面板）。
 * @param autoOnFocus 焦点移入这一行时自动执行一次（用于"锁定开关/预览"这类行：按 UP/DOWN 经过即生效，遥控器手感最顺）
 */
@Composable
fun SettingsFocusRow(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onConfirm: () -> Unit,
    onAdjust: ((isLeft: Boolean) -> Boolean)? = null,
    onFocused: ((Boolean) -> Unit)? = null,
    autoOnFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) SETTINGS_FOCUS_SCALE else 1f,
        animationSpec = tween(140),
        label = "settingsRowScale",
    )
    val shape = RoundedCornerShape(TvDimen.RadiusM)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(SettingsRowHeight)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(if (focused) TvColor.SurfaceHigh else TvColor.Surface.copy(alpha = 0.34f))
            .border(
                width = if (focused) TvDimen.FocusRingWidth else 1.dp,
                color = if (focused) TvColor.FocusRing else TvColor.Divider,
                shape = shape,
            )
            .onFocusChanged { state ->
                if (focused != state.isFocused) {
                    focused = state.isFocused
                    onFocused?.invoke(state.isFocused)
                    if (state.isFocused) autoOnFocus?.invoke()
                }
            }
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onConfirm()
                        true
                    }

                    Key.DirectionLeft -> {
                        onAdjust?.invoke(true)
                        true
                    }

                    Key.DirectionRight -> {
                        onAdjust?.invoke(false)
                        true
                    }

                    // 上下永远交给 Compose 默认焦点搜索，保证在长列表里能正常换行
                    Key.DirectionUp, Key.DirectionDown -> false

                    else -> false
                }
            },
    ) {
        content(focused)
    }
}

/** 行高：1080p 下 8~10 行刚好一屏 + 上下留白 */
val SettingsRowHeight = 62.dp
