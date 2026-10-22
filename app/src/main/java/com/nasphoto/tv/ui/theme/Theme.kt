package com.nasphoto.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val TvColorScheme = darkColorScheme(
    primary = TvColor.Accent,
    onPrimary = Color(0xFF00210F),
    secondary = TvColor.AccentAlt,
    onSecondary = Color.White,
    background = TvColor.Bg,
    onBackground = TvColor.TextPrimary,
    surface = TvColor.BgElevated,
    onSurface = TvColor.TextPrimary,
    surfaceVariant = TvColor.Surface,
    onSurfaceVariant = TvColor.TextSecondary,
    error = TvColor.Danger,
)

private val TvTypography = Typography(
    displayLarge = TextStyle(fontSize = TvType.Display, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = TvType.Section, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = TvType.CardTitle, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = TvType.Body, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = TvType.Caption, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = TvType.Tiny, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

object TvShapes {
    val S = RoundedCornerShape(TvDimen.RadiusS)
    val M = RoundedCornerShape(TvDimen.RadiusM)
    val L = RoundedCornerShape(TvDimen.RadiusL)
    val Xl = RoundedCornerShape(TvDimen.RadiusXL)
}

@Composable
fun NasPhotoTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        typography = TvTypography,
        content = content,
    )
}
