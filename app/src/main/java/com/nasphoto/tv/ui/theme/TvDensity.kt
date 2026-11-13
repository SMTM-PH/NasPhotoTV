package com.nasphoto.tv.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * ================= 电视端统一设计画布 =================
 *
 * 为什么需要：Android TV 报告的「逻辑画布」差异极大，同一套 dp 尺寸在不同机器上
 * 观感完全不同。实测三种典型情况（都是物理 1080p/4K 面板）：
 *
 *  - `1920x1080 @ 320dpi` → 960x540dp：设计基准，正常；
 *  - `1920x1080 @ 640dpi` → 480x270dp：**图标巨大、一行只放得下两张卡、文字溢出**；
 *  - 4K 面板 `@ 320dpi`     → 1920x1080dp：元素相对变小、版面空荡。
 *
 * 本 App 的尺寸（TvDimen/TvType）都是按 960x540dp 设计的。这里把「1dp = 多少像素」
 * 重新算一遍，使**任何分辨率、任何 densityDpi 下都恰好是 960x540dp 的设计画布**，
 * 尺寸与排版在全系电视上一致；同时因为仍然按物理像素绘制，4K 面板上文字/图片是原生 4K 清晰度。
 *
 * 取 `min(宽比例, 高比例)` 保证两个方向都不小于设计基准：
 * 16:9 恰为 960x540；4:3 得到 960x720（多出的高度可用）；21:9 得到 1280x540。
 */
@Composable
fun ProvideTvDesignDensity(
    designWidthDp: Float = 960f,
    designHeightDp: Float = 540f,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints {
        val base = LocalDensity.current
        // BoxWithConstraints 的 maxWidth/maxHeight 已是 dp；换算成物理像素后再算比例
        val widthPx = with(base) { maxWidth.toPx() }
        val heightPx = with(base) { maxHeight.toPx() }
        val scale = remember(widthPx, heightPx, base.density, base.fontScale) {
            designDensityScale(widthPx, heightPx, designWidthDp, designHeightDp, base.density)
        }
        CompositionLocalProvider(
            // fontScale 保留系统无障碍字号缩放
            LocalDensity provides Density(density = scale, fontScale = base.fontScale),
        ) {
            content()
        }
    }
}

/**
 * 纯函数：由窗口物理像素算出「1dp 对应多少像素」。
 *
 * 取 `min(宽/设计宽, 高/设计高)`，保证两个方向都不小于设计画布；
 * 尺寸非法（<=0）时回退到系统密度，避免出现 0 密度导致布局塌缩。
 */
internal fun designDensityScale(
    widthPx: Float,
    heightPx: Float,
    designWidthDp: Float,
    designHeightDp: Float,
    fallbackDensity: Float,
): Float {
    if (widthPx <= 0f || heightPx <= 0f) return fallbackDensity
    if (designWidthDp <= 0f || designHeightDp <= 0f) return fallbackDensity
    return minOf(widthPx / designWidthDp, heightPx / designHeightDp)
}
