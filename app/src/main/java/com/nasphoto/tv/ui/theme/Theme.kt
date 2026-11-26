package com.nasphoto.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val TvTypography = Typography(
    displayLarge = TextStyle(fontSize = TvType.Display, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = TvType.Section, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = TvType.CardTitle, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = TvType.Label, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = TvType.Body, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = TvType.Caption, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = TvType.Label,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(fontSize = TvType.Tiny, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

object TvShapes {
    val S = RoundedCornerShape(TvDimen.RadiusS)
    val M = RoundedCornerShape(TvDimen.RadiusM)
    val L = RoundedCornerShape(TvDimen.RadiusL)
    val Xl = RoundedCornerShape(TvDimen.RadiusXL)
}

/**
 * 应用根主题。
 *
 * @param appearance 用户选择的外观；默认 [TvAppearance.DEFAULT]（深色），
 *   保留无参调用以兼容全部 `@Preview` 与既有代码。
 *
 * 主题做三件事：解析调色板 → 通过 [ProvideTvPalette] 下发语义 token →
 * 用 [tvColorScheme] 同步 M3 组件配色。看图 / 播放 / 屏保请用 [ProvideTvStage]
 * 在子树里强制切回深色板。
 */
@Composable
fun NasPhotoTvTheme(
    appearance: TvAppearance = TvAppearance.DEFAULT,
    content: @Composable () -> Unit,
) {
    val palette = resolveTvPalette(appearance)
    ProvideTvPalette(palette) {
        MaterialTheme(
            colorScheme = tvColorScheme(palette),
            typography = TvTypography,
            content = content,
        )
    }
}
