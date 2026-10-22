package com.nasphoto.tv.ui.screensaver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.ScreensaverSettings
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * ================= 屏保（待机显示）的时间 / 文案 / 农历工具 =================
 *
 * 设计原则：
 *  - 只有「秒」由状态驱动，且每帧最多让一个 Text 重组；
 *  - 日期 / 农历 / 天气等派生文案只在「秒」变化时用 remember(second) 计算，不重排整棵树；
 *  - 全部中文字面量，零新增依赖（农历用内置锚点映射，见 [lunarLabel]）。
 */

// ---------------------------------------------------------------------------
// 每秒时钟
// ---------------------------------------------------------------------------

/**
 * 对齐到「下一个整秒」再更新，避免 delay(1000) 累积漂移导致跳秒。
 */
@Composable
fun rememberNowEverySecond(): LocalDateTime {
    val now = remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = LocalDateTime.now()
            now.value = current
            val millisIntoSecond = current.nano / 1_000_000L
            delay((1000L - millisIntoSecond).coerceAtLeast(16L))
        }
    }
    return now.value
}

/**
 * 把 [now] 拆成按单位分层的稳定 key：
 *  - [ClockParts.hmKey] 变化 ⟺ 分钟变化（大数字翻页）
 *  - [ClockParts.second] 变化 ⟺ 秒变化（秒针/冒号/小秒字）
 *  - [ClockParts.dayKey] 变化 ⟺ 跨天（日期文案）
 */
@Composable
fun rememberClockParts(now: LocalDateTime): ClockParts = remember(now) {
    ClockParts(
        hour = two(now.hour),
        minute = two(now.minute),
        second = now.second,
        hmKey = "${two(now.hour)}:${two(now.minute)}",
        dayKey = now.toLocalDate().toEpochDay(),
    )
}

data class ClockParts(
    val hour: String,
    val minute: String,
    /** "09:41" */
    val hmKey: String,
    val second: Int,
    /** 天序号：日期类文案只在跨天时更新 */
    val dayKey: Long,
)

// ---------------------------------------------------------------------------
// 中文日期 / 星期 / 农历
// ---------------------------------------------------------------------------

private val WEEKDAY_CN = arrayOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
private val MONTH_CN = arrayOf("一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")
private val LUNAR_MONTH_CN = arrayOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
private val LUNAR_DAY_CN = arrayOf(
    "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
    "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
)
private val GAN = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
private val ZHI = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

/** ISO dayOfWeek（1=周一）→「星期一」 */
fun zhWeekday(date: LocalDate): String = WEEKDAY_CN[date.dayOfWeek.value - 1]

/** "10月19日" */
fun zhMonthDay(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

/** "2026年10月19日" */
fun zhFullDate(date: LocalDate): String = "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

/** 「十月十九」风格（DATE_FOCUS 大字日期用） */
fun zhMonthDayWords(date: LocalDate): String = "${MONTH_CN[date.monthValue - 1]}${zhLunarNum(date.dayOfMonth)}"

/** 1..31 → 一…三十一（汉字数字，不带「十」前缀混淆） */
fun zhLunarNum(n: Int): String {
    val digits = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
    return when {
        n <= 10 -> digits[n.coerceIn(0, 10)]
        n < 20 -> "十" + digits[n - 10]
        n % 10 == 0 -> digits[n / 10] + "十"
        else -> digits[n / 10] + "十" + digits[n % 10]
    }
}

/**
 * 屏保「农历小字」，例如「九月十九 · 丙午年」。
 *
 * 实现说明（原型取舍）：真正的农历朔望排算需要 1900-2100 年表，这里用两段轻量映射：
 *  - 年干支：以 1984 甲子年为准的通用公式（真实准确）；
 *  - 月/日：以 2026-10-10（九月初一）为锚点，按 30/29 天交替大小月循环推算。
 *  在 demo 日期附近观感自然；接真实农历库时只替换本函数即可。
 */
fun lunarLabel(date: LocalDate): String {
    val monthIndex = lunarMonthIndex(date)
    val dayIndex = lunarDayIndex(date)
    return "${LUNAR_MONTH_CN[monthIndex]}${LUNAR_DAY_CN[dayIndex]} · ${ganZhiYear(date.year)}年"
}

/** 锚点：2026-10-10 = 农历九月初一 */
private val LUNAR_ANCHOR: LocalDate = LocalDate.of(2026, 10, 10)
private const val LUNAR_ANCHOR_MONTH_INDEX = 8 // 九月

private fun lunarMonthIndex(date: LocalDate): Int {
    var d = LUNAR_ANCHOR
    var month = LUNAR_ANCHOR_MONTH_INDEX
    // 向前/向后逐「月」走（最多 36 次，成本可忽略，且结果恒等于循环推算）
    while (d.isAfter(date)) {
        month = (month + 11) % 12
        d = d.minusDays(lunarMonthLength(month).toLong())
    }
    while (!d.plusDays(lunarMonthLength(month).toLong()).isAfter(date)) {
        d = d.plusDays(lunarMonthLength(month).toLong())
        month = (month + 1) % 12
    }
    return month
}

private fun lunarDayIndex(date: LocalDate): Int {
    var d = LUNAR_ANCHOR
    var month = LUNAR_ANCHOR_MONTH_INDEX
    while (d.isAfter(date)) {
        month = (month + 11) % 12
        d = d.minusDays(lunarMonthLength(month).toLong())
    }
    while (!d.plusDays(lunarMonthLength(month).toLong()).isAfter(date)) {
        d = d.plusDays(lunarMonthLength(month).toLong())
        month = (month + 1) % 12
    }
    val idx = ChronoUnit.DAYS.between(d, date).toInt()
    return idx.coerceIn(0, 29)
}

/** 大小月（29/30 天）简单的交替规则，只为让推算结果看起来自然 */
private fun lunarMonthLength(monthIndex: Int): Int = if (monthIndex % 2 == 0) 29 else 30

private fun ganZhiYear(year: Int): String {
    val offset = (year - 1984) % 60
    val g = (offset % 10 + 10) % 10
    val z = (offset % 12 + 12) % 12
    return "${GAN[g]}${ZHI[z]}"
}

/** 时钟旁的安静时段小字：清晨 / 上午 / 中午 / 下午 / 傍晚 / 深夜 */
fun zhMeridiem(hour: Int): String = when (hour) {
    in 5..8 -> "清晨"
    in 9..11 -> "上午"
    12 -> "中午"
    in 13..17 -> "下午"
    in 18..22 -> "傍晚"
    else -> "深夜"
}

// ---------------------------------------------------------------------------
// 小工具
// ---------------------------------------------------------------------------

/** 两位补零（不依赖 String.format，避免 locale 差异） */
fun two(n: Int): String = if (n < 10) "0$n" else n.toString()

/**
 * 等宽数字（tabular figures）+ 可选柔和投影。
 * material3 的 Text 没有 fontFeatureSettings / shadow 参数，只能经 TextStyle 传入；
 * 所有时钟与温度数字都过这里，跳数字时绝不左右抖，且在任何背景上都清晰。
 */
@Composable
fun rememberTabularStyle(
    baseSize: TextUnit,
    baseWeight: FontWeight? = null,
    shadow: Shadow? = null,
): TextStyle = remember(baseSize, baseWeight, shadow) {
    TextStyle(
        fontSize = baseSize,
        fontWeight = baseWeight,
        fontFeatureSettings = "tnum",
        shadow = shadow,
    )
}

/** 给大字号白色文字用的柔和投影，比纯黑描边更"安静" */
fun softTextShadow(blur: Float = 26f, dy: Float = 6f): Shadow =
    Shadow(color = Color.Black.copy(alpha = 0.42f), offset = Offset(0f, dy), blurRadius = blur)

/** 颜色线性插值（Compose 的 lerp 不合适时用，保留 alpha） */
fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f,
    )
}

/** 向白色混入，用于玻璃高光 */
fun Color.mixWhite(t: Float): Color = lerpColor(this, Color.White, t)

/**
 * 屏保实际使用的背景：选了 PHOTO 但没有照片时兜底为 AURORA（不崩、不黑屏）。
 */
fun effectiveBackground(
    settings: ScreensaverSettings,
    photos: List<MediaItem>,
): ScreensaverSettings.BackgroundStyle =
    if (settings.background == ScreensaverSettings.BackgroundStyle.PHOTO && photos.isEmpty()) {
        ScreensaverSettings.BackgroundStyle.AURORA
    } else {
        settings.background
    }
