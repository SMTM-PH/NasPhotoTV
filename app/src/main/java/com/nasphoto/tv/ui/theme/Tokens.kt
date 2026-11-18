package com.nasphoto.tv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ================= NAS Photo TV 设计规范（Design Tokens） =================
 * 10-foot UI：所有尺寸按 960x540 dp 画布设计（ProvideTvDesignDensity 会把任意
 * 分辨率/系统密度归一化到这块画布；1080p 上 1dp=2px，4K 上 1dp=4px）。
 * 所有页面必须使用这里的 token，禁止在页面里写魔法色值/字号。
 */
object TvColor {
    /** 全局底色（深空黑，带一点冷调） */
    val Bg = Color(0xFF06131E)
    val BgElevated = Color(0xFF12141B)
    val Surface = Color(0xFF1A1D26)
    val SurfaceHigh = Color(0xFF252935)
    val SurfaceGlass = Color(0x66FFFFFF)
    /** 客厅影廊：安静的照片舞台与柔和的功能面。 */
    val Stage = Color(0xFF0B202F)
    val SurfaceSoft = Color(0xFF122A3A)
    val InkOnAccent = Color(0xFF002232)
    val CinemaPanel = Color(0xCC081D2B)
    val TextMuted = Color(0xFF8D9AA8)
    val Warm = Color(0xFFCDBA98)
    val Divider = Color(0x1FFFFFFF)

    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFFA9B0BE)
    val TextTertiary = Color(0xFF6F7787)

    /** 品牌主色：冰蓝，与沉浸式照片舞台呼应。 */
    val Accent = Color(0xFF31B6ED)
    val AccentDim = Color(0x3331B6ED)
    /** 次强调：星蓝（用于视频/设置选中） */
    val AccentAlt = Color(0xFF5B8CFF)
    val AccentAltDim = Color(0x335B8CFF)
    val Danger = Color(0xFFFF6B6B)
    val Warn = Color(0xFFFFC857)

    /** 焦点环 */
    val FocusRing = Color(0xFFFFFFFF)
    val FocusRingGlow = Color(0x8031B6ED)

    val ScrimTop = Color(0xB3000000)
    val ScrimBottom = Color(0xD9000000)
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
    /** 辅助 */
    val Caption = 16.sp
    val Tiny = 13.sp
}
