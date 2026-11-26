/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasphoto.tv.R
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.screensaver.ScreensaverPhotoWall
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import java.time.LocalDateTime

/** 展示当前屏保画面，调整选项时保持预览可见。 */
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
    val showsClock = s.mode == ScreensaverSettings.Mode.CLOCK
    val photoWall = s.mode == ScreensaverSettings.Mode.PHOTO_WALL
    val choosingOption = focusedKey == SettingsKey.SS_MODE ||
        focusedKey == SettingsKey.SS_SOURCE || focusedKey == SettingsKey.SS_ALBUM ||
        focusedKey == SettingsKey.SS_CLOCK || focusedKey == SettingsKey.SS_BACKGROUND
    val showPushIn = focusedKey == SettingsKey.SS_KEN_BURNS && !photoWall
    // 外壳传入已按当前账号白名单过滤的照片；不能回退到最近照片或未过滤缓存。
    val previewPhotos = if (store.displayAlbumIds.isEmpty()) emptyList() else screensaverPhotos
    val photo = previewPhotos.firstOrNull()
    val displayAlbums = currentDisplayAlbums(store).filter { it.id in store.displayAlbumIds }
    val now = remember { LocalDateTime.now() }
    val panelScroll = rememberScrollState()
    LaunchedEffect(focusedKey) { panelScroll.scrollTo(0) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(panelScroll),
    ) {
        when (focusedKey) {
            SettingsKey.SS_MODE -> ChoiceStrip(
                title = "屏保画面",
                items = ScreensaverSettings.Mode.entries.map { it.label },
                selectedIndex = s.mode.ordinal,
                onPick = { index -> onPickMode(ScreensaverSettings.Mode.entries[index]) },
            )
            SettingsKey.SS_SOURCE -> ChoiceStrip(
                title = "轮播内容",
                items = ScreensaverSettings.PhotoSource.entries.map { it.label },
                selectedIndex = s.photoSource.ordinal,
                onPick = { index -> onPickSource(ScreensaverSettings.PhotoSource.entries[index]) },
            )
            SettingsKey.SS_ALBUM -> PanelTitle(
                "轮播相册",
                displayAlbums.firstOrNull { it.id == s.albumId }?.name ?: "请选择客厅展示相册",
            )
            SettingsKey.SS_CLOCK -> if (showsClock) {
                ChoiceStrip(
                    title = "时钟样式",
                    items = ScreensaverSettings.ClockStyle.entries.map { it.label },
                    selectedIndex = s.clockStyle.ordinal,
                    onPick = { index -> onPickClock(ScreensaverSettings.ClockStyle.entries[index]) },
                )
            } else {
                PanelNote("切到时钟待机后可调整时钟样式")
            }
            SettingsKey.SS_BACKGROUND -> if (showsClock) {
                ChoiceStrip(
                    title = "背景",
                    items = ScreensaverSettings.BackgroundStyle.entries.map { it.label },
                    selectedIndex = s.background.ordinal,
                    onPick = { index -> onPickBackground(ScreensaverSettings.BackgroundStyle.entries[index]) },
                )
            } else {
                PanelNote("切到时钟待机后可调整背景")
            }
            else -> if (showPushIn) {
                PanelTitle("照片推拉预览", if (s.kenBurns) "已开启 · 示例照片" else "已关闭 · 示例照片")
            } else {
                PanelTitle("屏保预览", if (s.enabled) s.mode.label else "${s.mode.label} · 自动屏保已关闭")
            }
        }
        Spacer(Modifier.height(TvDimen.SpaceS))

        // ---------------- 预览框 ----------------
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(if (choosingOption) 0.84f else 1f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(TvDimen.RadiusL))
                .background(Color.Black)
                .border(1.5.dp, TvColor.Divider, RoundedCornerShape(TvDimen.RadiusL)),
        ) {
            if (showPushIn) {
                PhotoPushInPreview(enabled = s.kenBurns)
            } else if (!showsClock) {
                when {
                    photo == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (store.displayAlbumIds.isEmpty()) {
                                "请先选择客厅展示相册"
                            } else {
                                "展示相册暂无可用照片"
                            },
                            fontSize = TvType.Caption,
                            color = TvColor.TextTertiary,
                        )
                    }

                    photoWall -> ScreensaverPhotoWall(previewPhotos, index = 0, modifier = Modifier.fillMaxSize())

                    else -> {
                        val scale = if (s.kenBurns) KEN_BURNS_PREVIEW_SCALE else 1f
                        MediaImage(
                            url = photo.thumbnailUrl.ifBlank { photo.url },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                            targetWidth = 1080,
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
                                        scaleX = KEN_BURNS_PREVIEW_SCALE
                                        scaleY = KEN_BURNS_PREVIEW_SCALE
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

        }

        if (!choosingOption && !showPushIn) {
            Spacer(Modifier.height(TvDimen.SpaceS))
            if (store.displayAlbumIds.isEmpty()) {
                Text("请先选择客厅展示相册", fontSize = TvType.Caption, color = TvColor.TextSecondary)
            } else {
                val interval = SettingsStore.formatInterval(s.photoIntervalSec)
                InfoLine("照片停留", "$interval / ${if (photoWall) "轮" else "张"}")
            }
            if (s.enabled) {
                InfoLine("自动进入", "静止 ${SettingsStore.formatIdleTimeout(s.idleTimeoutSec)}后")
            }
        }

        if (focusedKey == SettingsKey.SS_PREVIEW || focusedKey == SettingsKey.SS_ENABLED) {
            Spacer(Modifier.height(TvDimen.SpaceS))
            val cycle = if (showsClock) "换表盘" else "换轮播内容"
            Text(
                text = "ENTER 全屏预览 · 屏保里 ← → $cycle，其他键退出",
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

@Composable
private fun PhotoPushInPreview(enabled: Boolean) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(enabled) {
        scale.snapTo(1f)
        if (enabled) scale.animateTo(KEN_BURNS_PREVIEW_SCALE, tween(1_800, easing = LinearOutSlowInEasing))
    }
    Image(
        painter = painterResource(R.drawable.slideshow_preview_lake),
        contentDescription = null,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        contentScale = ContentScale.Crop,
    )
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
    // 静态极光：预览不需要每帧漂移，避免设置页持续重绘
    val t = 0.5f
    // drawBehind 的 DrawScope 不是 Composable 上下文，先取色再捕获。
    val accent = TvColor.Accent
    val accentAlt = TvColor.AccentAlt
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
                        listOf(accent.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(w * (0.20f + 0.10f * t), h * 0.28f),
                        radius = w * 0.42f,
                    ),
                    radius = w * 0.42f,
                    center = Offset(w * (0.20f + 0.10f * t), h * 0.28f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accentAlt.copy(alpha = 0.30f), Color.Transparent),
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

/** 屏保预览里 Ken Burns 的固定缩放（只做静态构图，不做动画） */
private const val KEN_BURNS_PREVIEW_SCALE = 1.05f
