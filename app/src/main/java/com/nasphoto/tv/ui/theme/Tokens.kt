/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ================= NAS Photo TV 设计规范（Design Tokens） =================
 * 10-foot UI：所有尺寸按 960x540 dp 画布设计（ProvideTvDesignDensity 会把任意
 * 分辨率/系统密度归一化到这块画布；1080p 上 1dp=2px，4K 上 1dp=4px）。
 * 所有页面必须使用这里的 token，禁止在页面里写魔法色值/字号。
 *
 * 颜色不再是常量，而是从 [LocalTvPalette] 读取的**语义角色**：
 * 深色「影院黑」/ 浅色「画廊白」两套取值见 `Appearance.kt` 与
 * `docs/appearance/README.md`。字段名保持不变，业务代码无需改动。
 * 注意：这些 getter 是 `@Composable` 的，只能在 Composable 上下文里读；
 * 顶层 `val` 的 initializer 不要直接引用 `TvColor.*`，固定暗场请写死常量。
 */
object TvColor {
    /** 全局底色 */
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.bg
    val BgElevated: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.bgElevated
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.surface
    val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.surfaceHigh
    val SurfaceGlass: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.surfaceGlass
    /** 客厅影廊：固定暗场的照片舞台，两套模式下字面相同。 */
    val Stage: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.stage
    val SurfaceSoft: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.surfaceSoft
    val InkOnAccent: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.inkOnAccent
    val CinemaPanel: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.cinemaPanel
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.textMuted
    val Warm: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.warm
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.divider

    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.textSecondary
    val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.textTertiary

    /** 品牌主色：深色冰蓝 / 浅色深冰蓝（浅底上加深以保证对比度）。 */
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.accent
    val AccentDim: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.accentDim
    /** 次强调：星蓝（用于视频/设置选中） */
    val AccentAlt: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.accentAlt
    val AccentAltDim: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.accentAltDim
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.danger
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.warn

    /** 焦点环：深色白描边 + 冰蓝辉光 / 浅色墨蓝描边 + 淡墨投影。 */
    val FocusRing: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.focusRing
    val FocusRingGlow: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.focusRingGlow

    /** 压在照片上的黑遮罩，两套模式下字面相同。 */
    val ScrimTop: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.scrimTop
    val ScrimBottom: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.scrimBottom
}

object TvDimen {
    /** 屏幕外边距 */
    val ScreenPadding = 64.dp
    /** 内容最大宽度留白（右侧安全区） */
    val SafeInset = 48.dp

    val RadiusS = 10.dp
    val RadiusM = 16.dp
    val RadiusL = 24.dp
    val RadiusXL = 32.dp

    val SpaceXs = 8.dp
    val SpaceS = 12.dp
    val SpaceM = 20.dp
    val SpaceL = 32.dp
    val SpaceXl = 48.dp
    val SpaceXxl = 72.dp
    val NavigationHeight = 64.dp
    val ControlHeight = 52.dp
    val HeaderInset = 28.dp
    val IconSize = 24.dp
    val CinemaHeaderHeight = 48.dp
    val CinemaRailInset = 16.dp
    val CinemaThumbnailRadius = 8.dp

    /** 焦点放大比例（标准） */
    const val FocusScale = 1.05f
    const val FocusScaleHero = 1.015f
    const val FocusScaleLarge = 1.12f
    val FocusRingWidth = 3.dp
    val FocusElevation = 24.dp
}

/**
 * 缩略图请求像素：指「按 1080p 设计密度（1dp=2px）计算的目标宽度」，
 * MediaImage 会再按实际设计密度等比放大（4K 上 ×2，封顶 1920）。
 * 目标必须大于卡片显示像素，否则会出现「卡片大而图糊」的粗糙观感。
 */
object TvImage {
    /** 相册封面（256dp 卡片，1080p 显示约 512px，请求 960px ≈ 1.9×） */
    const val AlbumCoverPx = 960
    /** 照片网格缩略图（192dp 卡片，1080p 显示约 384px） */
    const val MediaThumbPx = 640
    /** 详情页满屏背景：只做虚化背景，不需要全分辨率 */
    const val DetailBackdropPx = 1280
}

object TvType {
    /** 大标题（页面标题） */
    val Display = 56.sp
    val CinemaDisplay = 84.sp
    val CinemaCompactDisplay = 40.sp
    /** 屏保大时钟 */
    val ClockHuge = 220.sp
    val ClockLarge = 120.sp
    /** 区块标题 */
    val Section = 30.sp
    /** 卡片标题 */
    val CardTitle = 22.sp
    /** 正文 */
    val Body = 20.sp
    val Label = 18.sp
    /** 辅助 */
    val Caption = 16.sp
    val Tiny = 13.sp
}
