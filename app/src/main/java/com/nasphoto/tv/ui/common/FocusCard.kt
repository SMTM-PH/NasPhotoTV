package com.nasphoto.tv.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen

/**
 * ================= 电视端可聚焦卡片（Lead 提供的公共组件，供各页面复用） =================
 *
 * 行为：聚焦时放大 + 白色描边 + 阴影 + 青色辉光。
 * 注意：外层容器必须留出 (scale-1)*size/2 的空间，避免放大被 clip。
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = TvDimen.RadiusM,
    focusedScale: Float = TvDimen.FocusScale,
    shape: Shape = RoundedCornerShape(cornerRadius),
    focusRequester: FocusRequester? = null,
    borderColor: Color = TvColor.FocusRing,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "focusScale",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "focusRing",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (focused) {
                    Modifier.shadow(
                        elevation = TvDimen.FocusElevation,
                        shape = shape,
                        ambientColor = TvColor.FocusRingGlow,
                        spotColor = TvColor.FocusRingGlow,
                    )
                } else {
                    Modifier
                },
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(TvColor.Surface)
            .onFocusChanged { state ->
                if (focused != state.isFocused) {
                    focused = state.isFocused
                    onFocusChanged?.invoke(state.isFocused)
                }
            }
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            },
    ) {
        content(focused)
        if (ringAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(BorderStroke(TvDimen.FocusRingWidth, borderColor.copy(alpha = ringAlpha)), shape),
            )
        }
    }
}
