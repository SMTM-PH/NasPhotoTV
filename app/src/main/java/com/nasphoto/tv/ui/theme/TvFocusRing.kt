/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Keep the focus outline inside the control so a clipped card still has a full outline.
 *
 * 焦点环颜色取自语义 token（深色白 / 浅色墨蓝），所以这里必须是 `@Composable`：
 * 先在组合期取色，再把纯 [androidx.compose.ui.graphics.Color] 捕获进 `drawWithContent`
 * 的 DrawScope lambda（DrawScope 不是 Composable 上下文）。
 */
@Composable
internal fun Modifier.tvFocusRing(radius: Dp, alpha: Float = 1f): Modifier {
    val ringColor = TvColor.FocusRing
    return drawWithContent {
        drawContent()
        val width = TvDimen.FocusRingWidth.toPx()
        if (alpha > 0f && size.minDimension > width) {
            val inset = width / 2f
            drawRoundRect(
                color = ringColor.copy(alpha = alpha),
                topLeft = Offset(inset, inset),
                size = Size(size.width - width, size.height - width),
                cornerRadius = CornerRadius((radius.toPx() - inset).coerceAtLeast(0f)),
                style = Stroke(width),
            )
        }
    }
}
