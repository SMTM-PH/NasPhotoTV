package com.nasphoto.tv.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import java.time.LocalDateTime

/**
 * ================= 右栏 · 屏保实时预览（settings-main 重点） =================
 *
 * 一个 16:9 的"小电视"：里面按当前 ScreensaverSettings 实时渲染
 *  - 背景（照片轮播 / 极光 / 纯黑）
 *  - 时钟小样（四种样式各有不同构图，一眼能看出区别）
 *  - 日期 / 照片信息（跟随开关）
 *  - 夜间调暗（整体压暗 + 暖色遮罩）、Ken Burns（照片极缓慢缩放）
 *
 * 刻意不直接嵌 ScreensaverScreen：那个组件是给 1080p 全屏设计的（220sp 时钟、像素位移、退出手势），
 * 缩到 900x506 的小框里用独立的"缩略版"反而更像右侧面板该有的信息密度。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsScreensaverPanel(
    store: SettingsStore,
    focusedKey: SettingsKey?,
    onPickClock: (ScreensaverSettings.ClockStyle) -> Unit,
    onPickBackground: (ScreensaverSettings.BackgroundStyle) -> Unit,
    onPickMode: (ScreensaverSettings.Mode) -> Unit = {},
    onPickSource: (ScreensaverSettings.PhotoSource) -> Unit = {},
    screensaverPhotos: List<MediaItem> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val s = store.screensaver
    val purePhoto = s.mode == ScreensaverSettings.Mode.PHOTO_ONLY
    val photo = screensaverPhotos.firstOrNull()
        ?: MediaCache.screensaverPhotos.firstOrNull()
        ?: MediaCache.recent.firstOrNull()
    val now = remember { LocalDateTime.now() }
    val kenBurnsScale by rememberInfiniteTransition(label = "kb").animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "kbScale",
    )

    // 预览很高：聚焦到「时钟样式 / 背景」时把选择条滚进视野（最小滚动，尽量保留预览）
    val panelScroll = rememberScrollState()
    val stripBringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focusedKey) {
        kotlinx.coroutines.delay(80)
        when (focusedKey) {
            SettingsKey.SS_MODE,
            SettingsKey.SS_SOURCE,
            SettingsKey.SS_ALBUM,
            SettingsKey.SS_CLOCK,
            SettingsKey.SS_BACKGROUND,
            -> runCatching { stripBringIntoView.bringIntoView() }

            else -> panelScroll.animateScrollTo(0)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(panelScroll),
    ) {
        PanelTitle("屏保预览", if (s.enabled) "待机时所见即所得" else "屏保已关闭 · 预览仍可查看")
        Spacer(Modifier.height(TvDimen.SpaceM))

        // ---------------- 预览框 ----------------
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(TvDimen.RadiusL))
                .background(Color.Black)
                .border(1.5.dp, TvColor.Divider, RoundedCornerShape(TvDimen.RadiusL)),
        ) {
            if (purePhoto) {
                // 纯照片：全屏照片本身，不压暗、不叠加任何文字
                if (photo != null) {
                    if (s.kenBurns) {
                        MediaImage(
                            url = photo.thumbnailUrl.ifBlank { photo.url },
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = kenBurnsScale
                                    scaleY = kenBurnsScale
                                },
                            targetWidth = 1080,
                        )
                    } else {
                        MediaImage(
                            url = photo.thumbnailUrl.ifBlank { photo.url },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            targetWidth = 1080,
                        )
                    }
                } else {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "当前来源暂无照片",
                            fontSize = TvType.Caption,
                            color = TvColor.TextTertiary,
                        )
                    }
                }
            } else {
                // 背景层
                when (s.background) {
                    ScreensaverSettings.BackgroundStyle.PHOTO -> {
                        if (s.kenBurns) {
                            MediaImage(
                                url = photo?.thumbnailUrl.orEmpty(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = kenBurnsScale
                                        scaleY = kenBurnsScale
                                    },
                                targetWidth = 1080,
                            )
                        } else {
                            MediaImage(
                                url = photo?.thumbnailUrl.orEmpty(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                targetWidth = 1080,
                            )
                        }
                        // 压暗，保证白色时钟可读
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.55f),
                                            Color.Black.copy(alpha = 0.28f),
                                            Color.Black.copy(alpha = 0.62f),
                                        ),
                                    ),
                                ),
                        )
                    }

                    ScreensaverSettings.BackgroundStyle.AURORA -> AuroraPreview(Modifier.fillMaxSize())

                    ScreensaverSettings.BackgroundStyle.BLACK -> Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                    )
                }

                // 夜间调暗：整体压暗 + 一点点暖色
                if (s.nightDim) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x33FF8A3D)),
                    )
                }

                // 时钟层：五种样式各自构图
                AnimatedContent(
                    targetState = s.clockStyle,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    modifier = Modifier.fillMaxSize(),
                    label = "clockPreview",
                ) { style ->
                    ClockThumb(style = style, now = now, showDate = s.showDate)
                }
            }

            // 关闭态：在上面盖一层灰 + 文案
            if (!s.enabled) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "屏保已关闭",
                        fontSize = TvType.CardTitle,
                        fontWeight = FontWeight.Medium,
                        color = TvColor.TextPrimary.copy(alpha = 0.85f),
                    )
                }
            }
        }

        Spacer(Modifier.height(TvDimen.SpaceM))

        // ---------------- 一眼可点的选择条（紧贴预览） ----------------
        // 只在关注对应行时出现，避免右栏永远满满当当
        Box(Modifier.bringIntoViewRequester(stripBringIntoView)) {
            when (focusedKey) {
                SettingsKey.SS_MODE -> ChoiceStrip(
                    title = "屏保画面",
                    items = ScreensaverSettings.Mode.entries.map { it.label },
                    selectedIndex = s.mode.ordinal,
                    onPick = { idx -> onPickMode(ScreensaverSettings.Mode.entries[idx]) },
                )

                SettingsKey.SS_SOURCE -> ChoiceStrip(
                    title = "轮播内容",
                    items = ScreensaverSettings.PhotoSource.entries.map { it.label },
                    selectedIndex = s.photoSource.ordinal,
                    onPick = { idx -> onPickSource(ScreensaverSettings.PhotoSource.entries[idx]) },
                )

                SettingsKey.SS_ALBUM -> Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("相册", fontSize = TvType.Tiny, color = TvColor.TextTertiary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${MediaCache.albums.size} 个 · 在左栏按 ← → 切换",
                            fontSize = TvType.Tiny,
                            color = TvColor.TextTertiary.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.height(TvDimen.SpaceXs))
                    Text(
                        text = s.albumName.ifBlank {
                            if (MediaCache.albums.isEmpty()) "暂无相册" else "未选择"
                        },
                        fontSize = TvType.CardTitle,
                        fontWeight = FontWeight.Medium,
                        color = TvColor.TextPrimary,
                    )
                    if (s.photoSource != ScreensaverSettings.PhotoSource.ALBUM) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "把「轮播内容」切到「相册」后生效",
                            fontSize = TvType.Tiny,
                            color = TvColor.TextTertiary.copy(alpha = 0.8f),
                        )
                    }
                }

                SettingsKey.SS_CLOCK -> if (purePhoto) {
                    PanelNote("纯照片模式下不显示时钟")
                } else {
                    ChoiceStrip(
                        title = "时钟样式",
                        items = ScreensaverSettings.ClockStyle.entries.map { it.label },
                        selectedIndex = s.clockStyle.ordinal,
                        onPick = { idx -> onPickClock(ScreensaverSettings.ClockStyle.entries[idx]) },
                    )
                }

                SettingsKey.SS_BACKGROUND -> if (purePhoto) {
                    PanelNote("纯照片模式固定使用全屏照片")
                } else {
                    ChoiceStrip(
                        title = "背景",
                        items = ScreensaverSettings.BackgroundStyle.entries.map { it.label },
                        selectedIndex = s.background.ordinal,
                        onPick = { idx -> onPickBackground(ScreensaverSettings.BackgroundStyle.entries[idx]) },
                    )
                }

                else -> {}
            }
        }

        Spacer(Modifier.height(TvDimen.SpaceM))

        // ---------------- 快捷参数（遥控器也能直接点） ----------------
        InfoLine("画面", s.mode.label)
        InfoLine(
            "轮播",
            if (s.photoSource == ScreensaverSettings.PhotoSource.ALBUM) {
                "相册 · ${s.albumName.ifBlank { "未选择" }}"
            } else {
                s.photoSource.label
            },
        )
        if (!purePhoto) InfoLine("时钟", s.clockStyle.label)
        InfoLine("背景", if (purePhoto) "全屏照片" else s.background.label)
        InfoLine("停留", "${SettingsStore.formatInterval(s.photoIntervalSec)} / 张")
        InfoLine("进入", if (s.enabled) "静止 ${SettingsStore.formatIdleTimeout(s.idleTimeoutSec)}后" else "不会进入")
        InfoLine(
            "叠加",
            if (purePhoto) {
                "无（纯照片）"
            } else {
                buildList {
                    if (s.showDate) add("日期")
                    if (s.nightDim) add("夜间调暗")
                    if (s.kenBurns) add("Ken Burns")
                }.joinToString(" · ").ifEmpty { "无" }
            },
        )

        if (focusedKey == SettingsKey.SS_PREVIEW || focusedKey == SettingsKey.SS_ENABLED) {
            Spacer(Modifier.height(TvDimen.SpaceS))
            Text(
                text = "ENTER 全屏预览 · 屏保里 ← → 换样式，其他键退出",
                fontSize = TvType.Tiny,
                color = TvColor.Accent.copy(alpha = 0.85f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TvColor.Accent.copy(alpha = 0.12f))
                    .padding(horizontal = TvDimen.SpaceM, vertical = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 时钟小样：五种样式，在这个小框里各自保留"识别度"
// ---------------------------------------------------------------------------

@Composable
private fun ClockThumb(
    style: ScreensaverSettings.ClockStyle,
    now: LocalDateTime,
    showDate: Boolean,
) {
    val hhmm = two(now.hour) + ":" + two(now.minute)
    when (style) {
        ScreensaverSettings.ClockStyle.CINEMA -> Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(TvDimen.SpaceL)) {
                Text(
                    text = hhmm.substring(0, 2),
                    fontSize = 54.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Thin,
                    color = Color.White.copy(alpha = 0.94f),
                )
                Text(
                    text = hhmm.substring(3, 5),
                    fontSize = 54.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Thin,
                    color = Color.White.copy(alpha = 0.94f),
                )
                if (showDate) {
                    Text(
                        text = "${now.monthValue}月${now.dayOfMonth}日 · 星期${weekdayCn(now)}",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.60f),
                    )
                }
            }
            // 右上角竖排地点小样
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = TvDimen.SpaceM),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 1.dp, height = 12.dp)
                        .background(Color.White.copy(alpha = 0.35f)),
                )
                Text("东", fontSize = 9.sp, color = Color.White.copy(alpha = 0.74f))
                Text("京", fontSize = 9.sp, color = Color.White.copy(alpha = 0.74f))
            }
        }

        ScreensaverSettings.ClockStyle.FLIP -> Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = TvDimen.SpaceL),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showDate) {
                    Text(
                        text = "星期${weekdayCn(now)} · ${now.monthValue}月${now.dayOfMonth}日",
                        fontSize = TvType.Tiny,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                // 翻页片：两个数字块 + 中间冒号
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlipTile(hhmm.substring(0, 2))
                    Text(
                        text = ":",
                        fontSize = 54.sp,
                        color = TvColor.Accent.copy(alpha = if (now.second % 2 == 0) 0.95f else 0.35f),
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    FlipTile(hhmm.substring(3, 5))
                }
            }
        }

        ScreensaverSettings.ClockStyle.THIN -> Column(
            Modifier
                .fillMaxSize()
                .padding(TvDimen.SpaceL),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            if (showDate) {
                Text(
                    text = "星期${weekdayCn(now)} · ${now.monthValue}月${now.dayOfMonth}日",
                    fontSize = TvType.Caption,
                    color = Color.White.copy(alpha = 0.66f),
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = hhmm,
                fontSize = 76.sp,
                fontWeight = FontWeight.Thin,
                color = Color.White.copy(alpha = 0.94f),
                letterSpacing = 2.sp,
            )
        }

        ScreensaverSettings.ClockStyle.MINIMAL -> Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(TvDimen.SpaceL)) {
                Text(
                    text = "星期${weekdayCn(now)}",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White.copy(alpha = 0.92f),
                    letterSpacing = 4.sp,
                )
                if (showDate) {
                    Text(
                        text = "${now.monthValue}月${now.dayOfMonth}日",
                        fontSize = TvType.Caption,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
            Text(
                text = hhmm,
                fontSize = TvType.CardTitle,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = TvDimen.SpaceM),
            )
        }

        ScreensaverSettings.ClockStyle.DATE_FOCUS -> Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = TvDimen.SpaceL),
            ) {
                Text(
                    text = "星期${weekdayCn(now)}",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White.copy(alpha = 0.94f),
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "${now.monthValue}月${now.dayOfMonth}日",
                    fontSize = TvType.Caption,
                    color = Color.White.copy(alpha = 0.62f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hhmm,
                    fontSize = TvType.CardTitle,
                    color = TvColor.Accent.copy(alpha = 0.92f),
                    letterSpacing = 2.sp,
                )
            }
            MiniMonth(
                year = now.year,
                month = now.monthValue,
                today = now.dayOfMonth,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = TvDimen.SpaceL),
            )
        }
    }
}

/** 翻页数字：上下两半带高光，模仿机械翻页片 */
@Composable
private fun FlipTile(text: String) {
    Box(
        Modifier
            .size(width = 68.dp, height = 86.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.14f),
                        0.49f to Color.Transparent,
                        0.51f to Color.Black.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 54.sp,
            fontWeight = FontWeight.ExtraLight,
            color = Color.White.copy(alpha = 0.94f),
        )
    }
}

/** 迷你月历（DATE_FOCUS 样式的小样） */
@Composable
private fun MiniMonth(year: Int, month: Int, today: Int, modifier: Modifier = Modifier) {
    val first = java.time.LocalDate.of(year, month, 1)
    val leading = first.dayOfWeek.value - 1
    val days = first.lengthOfMonth()
    val cells: List<Int?> = buildList {
        repeat(leading) { add(null) }
        for (d in 1..days) add(d)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(TvDimen.RadiusM))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(10.dp),
    ) {
        days.let { _ ->
            cells.chunked(7).take(4).forEach { week ->
                Row {
                    week.forEach { d ->
                        Box(
                            Modifier.size(18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (d != null) {
                                Text(
                                    text = d.toString(),
                                    fontSize = 9.sp,
                                    color = if (d == today) TvColor.Accent else Color.White.copy(alpha = 0.55f),
                                    fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 小件
// ---------------------------------------------------------------------------

/** 极光背景：几条缓慢漂移的渐变光带 */
@Composable
private fun AuroraPreview(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "auroraPhase",
    )
    Box(
        modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF04121A), Color(0xFF071B24), Color(0xFF050A12)),
                ),
            )
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(TvColor.Accent.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(w * (0.20f + 0.10f * t), h * 0.28f),
                        radius = w * 0.42f,
                    ),
                    radius = w * 0.42f,
                    center = Offset(w * (0.20f + 0.10f * t), h * 0.28f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(TvColor.AccentAlt.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(w * (0.78f - 0.08f * t), h * 0.34f),
                        radius = w * 0.36f,
                    ),
                    radius = w * 0.36f,
                    center = Offset(w * (0.78f - 0.08f * t), h * 0.34f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF8E7CFF).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(w * (0.52f + 0.06f * t), h * 0.78f),
                        radius = w * 0.30f,
                    ),
                    radius = w * 0.30f,
                    center = Offset(w * (0.52f + 0.06f * t), h * 0.78f),
                )
            },
    )
}

/** 右栏里的一行说明（当前模式下该项不适用时使用） */
@Composable
private fun PanelNote(text: String) {
    Text(
        text = text,
        fontSize = TvType.Tiny,
        color = TvColor.TextTertiary,
        modifier = Modifier
            .clip(CircleShape)
            .background(TvColor.SurfaceHigh.copy(alpha = 0.35f))
            .padding(horizontal = TvDimen.SpaceM, vertical = 6.dp),
    )
}

/** 右栏里的一组可选胶囊（点了直接切换，也可用左右键在左栏切） */
@Composable
private fun ChoiceStrip(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = TvType.Tiny, color = TvColor.TextTertiary)
            Spacer(Modifier.weight(1f))
            Text("在左栏按 ← → 也能切换", fontSize = TvType.Tiny, color = TvColor.TextTertiary.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(TvDimen.SpaceXs))
        Row(horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs)) {
            items.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) TvColor.Accent.copy(alpha = 0.18f)
                            else TvColor.SurfaceHigh.copy(alpha = 0.45f),
                        )
                        .border(
                            width = if (selected) 1.5.dp else 0.dp,
                            color = if (selected) TvColor.Accent else Color.Transparent,
                            shape = CircleShape,
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = label,
                        fontSize = TvType.Tiny,
                        color = if (selected) TvColor.Accent else TvColor.TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** 右栏面板的标题（所有面板共用） */
@Composable
fun PanelTitle(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            fontSize = TvType.Section,
            fontWeight = FontWeight.SemiBold,
            color = TvColor.TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = subtitle, fontSize = TvType.Tiny, color = TvColor.TextTertiary)
    }
}

/** 右栏信息行（标签 + 值） */
@Composable
fun InfoLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = TvType.Tiny,
            color = TvColor.TextTertiary,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            fontSize = TvType.Caption,
            color = TvColor.TextSecondary,
            textAlign = TextAlign.Start,
            maxLines = 1,
        )
    }
}

/** 两位补零（不用 String.format，避免 locale 差异） */
private fun two(n: Int): String = if (n < 10) "0$n" else n.toString()

private fun weekdayCn(now: LocalDateTime): String =
    listOf("一", "二", "三", "四", "五", "六", "日")[now.dayOfWeek.value - 1]
