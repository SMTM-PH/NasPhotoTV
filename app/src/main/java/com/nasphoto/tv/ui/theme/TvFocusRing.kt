package com.nasphoto.tv.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** Keep the focus outline inside the control so a clipped card still has a full outline. */
internal fun Modifier.tvFocusRing(radius: Dp, alpha: Float = 1f): Modifier = drawWithContent {
    drawContent()
    val width = TvDimen.FocusRingWidth.toPx()
    if (alpha > 0f && size.minDimension > width) {
        val inset = width / 2f
        drawRoundRect(
            color = TvColor.FocusRing.copy(alpha = alpha),
            topLeft = Offset(inset, inset),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius((radius.toPx() - inset).coerceAtLeast(0f)),
            style = Stroke(width),
        )
    }
}
