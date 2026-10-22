package com.nasphoto.tv.ui.screensaver

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.ui.common.LivePhotoVideo
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.theme.TvColor
import kotlin.math.abs

/**
 * ================= 屏保背景层 =================
 *
 * PHOTO  ：全屏照片 + 2s 交叉淡入 + Ken Burns 推拉
 * AURORA ：纯 Compose 极光（三团径向渐变大色斑缓慢漂移 + 星点 + 噪点）
 * BLACK  ：纯黑极简（只留一层极弱的顶光，避免死黑）
 *
 * 全部背景统一压一层深色遮罩，保证白色大数字可读、不刺眼。
 */

/** 单张照片停留时长（下限 3s，防止配置成 0 时疯狂切换） */
private fun intervalMs(settings: ScreensaverSettings): Int =
    (settings.photoIntervalSec.coerceIn(3, 600)) * 1000

/** 交叉淡入时长：2s，但不超过单张停留时长的一半 */
private fun crossfadeMs(settings: ScreensaverSettings): Int =
    minOf(2000, intervalMs(settings) / 2)

/**
 * 照片轮播：用 Crossfade 做叠化，index 由外部计时驱动。
 * Ken Burns 用无限往返动画（scale 1.0→1.12 + 轻微平移），transformOrigin 每张随机，
 * 这样相邻两张的推拉方向不同，观感更像实拍「呼吸」。
 */
@Composable
fun ScreensaverPhotoBackground(
    photos: List<MediaItem>,
    index: Int,
    kenBurns: Boolean,
    settings: ScreensaverSettings,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(TvColor.Bg)) {
        Crossfade(
            targetState = index,
            animationSpec = tween(durationMillis = crossfadeMs(settings), easing = FastOutSlowInEasing),
            label = "photoCrossfade",
        ) { i ->
            val item = photos.getOrNull(i % photos.size.coerceAtLeast(1)) ?: return@Crossfade
            KenBurnsFrame(
                key = item.url,
                enabled = kenBurns,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    MediaImage(
                        url = item.thumbnailUrl.ifBlank { item.url },
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        targetWidth = 1280,
                    )
                    // 实况照片在屏保里也自动播放动态片段（静音、循环）
                    if (item.isLive && item.liveUrl.isNotBlank()) {
                        LivePhotoVideo(
                            url = item.liveUrl,
                            active = true,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ken Burns 容器：无限往返动画。
 * 注意 scale 放 1.0→1.12 且 pivot 随机偏置，所以放大后不会露出黑边（1.12 足够覆盖偏移）。
 */
@Composable
fun KenBurnsFrame(
    key: Any?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 每张照片的推拉参数不同：以 key 的 hash 决定起始方向与原点
    val seed = remember(key) { (key?.hashCode() ?: 0) and 0x7fffffff }
    val dirX = if (seed % 2 == 0) 1f else -1f
    val dirY = if ((seed / 2) % 2 == 0) 1f else -1f
    val originX = if (seed % 3 == 0) 0.35f else if (seed % 3 == 1) 0.65f else 0.5f
    val originY = if ((seed / 3) % 3 == 0) 0.35f else if ((seed / 3) % 3 == 1) 0.65f else 0.5f

    if (!enabled) {
        Box(modifier) { content() }
        return
    }

    val transition = rememberInfiniteTransition(label = "kenBurns")
    // 放大约 1.0 → 1.12，单程 26s，往返更慢；用 key 让不同照片反向
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenBurnsProgress",
    )
    val p = if (dirX > 0) progress else 1f - progress
    val scale = 1f + 0.12f * p
    val transX = (p - 0.5f) * 2f * 14f * dirX
    val transY = (p - 0.5f) * 2f * 10f * dirY

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = transX
                translationY = transY
                transformOrigin = TransformOrigin(originX, originY)
            },
    ) {
        content()
    }
}

/**
 * 极光背景：不依赖图片。
 * - 3~4 团超大径向渐变（极光青 / 星蓝 / 淡紫）各自以不同周期缓慢漂移
 * - 叠加极弱星点（固定伪随机，不闪烁，避免烧屏像素常亮）
 * - 顶层用 drawWithCache 画一层极淡噪点，给纯色大色块一点胶片感
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val t1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(34_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraA",
    )
    val t2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(47_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraB",
    )
    val t3 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(61_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraC",
    )

    val blobs = remember { auroraStars(seed = 20261019, count = 46) }

    Box(modifier.fillMaxSize().background(TvColor.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF070B12),
                    0.55f to Color(0xFF0A0F18),
                    1f to Color(0xFF04060A),
                ),
            )

            // 极光青主团：从左下缓缓扫到中上
            val cx1 = w * (0.12f + 0.26f * t1)
            val cy1 = h * (0.86f - 0.30f * t1)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        TvColor.Accent.copy(alpha = 0.26f),
                        TvColor.Accent.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(cx1, cy1),
                    radius = w * 0.62f,
                ),
            )

            // 星蓝副团：右上
            val cx2 = w * (0.92f - 0.30f * t2)
            val cy2 = h * (0.16f + 0.34f * t2)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        TvColor.AccentAlt.copy(alpha = 0.22f),
                        TvColor.AccentAlt.copy(alpha = 0.07f),
                        Color.Transparent,
                    ),
                    center = Offset(cx2, cy2),
                    radius = w * 0.58f,
                ),
            )

            // 淡紫（复用 AccentAlt 向白偏一点做紫调），中右偏下
            val cx3 = w * (0.62f + 0.22f * t3)
            val cy3 = h * (0.78f - 0.24f * t3)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8E7CFF).copy(alpha = 0.16f),
                        Color(0xFF8E7CFF).copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                    center = Offset(cx3, cy3),
                    radius = w * 0.50f,
                ),
            )

            // 顶部一道很淡的冷光带，模拟大气散射
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF9FD8FF).copy(alpha = 0.05f),
                    0.35f to Color.Transparent,
                ),
            )

            // 星点：固定位置、极低 alpha、尺寸 1~2.4px，不闪烁
            blobs.forEach { s ->
                drawCircle(
                    color = Color.White.copy(alpha = s.alpha),
                    radius = s.r,
                    center = Offset(s.x * w, s.y * h),
                )
            }
        }

        // 细噪点：靠 drawWithCache 预生成一个点阵，不逐帧随机（省电、不闪）
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val step = 3f
                    val dot = Color.White.copy(alpha = 0.020f)
                    onDrawBehind {
                        var y = 0f
                        var row = 0
                        while (y < size.height) {
                            var x = (row % 2) * step
                            while (x < size.width) {
                                drawCircle(dot, radius = 0.7f, center = Offset(x, y))
                                x += step * 2
                            }
                            y += step * 2
                            row++
                        }
                    }
                },
        )
    }
}

/** 纯黑极简：只留极弱顶光 + 中央一圈几乎看不见的冷色，避免"死黑"像关机 */
@Composable
fun BlackBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0A0D12), Color(0xFF05060A), Color.Black),
                    center = Offset.Unspecified,
                    radius = 1500f,
                ),
            ),
    )
}

/**
 * 背景之上的可读性遮罩：
 * - 整体 30~45% 黑（照片/极光），底部加深（TvColor.ScrimBottom 思路）
 * - 照片模式额外加左右方向的一点压暗，让大数字四周干净
 * - cinematic：巨幕时钟样式只上一层很薄的局部渐变，保住照片原本的通透感
 *   （参考 Apple TV 屏保：画面是主体，文字靠自身投影可读）
 */
@Composable
fun ScreensaverScrim(
    style: ScreensaverSettings.BackgroundStyle,
    cinematic: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (cinematic) {
        Box(
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.32f to Color.Black.copy(alpha = 0.06f),
                        0.70f to Color.Black.copy(alpha = 0.04f),
                        1f to Color.Black.copy(alpha = 0.40f),
                    ),
                )
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.20f),
                        0.42f to Color.Transparent,
                    ),
                ),
        )
        return
    }

    val base = when (style) {
        ScreensaverSettings.BackgroundStyle.PHOTO -> 0.34f
        ScreensaverSettings.BackgroundStyle.AURORA -> 0.30f
        ScreensaverSettings.BackgroundStyle.BLACK -> 0f
    }
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = base * 0.85f),
                    0.45f to Color.Black.copy(alpha = base),
                    1f to Color.Black.copy(alpha = (base + 0.22f).coerceAtMost(0.62f)),
                ),
            )
            .then(
                if (style == ScreensaverSettings.BackgroundStyle.PHOTO) {
                    Modifier.background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                            center = Offset.Unspecified,
                            radius = 1200f,
                        ),
                    )
                } else {
                    Modifier
                },
            ),
    )
}

// ---------------------------------------------------------------------------
// 伪随机星点（固定种子，屏保常驻时不会闪，也避免 CPU 抖动）
// ---------------------------------------------------------------------------

private class Star(val x: Float, val y: Float, val r: Float, val alpha: Float)

private fun auroraStars(seed: Int, count: Int): List<Star> {
    var s = seed
    fun next(): Float {
        // xorshift：确定性伪随机
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        return (abs(s % 10_000) / 10_000f)
    }
    return List(count) {
        Star(
            x = next(),
            y = next() * 0.92f,
            r = 0.6f + next() * 1.7f,
            alpha = 0.10f + next() * 0.32f,
        )
    }
}
