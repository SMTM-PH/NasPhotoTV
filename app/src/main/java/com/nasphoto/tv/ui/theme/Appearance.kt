/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

/**
 * ================= 外观模式（浅色 / 深色） =================
 *
 * 设计说明见 `docs/appearance/README.md`。要点：
 *  - 深色「影院黑」沿用线上 `Tokens.kt` 现值，是默认模式；
 *  - 浅色「画廊白」用晨雾白底 + 纯白卡片，主色加深以保证 10-foot 对比度；
 *  - `Stage` / `CinemaPanel` / `Scrim*` 是「固定暗场」常量，两套模式字面相同；
 *    看图 / 播放 / 屏保通过 [ProvideTvStage] 强制切回深色板，照片永远是主角。
 *
 * 实现约定：`TvColor` 的同名属性读取 [LocalTvPalette]，业务侧 38 个文件一概不用改；
 * 顶层 `val` 的 initializer 不能读 `TvColor.*`（会固化成启动时的配色），固定暗场写死常量。
 */

/** 用户可选的外观模式。默认 [Dark]，保证升级后不会突然变白。 */
enum class TvAppearance {
    System,
    Light,
    Dark,
    ;

    /** 值胶囊用的短标签（10-foot 单行不省略）。 */
    val label: String
        get() = when (this) {
            System -> "跟随系统"
            Light -> "浅色"
            Dark -> "深色"
        }

    /** 设置右栏 / 文档用的完整名称。 */
    val fullLabel: String
        get() = when (this) {
            System -> "跟随系统"
            Light -> "浅色 · 画廊白"
            Dark -> "深色 · 影院黑"
        }

    /** 遥控器 ← → / ENTER 循环顺序：跟随系统 → 浅色 → 深色 → 跟随系统 */
    fun next(): TvAppearance = entries[(ordinal + 1) % entries.size]

    companion object {
        const val PREF_KEY = "appearance"
        val DEFAULT = Dark

        fun fromStored(name: String?): TvAppearance =
            if (name == null) DEFAULT
            else runCatching { valueOf(name) }.getOrDefault(DEFAULT)
    }
}

/**
 * 语义调色板。字段名与 [TvColor] 一一对应；`isLight` 决定 M3 用哪套 colorScheme。
 * 所有半透明色（玻璃 / 描边 / 淡底 / 辉光）都按模式重新定义，不能只换不透明底色。
 */
data class TvPalette(
    val bg: Color,
    val bgElevated: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceGlass: Color,
    val stage: Color,
    val surfaceSoft: Color,
    val inkOnAccent: Color,
    val cinemaPanel: Color,
    val textMuted: Color,
    val warm: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentDim: Color,
    val accentAlt: Color,
    val accentAltDim: Color,
    val danger: Color,
    val warn: Color,
    val focusRing: Color,
    val focusRingGlow: Color,
    val scrimTop: Color,
    val scrimBottom: Color,
    val isLight: Boolean,
)

/** 深色 · 影院黑 Cinema —— 线上现值（仅 TextTertiary 提亮以满足 WCAG AA）。 */
val DarkPalette = TvPalette(
    bg = Color(0xFF06131E),
    bgElevated = Color(0xFF12141B),
    surface = Color(0xFF1A1D26),
    surfaceHigh = Color(0xFF252935),
    surfaceGlass = Color(0x66FFFFFF),
    stage = Color(0xFF0B202F),
    surfaceSoft = Color(0xFF122A3A),
    inkOnAccent = Color(0xFF002232),
    cinemaPanel = Color(0xCC081D2B),
    textMuted = Color(0xFF8D9AA8),
    warm = Color(0xFFCDBA98),
    divider = Color(0x1FFFFFFF),
    textPrimary = Color(0xFFF5F7FA),
    textSecondary = Color(0xFFA9B0BE),
    textTertiary = Color(0xFF8C97A8),
    accent = Color(0xFF31B6ED),
    accentDim = Color(0x3331B6ED),
    accentAlt = Color(0xFF5B8CFF),
    accentAltDim = Color(0x335B8CFF),
    danger = Color(0xFFFF6B6B),
    warn = Color(0xFFFFC857),
    focusRing = Color(0xFFFFFFFF),
    focusRingGlow = Color(0x8031B6ED),
    scrimTop = Color(0xB3000000),
    scrimBottom = Color(0xD9000000),
    isLight = false,
)

/** 浅色 · 画廊白 Gallery —— 晨雾白底、纯白卡片、深冰蓝主色、墨蓝焦点环。 */
val LightPalette = TvPalette(
    bg = Color(0xFFE9EEF4),
    bgElevated = Color(0xFFF3F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFDDE5EE),
    surfaceGlass = Color(0xB3FFFFFF),
    // Stage 是普通语义面：浅色下换成柔和的冷调面板，浏览界面整体才不会「半亮半暗」。
    // 看图 / 播放 / 屏保由 ProvideTvStage 强制切回深色板，那里的舞台仍是 #0B202F。
    stage = Color(0xFFDDE7F0),
    surfaceSoft = Color(0xFFD7E6F1),
    inkOnAccent = Color(0xFFFFFFFF),
    // ↓ 压在照片上的浮层 / 遮罩：两套模式字面相同，与外观无关
    cinemaPanel = Color(0xCC081D2B),
    scrimTop = Color(0xB3000000),
    scrimBottom = Color(0xD9000000),
    // ↑ 压在照片上的浮层
    textMuted = Color(0xFF516376),
    warm = Color(0xFF755A34),
    divider = Color(0x1F12263A),
    textPrimary = Color(0xFF0E1A28),
    textSecondary = Color(0xFF44566B),
    textTertiary = Color(0xFF546579),
    accent = Color(0xFF096494),
    accentDim = Color(0x1F096494),
    accentAlt = Color(0xFF3557B8),
    accentAltDim = Color(0x1F3557B8),
    danger = Color(0xFFAF2E26),
    warn = Color(0xFF8A5700),
    focusRing = Color(0xFF0B3A5C),
    focusRingGlow = Color(0x330B3A5C),
    isLight = true,
)

/** 当前子树的调色板；默认深色，让所有已有 `@Preview` 保持原样。 */
val LocalTvPalette = staticCompositionLocalOf { DarkPalette }

/** 解析「跟随系统」：系统夜间 → 影院黑；系统日间 → 画廊白；未定义 → 回落影院黑。 */
@Composable
@ReadOnlyComposable
fun resolveTvPalette(appearance: TvAppearance): TvPalette = when (appearance) {
    TvAppearance.Light -> LightPalette
    TvAppearance.Dark -> DarkPalette
    TvAppearance.System -> {
        val nightMask = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (nightMask) {
            Configuration.UI_MODE_NIGHT_NO -> LightPalette
            Configuration.UI_MODE_NIGHT_YES -> DarkPalette
            else -> DarkPalette
        }
    }
}

/** 给子树换调色板。嵌套调用天然支持「页面浅色、舞台深色」。 */
@Composable
fun ProvideTvPalette(palette: TvPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTvPalette provides palette, content = content)
}

/**
 * 固定暗场：看图、视频播放、屏保、沉浸式照片舞台一律用深色板，
 * 与用户选的外观无关。避免白色舞台抢照片、切页白闪、夜间眩光与 OLED 烧屏。
 */
@Composable
fun ProvideTvStage(content: @Composable () -> Unit) = ProvideTvPalette(DarkPalette, content)

/**
 * M3 组件（ProgressIndicator / Slider / Switch …）也必须跟着换，否则仍是深色。
 * 以 light/dark 工厂的默认值为底，再覆盖本项目用到的角色色，避免逐个补全 M3 的全部字段。
 */
fun tvColorScheme(palette: TvPalette): ColorScheme {
    val base = if (palette.isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = palette.accent,
        onPrimary = palette.inkOnAccent,
        primaryContainer = palette.surfaceSoft,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.accentAlt,
        onSecondary = palette.inkOnAccent,
        secondaryContainer = palette.surfaceSoft,
        onSecondaryContainer = palette.textPrimary,
        background = palette.bg,
        onBackground = palette.textPrimary,
        surface = palette.bgElevated,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surface,
        onSurfaceVariant = palette.textSecondary,
        surfaceTint = palette.accent,
        error = palette.danger,
        onError = palette.inkOnAccent,
        errorContainer = palette.surfaceHigh,
        onErrorContainer = palette.danger,
        outline = palette.divider,
        outlineVariant = palette.divider,
        scrim = Color(0xFF000000),
        surfaceBright = palette.surface,
        surfaceDim = palette.bgElevated,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surfaceHigh,
        surfaceContainerHighest = palette.surfaceHigh,
        surfaceContainerLow = palette.bgElevated,
        surfaceContainerLowest = palette.bg,
    )
}
