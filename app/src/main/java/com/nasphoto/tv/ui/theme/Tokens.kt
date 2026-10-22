package com.nasphoto.tv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ================= NAS Photo TV 设计规范（Design Tokens） =================
 * 10-foot UI：所有尺寸按 1920x1080 dp 画布设计（activity 未做 density 缩放时即 1dp≈1px on 1080p）。
 * 所有页面必须使用这里的 token，禁止在页面里写魔法色值/字号。
 */
object TvColor {
    /** 全局底色（深空黑，带一点冷调） */
    val Bg = Color(0xFF08090D)
    val BgElevated = Color(0xFF12141B)
    val Surface = Color(0xFF1A1D26)
    val SurfaceHigh = Color(0xFF252935)
    val SurfaceGlass = Color(0x66FFFFFF)
    val Divider = Color(0x1FFFFFFF)

    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFFA9B0BE)
    val TextTertiary = Color(0xFF6F7787)

    /** 品牌主色：极光青 */
    val Accent = Color(0xFF3DDC97)
    val AccentDim = Color(0x333DDC97)
    /** 次强调：星蓝（用于视频/设置选中） */
    val AccentAlt = Color(0xFF5B8CFF)
    val AccentAltDim = Color(0x335B8CFF)
    val Danger = Color(0xFFFF6B6B)
    val Warn = Color(0xFFFFC857)

    /** 焦点环 */
    val FocusRing = Color(0xFFFFFFFF)
    val FocusRingGlow = Color(0x803DDC97)

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

    /** 焦点放大比例（标准） */
    const val FocusScale = 1.08f
    const val FocusScaleLarge = 1.12f
    val FocusRingWidth = 3.dp
    val FocusElevation = 24.dp
}

object TvType {
    /** 大标题（页面标题） */
    val Display = 56.sp
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
