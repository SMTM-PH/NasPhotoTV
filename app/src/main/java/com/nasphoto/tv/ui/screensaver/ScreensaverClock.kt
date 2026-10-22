package com.nasphoto.tv.ui.screensaver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset as GeoOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * ================= 屏保的五种时钟样式（对标 iPhone StandBy / Apple TV 屏保） =================
 *
 * 统一约定：
 *  - 时间白色 90%（[TimeWhite]，防烧屏：不用纯白）
 *  - 数字一律 tabular 等宽（fontFeatureSettings "tnum"），跳字不抖
 *  - 只有分钟变化 / 秒变化的那一处 Text 会重组（见 [ClockParts]），常驻时几乎零重组
 *  - 卡片用 [GlassCard] 玻璃拟态；不用真实背景模糊（电视芯片代价高）
 */

/** 时钟主色：白色 90% */
val TimeWhite = TvColor.TextPrimary.copy(alpha = 0.90f)

/** 大数字共用字形：等宽 + 紧字距 + 投影，保证任何背景上都清晰 */
private fun bigTimeStyle(size: TextUnit, weight: FontWeight): TextStyle = TextStyle(
    fontSize = size,
    fontWeight = weight,
    color = TimeWhite,
    letterSpacing = (-4).sp,
    lineHeight = size * 1.05f,
    textAlign = TextAlign.Center,
    fontFeatureSettings = "tnum",
    shadow = Shadow(Color.Black.copy(alpha = 0.45f), GeoOffset(0f, 6f), 26f),
)

/**
 * 入口：按样式分发。四种样式共用同一份 [ClockParts]，切换样式时时间无缝衔接。
 */
@Composable
fun ScreensaverClock(
    style: ScreensaverSettings.ClockStyle,
    now: LocalDateTime,
    parts: ClockParts,
    showDate: Boolean,
    modifier: Modifier = Modifier,
) {
    when (style) {
        ScreensaverSettings.ClockStyle.CINEMA ->
            CinemaClock(now, parts, showDate, variant = 0, modifier = modifier)
        ScreensaverSettings.ClockStyle.FLIP -> FlipClock(parts, now, showDate, modifier)
        ScreensaverSettings.ClockStyle.THIN -> ThinClock(parts, now, showDate, modifier)
        ScreensaverSettings.ClockStyle.MINIMAL -> MinimalClock(now, showDate, modifier)
        ScreensaverSettings.ClockStyle.DATE_FOCUS -> DateFocusClock(parts, now, showDate, modifier)
    }
}

// ---------------------------------------------------------------------------
// 1. FLIP 翻页数字：220sp 超大数字 + 每秒呼吸冒号 + 数字从上滚入
// ---------------------------------------------------------------------------

@Composable
private fun FlipClock(parts: ClockParts, now: LocalDateTime, showDate: Boolean, modifier: Modifier) {
    // 冒号以秒为单位明暗呼吸（1s 一周期，缓动而非硬切，观感克制）
    val colonAlpha by animateFloatAsState(
        targetValue = if (parts.second % 2 == 0) 0.95f else 0.20f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "flipColon",
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (showDate) {
            QuietDateLine(now, Modifier.padding(bottom = TvDimen.SpaceM))
        }

        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.Center) {
            FlipDigitGroup(parts.hour, target = parts.hmKey)
            Box(
                Modifier
                    .width(72.dp)
                    .height(230.dp)
                    .graphicsLayer { alpha = colonAlpha },
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(54.dp)) {
                    ColonDot()
                    ColonDot()
                }
            }
            FlipDigitGroup(parts.minute, target = parts.hmKey)
        }

        if (showDate) {
            Spacer(Modifier.height(TvDimen.SpaceM))
            SecondaryLine(primary = "第 ${now.dayOfYear} 天", secondary = zhWeekday(now.toLocalDate()))
        }
    }
}

@Composable
private fun ColonDot() {
    Box(
        Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(TvColor.Accent.copy(alpha = 0.92f)),
    )
}

/**
 * 两位数字的翻页：AnimatedContent 的 target 是「时:分」，
 * 只有分钟真的变了才播动画（秒变化不会穿过这个重组边界）。
 */
@Composable
private fun FlipDigitGroup(text: String, target: String) {
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            // 新数字自上方滑入 + 淡入；旧数字向下淡出 —— 机械翻页的物理感
            (slideInVertically(tween(520, easing = FastOutSlowInEasing)) { h -> (-h * 0.55f).toInt() } +
                fadeIn(tween(340))) togetherWith
                (slideOutVertically(tween(420, easing = FastOutSlowInEasing)) { h -> (h * 0.30f).toInt() } +
                    fadeOut(tween(260)))
        },
        label = "flipDigits",
    ) { _ ->
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = bigTimeStyle(TvType.ClockHuge, FontWeight.ExtraLight),
                modifier = Modifier.drawBehind {
                    // 数字上半微亮、下半微暗，像翻页片的金属反光
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.10f),
                            0.48f to Color.Transparent,
                            0.52f to Color.Black.copy(alpha = 0.10f),
                            1f to Color.Transparent,
                        ),
                    )
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2. THIN 纤细极简：120sp 细体数字 + 上方 30sp 日期行
// ---------------------------------------------------------------------------

@Composable
private fun ThinClock(parts: ClockParts, now: LocalDateTime, showDate: Boolean, modifier: Modifier) {
    val date = now.toLocalDate()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (showDate) {
            Text(
                text = "${zhMonthDay(date)} · ${zhWeekday(date)}",
                fontSize = TvType.Section,
                fontWeight = FontWeight.Light,
                color = TvColor.TextPrimary.copy(alpha = 0.72f),
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(TvDimen.SpaceL))
        }

        // 极细分隔短线：衬托数字，是"极简"的气质来源
        Box(
            Modifier
                .width(88.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                    ),
                ),
        )
        Spacer(Modifier.height(TvDimen.SpaceM))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            ThinDigit(parts.hour)
            Text(
                text = ":",
                fontSize = TvType.ClockLarge,
                fontWeight = FontWeight.Thin,
                color = TimeWhite.copy(alpha = 0.50f),
                style = rememberTabularStyle(TvType.ClockLarge),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            ThinDigit(parts.minute)
        }

        Spacer(Modifier.height(TvDimen.SpaceM))
        Text(
            text = "${zhMeridiem(now.hour)} · ${two(now.second)} 秒",
            fontSize = TvType.Caption,
            color = TvColor.TextSecondary.copy(alpha = 0.52f),
            letterSpacing = 3.sp,
        )
    }
}

@Composable
private fun ThinDigit(text: String) {
    Text(text = text, style = bigTimeStyle(TvType.ClockLarge, FontWeight.Thin))
}

// ---------------------------------------------------------------------------
// 3. MINIMAL 极简小钟：右上角小号时间 + 主视觉大字日期
// ---------------------------------------------------------------------------

/** 右上角小号时间（MINIMAL 样式：由 ScreensaverScreen 摆到画面右上角） */
@Composable
fun MinimalClock(now: LocalDateTime, showDate: Boolean, modifier: Modifier) {
    val date = now.toLocalDate()
    Column(modifier, horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlinkDot()
            Spacer(Modifier.width(TvDimen.SpaceS))
            // 直接读 now：只有跨秒时才会重组这一个 Text，代价可忽略
            Text(
                text = "${two(now.hour)}:${two(now.minute)}",
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                color = TimeWhite,
                letterSpacing = (-1).sp,
                style = rememberTabularStyle(44.sp, shadow = softTextShadow(18f, 3f)),
            )
        }
        if (showDate) {
            Spacer(Modifier.height(TvDimen.SpaceXs))
            Text(
                text = "${zhFullDate(date)}  ${zhWeekday(date)}",
                fontSize = TvType.Caption,
                color = TvColor.TextSecondary.copy(alpha = 0.60f),
                letterSpacing = 2.sp,
            )
        }
    }
}

/** 秒点：每秒缓慢呼吸，不闪 */
@Composable
private fun BlinkDot() {
    val transition = rememberInfiniteTransition(label = "minimalDot")
    val a by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "minimalDotAlpha",
    )
    Box(
        Modifier
            .size(9.dp)
            .graphicsLayer { alpha = a }
            .clip(RoundedCornerShape(percent = 50))
            .background(TvColor.Accent),
    )
}

/**
 * MINIMAL 的主视觉：竖排大字（星期三 / 10月19日），信息最少、最耐看。
 * 对外公开：MINIMAL 样式下由 ScreensaverScreen 摆在画面中央偏左，与小钟形成不对称构图。
 */
@Composable
fun MinimalDateHero(now: LocalDateTime, modifier: Modifier = Modifier) {
    val date = now.toLocalDate()
    Column(modifier) {
        Text(
            text = zhWeekday(date),
            fontSize = 96.sp,
            fontWeight = FontWeight.Light,
            color = TimeWhite,
            letterSpacing = 10.sp,
            style = rememberTabularStyle(96.sp, shadow = softTextShadow(30f, 6f)),
        )
        Spacer(Modifier.height(TvDimen.SpaceM))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = zhMonthDay(date),
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraLight,
                color = TvColor.TextPrimary.copy(alpha = 0.78f),
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.width(TvDimen.SpaceM))
            Text(
                text = lunarLabel(date),
                fontSize = TvType.Caption,
                color = TvColor.TextSecondary.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 4. DATE_FOCUS 日期日历：大号「星期五」+「10月19日」+ 小号时间 + 月历卡
// ---------------------------------------------------------------------------

@Composable
private fun DateFocusClock(parts: ClockParts, now: LocalDateTime, showDate: Boolean, modifier: Modifier) {
    val date = now.toLocalDate()

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                text = zhWeekday(date),
                fontSize = 168.sp,
                fontWeight = FontWeight.Light,
                color = TimeWhite,
                lineHeight = 176.sp,
                letterSpacing = 4.sp,
                style = rememberTabularStyle(168.sp, shadow = softTextShadow(34f, 8f)),
            )
            Spacer(Modifier.height(TvDimen.SpaceS))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = zhMonthDay(date),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraLight,
                    color = TvColor.TextPrimary.copy(alpha = 0.82f),
                    letterSpacing = 2.sp,
                )
                if (showDate) {
                    Spacer(Modifier.width(TvDimen.SpaceM))
                    Text(
                        text = lunarLabel(date),
                        fontSize = TvType.Body,
                        color = TvColor.TextSecondary.copy(alpha = 0.60f),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(TvDimen.SpaceL))
            // 小号时间：只有跨分钟才重组
            Text(
                text = parts.hmKey,
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
                color = TvColor.Accent.copy(alpha = 0.92f),
                letterSpacing = 3.sp,
                style = rememberTabularStyle(46.sp),
            )
        }

        Spacer(Modifier.width(TvDimen.SpaceXxl))
        CalendarPanel(date = date, modifier = Modifier.width(368.dp))
    }
}

/** 迷你月历：7 列，今天用青色圈点高亮；固定尺寸，切月不抖动 */
@Composable
private fun CalendarPanel(date: LocalDate, modifier: Modifier = Modifier) {
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val cells: List<Int?> = remember(date.year, date.monthValue) {
        val leading = date.withDayOfMonth(1).dayOfWeek.value - 1 // 周一为首列
        buildList {
            repeat(leading) { add(null) }
            for (d in 1..date.lengthOfMonth()) add(d)
            while (size % 7 != 0) add(null)
        }
    }

    GlassCard(modifier) {
        Column(Modifier.padding(TvDimen.SpaceL), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${date.year}年",
                    fontSize = TvType.Caption,
                    color = TvColor.TextSecondary.copy(alpha = 0.70f),
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "${date.monthValue}月",
                    fontSize = TvType.CardTitle,
                    fontWeight = FontWeight.Medium,
                    color = TvColor.TextPrimary.copy(alpha = 0.90f),
                )
            }
            Spacer(Modifier.height(TvDimen.SpaceM))

            Row(Modifier, horizontalArrangement = Arrangement.SpaceBetween) {
                weekLabels.forEach { label ->
                    Box(Modifier.width(42.dp), contentAlignment = Alignment.Center) {
                        Text(text = label, fontSize = TvType.Tiny, color = TvColor.TextTertiary)
                    }
                }
            }
            Spacer(Modifier.height(TvDimen.SpaceXs))

            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { day -> DayCell(day = day, today = day == date.dayOfMonth) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(day: Int?, today: Boolean) {
    Box(Modifier.width(42.dp).height(38.dp), contentAlignment = Alignment.Center) {
        if (day == null) return@Box
        if (today) {
            Box(
                Modifier
                    .drawBehind {
                        drawCircle(
                            color = TvColor.Accent.copy(alpha = 0.18f),
                            radius = size.minDimension / 2f,
                        )
                        drawCircle(
                            color = TvColor.Accent.copy(alpha = 0.60f),
                            radius = size.minDimension / 2f - 1.dp.toPx(),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                    .size(34.dp),
            )
        }
        Text(
            text = day.toString(),
            fontSize = TvType.Caption,
            fontWeight = if (today) FontWeight.SemiBold else FontWeight.Normal,
            color = if (today) TvColor.Accent else TvColor.TextSecondary.copy(alpha = 0.64f),
        )
    }
}

// ---------------------------------------------------------------------------
// 5. CINEMA 巨幕时钟：对标 Apple TV 航拍屏保 / iPhone 待机显示
// ---------------------------------------------------------------------------

/** 堆叠构图两行数字的字号（行高压到 0.8，两行才咬得紧） */
private val CinemaStackedSize = 210.sp
private val CinemaStackedLine = 168.sp

/** 整行构图的字号：铺满画面中段，细到几乎发光 */
private val CinemaInlineSize = 260.sp

/**
 * 巨幕时钟：构图随照片轮换（Apple TV 屏保也会为防烧屏移动时钟位置）。
 *
 *  - variant 偶数：时 / 分上下堆叠在左上（参考图 1）；
 *  - variant 奇数：整行巨号时间居中，日期在下方（参考图 2）。
 *
 * 屏保只显示时间与日期 —— 照片的标题、地点、尺寸都属于图片信息，一律不上屏。
 */
@Composable
fun CinemaClock(
    now: LocalDateTime,
    parts: ClockParts,
    showDate: Boolean,
    variant: Int,
    modifier: Modifier = Modifier,
) {
    val date = now.toLocalDate()
    val dateLine = "${zhMonthDay(date)} · ${zhWeekday(date)}"

    Box(modifier.fillMaxSize()) {
        // 换照片时两种构图交叉淡入，和背景 Crossfade 同节奏，不"跳"
        Crossfade(
            targetState = variant % 2 == 0,
            animationSpec = tween(900, easing = FastOutSlowInEasing),
            label = "cinemaLayout",
        ) { stacked ->
            Box(Modifier.fillMaxSize()) {
                if (stacked) {
                    Column(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = TvDimen.ScreenPadding, top = 24.dp),
                    ) {
                        Text(text = parts.hour, style = cinemaStackedStyle())
                        Text(text = parts.minute, style = cinemaStackedStyle())
                        if (showDate) {
                            Spacer(Modifier.height(TvDimen.SpaceM))
                            CinemaCaption(dateLine)
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = parts.hour, style = cinemaInlineStyle())
                            Text(
                                text = ":",
                                style = cinemaInlineStyle().copy(color = TimeWhite.copy(alpha = 0.58f)),
                            )
                            Text(text = parts.minute, style = cinemaInlineStyle())
                        }
                        if (showDate) {
                            Spacer(Modifier.height(TvDimen.SpaceXs))
                            CinemaCaption(dateLine)
                        }
                    }
                }
            }
        }
    }
}

/** 数字共用字形：极细 + 紧行高 + 等宽 + 柔和投影 */
private fun cinemaStackedStyle(): TextStyle = TextStyle(
    fontSize = CinemaStackedSize,
    fontWeight = FontWeight.Thin,
    color = TimeWhite.copy(alpha = 0.94f),
    letterSpacing = (-6).sp,
    lineHeight = CinemaStackedLine,
    fontFeatureSettings = "tnum",
    shadow = softTextShadow(46f, 10f),
)

private fun cinemaInlineStyle(): TextStyle = TextStyle(
    fontSize = CinemaInlineSize,
    fontWeight = FontWeight.Thin,
    color = TimeWhite.copy(alpha = 0.94f),
    letterSpacing = (-8).sp,
    lineHeight = CinemaInlineSize,
    fontFeatureSettings = "tnum",
    shadow = softTextShadow(52f, 12f),
)

/** 时间下方的小字日期：宽字距、低存在感 */
@Composable
private fun CinemaCaption(text: String) {
    Text(
        text = text,
        fontSize = TvType.Body,
        fontWeight = FontWeight.Light,
        color = Color.White.copy(alpha = 0.72f),
        letterSpacing = 4.sp,
        style = rememberTabularStyle(TvType.Body, shadow = softTextShadow(18f, 3f)),
    )
}

// ---------------------------------------------------------------------------
// 共用小件
// ---------------------------------------------------------------------------

/** 一行很淡的日期（FLIP 顶部）：「星期五 · 九月初五 · 丙午年」 */
@Composable
private fun QuietDateLine(now: LocalDateTime, modifier: Modifier = Modifier) {
    val date = now.toLocalDate()
    Text(
        text = "${zhWeekday(date)} · ${lunarLabel(date)}",
        fontSize = TvType.Section,
        fontWeight = FontWeight.ExtraLight,
        color = TvColor.TextPrimary.copy(alpha = 0.62f),
        letterSpacing = 6.sp,
        modifier = modifier,
    )
}

/** 主行 · 副行（FLIP 底部） */
@Composable
private fun SecondaryLine(primary: String, secondary: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(primary, fontSize = TvType.Body, color = TvColor.TextSecondary.copy(alpha = 0.56f))
        Box(
            Modifier
                .padding(horizontal = TvDimen.SpaceS)
                .size(4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(TvColor.TextTertiary.copy(alpha = 0.70f)),
        )
        Text(secondary, fontSize = TvType.Body, color = TvColor.TextSecondary.copy(alpha = 0.56f))
    }
}

/**
 * 玻璃拟态卡片（屏保共用）：半透明白 + 顶部渐变 + 1px 高光描边。
 * 不用 Modifier.blur 做真实背景模糊（电视芯片代价高），多层半透明叠出玻璃感即可。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadiusDp: Float = 28f,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.055f))
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    0.5f to Color.White.copy(alpha = 0.03f),
                    1f to Color.Black.copy(alpha = 0.10f),
                ),
            )
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.13f),
                    cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx()),
                    style = Stroke(width = 1.6f),
                )
            },
    ) {
        content()
    }
}

/** 进入屏保时的落位动画：0.97 → 1.0 + 淡入，900ms，克制 */
@Composable
fun EntranceFade(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "entrance",
    )
    Box(
        modifier.graphicsLayer {
            val s = 0.97f + 0.03f * progress
            scaleX = s
            scaleY = s
            alpha = progress
            transformOrigin = TransformOrigin(0.5f, 0.5f)
        },
    ) { content() }
}
