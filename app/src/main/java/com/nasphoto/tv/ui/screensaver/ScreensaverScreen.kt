package com.nasphoto.tv.ui.screensaver

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.ui.theme.NasPhotoTvTheme
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * ================= 屏保 / 待机显示（对标 iPhone StandBy） =================
 *
 * 结构（自下而上）：
 *   1. 背景层  PHOTO 照片轮播+Ken Burns / AURORA 纯 Compose 极光 / BLACK 纯黑
 *   2. 遮罩层  整体 30~45% 黑，底部加深（保证大数字可读、不刺眼）
 *   3. 防烧屏层 所有前景元素挤在一个 [PixelShiftBox] 容器里，每 2 分钟在九宫格
 *              固定位置间平移（±8dp，≤10dp），加 nightDim 的 0.90~1.0 呼吸
 *   4. 前景   CLOCK：时钟与退出提示（照片元信息永远不上屏）；
 *            PHOTO_ONLY：只保留照片本身，不显示时钟/日期/提示，任何文字都不上屏。
 *
 * 交互：←/→ 在屏保内切换样式（时钟模式换时钟样式，纯照片模式换轮播来源），
 *       其他任意键退出；所有按键都被最外层 Box 吞掉，不会漏到下层页面。
 */
@Composable
fun ScreensaverScreen(
    settings: ScreensaverSettings,
    photos: List<MediaItem>,
    onExit: () -> Unit,
    onCycleStyle: () -> Unit = {},
    onCycleSource: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val now = rememberNowEverySecond()
    val parts = rememberClockParts(now)

    val purePhoto = settings.mode == ScreensaverSettings.Mode.PHOTO_ONLY
    // 随机来源：每次进入屏保时重新洗牌（覆盖层退出后会被销毁，重进即换一组顺序）
    val pool = remember(photos, settings.photoSource) {
        if (settings.photoSource == ScreensaverSettings.PhotoSource.RANDOM) photos.shuffled() else photos
    }

    // 纯照片模式固定使用照片；没有照片时兜底成 AURORA（不崩、不黑屏）
    val effectiveBg = if (purePhoto) {
        if (pool.isEmpty()) ScreensaverSettings.BackgroundStyle.AURORA
        else ScreensaverSettings.BackgroundStyle.PHOTO
    } else {
        effectiveBackground(settings, pool)
    }

    // ---------- 照片轮播：每 photoIntervalSec 换一张，自动淡入由 Crossfade 负责 ----------
    // 用整个 pool 做 key：换轮播来源后即使数量不变，也从第一张重新开始
    var photoIndex by remember(pool) { mutableIntStateOf(0) }
    LaunchedEffect(pool, settings.photoIntervalSec) {
        if (pool.size <= 1) return@LaunchedEffect
        val interval = settings.photoIntervalSec.coerceIn(3, 600) * 1000L
        while (true) {
            delay(interval)
            photoIndex = (photoIndex + 1) % pool.size
        }
    }

    // ---------- 键盘：任何按键都退出，且必须返回 true 吞掉 ----------
    // 屏保现在是覆盖层：下层页面仍然组合着并持有焦点。requestFocus 若在覆盖层节点
    // 完成布局之前调用会失败（被 runCatching 吞掉），按键就会漏到下层页面。
    // 所以这里重试到真正拿到焦点为止，而不是只试一次。
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(30) { attempt ->
            delay(if (attempt == 0) 80L else 100L)
            if (hasFocus) return@LaunchedEffect
            runCatching { focusRequester.requestFocus() }
                .onFailure { android.util.Log.w("ScreensaverFocus", "requestFocus failed", it) }
        }
        if (!hasFocus) android.util.Log.w("ScreensaverFocus", "gave up acquiring focus")
    }
    // 只退出一次：长按产生的重复 KeyDown / BACK 兜底可能同时到达
    var exited by remember { mutableStateOf(false) }
    val exitOnce: () -> Unit = {
        if (!exited) {
            exited = true
            onExit()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvColor.Bg)
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (purePhoto) onCycleSource() else onCycleStyle()
                        }

                        else -> exitOnce()
                    }
                }
                true // KeyDown/KeyUp 都吞掉：保证不会同时触发别的动作
            },
    ) {
        // 1. 背景
        when (effectiveBg) {
            ScreensaverSettings.BackgroundStyle.PHOTO -> ScreensaverPhotoBackground(
                photos = pool,
                index = photoIndex,
                kenBurns = settings.kenBurns,
                settings = settings,
                modifier = Modifier.fillMaxSize(),
            )
            ScreensaverSettings.BackgroundStyle.AURORA -> AuroraBackground(Modifier.fillMaxSize())
            ScreensaverSettings.BackgroundStyle.BLACK -> BlackBackground(Modifier.fillMaxSize())
        }

        if (!purePhoto) {
            // 2. 可读性遮罩
            ScreensaverScrim(
                effectiveBg,
                cinematic = settings.clockStyle == ScreensaverSettings.ClockStyle.CINEMA,
                modifier = Modifier.fillMaxSize(),
            )

            // 3. 防烧屏容器：呼吸 + 每 2 分钟整体位移
            PixelShiftBox(enabled = settings.nightDim, Modifier.fillMaxSize()) {
                EntranceFade(Modifier.fillMaxSize()) {
                    ScreensaverForeground(
                        settings = settings,
                        photoIndex = photoIndex,
                        now = now,
                        parts = parts,
                    )
                }
            }

            // 退出提示：进入后 8s 内可见，之后淡出
            ExitHint(Modifier.align(Alignment.BottomCenter))
        }
    }

    // 兜底：若某些遥控器把 BACK 交给 Activity（不经过 Compose 焦点链），也要吞掉并退出，
    // 否则屏保期间 BACK 会泄漏到 Nav 外壳里把页面退掉两层。
    val activity = LocalContext.current.findActivity() as? ComponentActivity
    DisposableEffect(activity) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onExit()
            }
        }
        activity?.onBackPressedDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }
}

/** 在 Context 包装链里找到宿主 Activity（Compose 常见链路：ContextWrapper → Activity） */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx: android.content.Context = this
    while (true) {
        if (ctx is android.app.Activity) return ctx
        ctx = (ctx as? android.content.ContextWrapper)?.baseContext ?: return null
    }
}

// ---------------------------------------------------------------------------
// 前景布局
// ---------------------------------------------------------------------------

@Composable
private fun BoxScope.ScreensaverForeground(
    settings: ScreensaverSettings,
    photoIndex: Int,
    now: LocalDateTime,
    parts: ClockParts,
) {
    val style = settings.clockStyle

    // 时钟：按样式决定锚点；天气跟着让位（CINEMA 的左上被巨号数字占住）
    when (style) {
        ScreensaverSettings.ClockStyle.CINEMA -> {
            CinemaClock(
                now = now,
                parts = parts,
                showDate = settings.showDate,
                variant = photoIndex,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ScreensaverSettings.ClockStyle.MINIMAL -> {
            // 右上小钟 + 中央偏左大字日期
            MinimalClock(
                now = now,
                showDate = settings.showDate,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = TvDimen.ScreenPadding, end = TvDimen.ScreenPadding),
            )
            MinimalDateHero(
                now = now,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = TvDimen.ScreenPadding + 8.dp, bottom = 24.dp),
            )
        }

        ScreensaverSettings.ClockStyle.DATE_FOCUS -> {
            // 居中的「星期 + 日期 + 月历卡」组合，整体略偏下，给顶部天气留呼吸
            ScreensaverClock(
                style = style,
                now = now,
                parts = parts,
                showDate = settings.showDate,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 48.dp),
            )
        }

        else -> {
            // FLIP / THIN：画面正中央的大时钟
            ScreensaverClock(
                style = style,
                now = now,
                parts = parts,
                showDate = settings.showDate,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 防烧屏：像素位移 + 夜间呼吸
// ---------------------------------------------------------------------------

/** 9 个固定偏移位置（dp）：中心 + 八个方向，最大 ±8dp，避免整屏像素常亮 */
private val PIXEL_SHIFT_POSITIONS: List<Pair<Float, Float>> = listOf(
    0f to 0f,
    -8f to -8f,
    8f to -8f,
    8f to 8f,
    -8f to 8f,
    0f to -8f,
    8f to 0f,
    0f to 8f,
    -8f to 0f,
)

/**
 * 防烧屏容器：
 *  - 每 2 分钟切到下一个固定偏移（quadraticTo 880ms，肉眼看不出"跳"）
 *  - nightDim 时整体 alpha 在 0.90~1.00 之间以 30s 一个来回缓慢呼吸
 *  - 用 graphicsLayer 平移，不触发测量/布局
 */
@Composable
fun PixelShiftBox(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var slot by remember { mutableIntStateOf(0) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            delay(2 * 60 * 1000L) // 2 分钟
            slot = (slot + 1) % PIXEL_SHIFT_POSITIONS.size
        }
    }
    val target = PIXEL_SHIFT_POSITIONS[slot]
    val shiftX by animateFloatAsState(
        targetValue = target.first,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "pixelShiftX",
    )
    val shiftY by animateFloatAsState(
        targetValue = target.second,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "pixelShiftY",
    )
    // 夜间呼吸：0.90 ↔ 1.00，一个来回 30s
    val breath = rememberBreathingAlpha(active = enabled)

    Box(
        modifier.graphicsLayer {
            translationX = shiftX.dp.toPx()
            translationY = shiftY.dp.toPx()
            alpha = breath
        },
        content = content,
    )
}

/**
 * 极慢的亮度呼吸。用 rememberInfiniteTransition 而不是状态机，
 * 这样它会跟着 Choreographer 走、暂停时自动冻结（屏保长时间常驻也不耗电炸弹）。
 */
@Composable
private fun rememberBreathingAlpha(active: Boolean): Float {
    if (!active) return 1f
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
    val a by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.00f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(15000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "breathAlpha",
    )
    return a
}

// ---------------------------------------------------------------------------
// 退出提示
// ---------------------------------------------------------------------------

/** 「← → 换样式，其他键返回」：渐入 400ms，8s 后淡出且不再出现 */
@Composable
private fun ExitHint(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(8000)
        visible = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "exitHint",
    )
    if (alpha <= 0.001f) return

    Box(
        modifier
            .padding(bottom = 40.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = TvDimen.SpaceM, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.45f)),
            )
            Spacer(Modifier.width(TvDimen.SpaceXs))
            Text(
                text = "← → 换样式 · 其他键返回",
                fontSize = TvType.Tiny,
                color = Color.White.copy(alpha = 0.42f),
                letterSpacing = 2.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

private fun previewPhotos(): List<MediaItem> = listOf(
    MediaItem(
        id = "p1", title = "冰岛 · 极光之夜", type = MediaType.PHOTO,
        url = "raw/wall_aurora", dateTaken = LocalDate.of(2025, 3, 14), resolution = "3840x2160",
    ),
    MediaItem(
        id = "p2", title = "海岛日落", type = MediaType.PHOTO,
        url = "raw/wall_sunset", dateTaken = LocalDate.of(2025, 7, 12), resolution = "4032x3024",
    ),
)

/** 点击预览：CINEMA + PHOTO（默认组合，对标 Apple TV 屏保） */
@Preview(widthDp = 1920, heightDp = 1080, showBackground = true, name = "屏保 · 巨幕时钟 + 照片轮播")
@Composable
private fun PreviewCinemaPhoto() {
    NasPhotoTvTheme {
        ScreensaverScreen(
            settings = ScreensaverSettings(),
            photos = previewPhotos(),
            onExit = {},
        )
    }
}

/** 点击预览：FLIP + PHOTO */
@Preview(widthDp = 1920, heightDp = 1080, showBackground = true, name = "屏保 · 翻页数字 + 照片轮播")
@Composable
private fun PreviewFlipPhoto() {
    NasPhotoTvTheme {
        ScreensaverScreen(
            settings = ScreensaverSettings(clockStyle = ScreensaverSettings.ClockStyle.FLIP),
            photos = previewPhotos(),
            onExit = {},
        )
    }
}

/** 点击预览：DATE_FOCUS + AURORA */
@Preview(widthDp = 1920, heightDp = 1080, showBackground = true, name = "屏保 · 日期日历 + 极光")
@Composable
private fun PreviewDateFocusAurora() {
    NasPhotoTvTheme {
        ScreensaverScreen(
            settings = ScreensaverSettings(
                clockStyle = ScreensaverSettings.ClockStyle.DATE_FOCUS,
                background = ScreensaverSettings.BackgroundStyle.AURORA,
            ),
            photos = emptyList(),
            onExit = {},
        )
    }
}

/** 点击预览：MINIMAL + BLACK */
@Preview(widthDp = 1920, heightDp = 1080, showBackground = true, name = "屏保 · 极简小钟 + 纯黑")
@Composable
private fun PreviewMinimalBlack() {
    NasPhotoTvTheme {
        ScreensaverScreen(
            settings = ScreensaverSettings(
                clockStyle = ScreensaverSettings.ClockStyle.MINIMAL,
                background = ScreensaverSettings.BackgroundStyle.BLACK,
            ),
            photos = emptyList(),
            onExit = {},
        )
    }
}

/** 点击预览：THIN + PHOTO */
@Preview(widthDp = 1920, heightDp = 1080, showBackground = true, name = "屏保 · 纤细极简 + 照片")
@Composable
private fun PreviewThinPhoto() {
    NasPhotoTvTheme {
        ScreensaverScreen(
            settings = ScreensaverSettings(
                clockStyle = ScreensaverSettings.ClockStyle.THIN,
                background = ScreensaverSettings.BackgroundStyle.PHOTO,
                nightDim = false,
            ),
            photos = previewPhotos(),
            onExit = {},
        )
    }
}
