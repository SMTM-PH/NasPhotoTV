/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.common

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nasphoto.tv.data.fn.FnOsTransport
import kotlinx.coroutines.delay

/**
 * ================= 通用媒体加载组件 =================
 *
 * 真实 NAS 图片走 Coil，与 API 共用 [FnOsTransport.client]：
 *  - 自签证书的 TOFU 信任；
 *  - AccessToken 由拦截器动态注入（重登后无需重建 ImageLoader）。
 *
 * 支持 http(s) NAS 图片和本地 android.resource 示例。占位样式见 [MediaPlaceholder]：
 * 幻灯片换页时不再闪出「破图」锯齿图标，而是保留上一张画面直到新图解码完成。
 */

/**
 * 加载 / 失败时的占位样式。
 *
 * - [SPINNER]：渐变底 + 转圈，卡片、缩略图默认。旧的 `BrokenImage` 锯齿图标
 *   在两张幻灯片之间会一闪而过，观感很差，统一换成转圈。
 * - [HOLD]：完全透明，保留下层画面（全屏舞台 / 屏保）。下一张还没解码出来时
 *   继续显示上一张；超过 [HoldSpinnerDelayMs] 才叠一个很轻的转圈，
 *   不会再出现「渐变底 + 锯齿图标」的间隙。
 */
enum class MediaPlaceholder { SPINNER, HOLD }

/** HOLD 模式下多久才显示转圈：秒开时不打扰，慢网络时给个可见反馈 */
private const val HoldSpinnerDelayMs = 600L

/** 由 url 生成一个稳定的「主题色」，用于加载中 / 失败占位 */
fun colorForUrl(url: String): Color {
    var h = url.hashCode()
    h = h xor (h ushr 16)
    val hue = ((h and 0x7fffffff) % 360) / 360f
    return Color.hsv(hue * 360f, 0.45f, 0.55f)
}

/**
 * 按屏幕密度放大缩略图请求尺寸（4K 面板不发虚），大图原样保留。
 * 抽成纯函数是为了让「预加载下一张」与真正显示时用同一把尺子 ——
 * Coil 的内存缓存键包含尺寸，两边不一致就会白预加载一场。
 */
internal fun effectiveImageWidth(targetWidth: Int, density: Float): Int =
    if (targetWidth >= 1920) targetWidth
    else (targetWidth * (density / 2f)).toInt().coerceIn(targetWidth, 1920)

/** 全局单例 ImageLoader：磁盘/内存缓存由 Coil 管理 */
object NasImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .okHttpClient(FnOsTransport.client)
            // 电视盒子内存普遍很小（实测本机模拟器 2.8G 上限、整机 7.7G 无 swap），
            // Coil 默认按 JVM 堆百分比取内存缓存，在低内存设备上会把整机拖到 OOM。
            // 这里显式压到 8%（约 20MB 级别），代价是滑动时偶尔要重解码，
            // 收益是长时间浏览不再把进程/宿主压死 —— 相册是长驻页，稳定性优先。
            .memoryCache {
                MemoryCache.Builder(context.applicationContext)
                    .maxSizePercent(0.08)
                    .build()
            }
            // 磁盘缓存同理给个上限，避免电视存储被原图缩略图长期撑大
            .diskCache {
                DiskCache.Builder()
                    .directory(context.applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .components {
                // GIF / 动态 WebP / 动画 HEIF：API 28+ 用系统 ImageDecoder，旧版本用 GifDecoder
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            // 本机（MTK Android 9 / Hisense）在 RenderThread 上传硬件位图时会因
            // glEGLImageTargetTexture2DOES 返回 GL_INVALID_VALUE 直接 SIGABRT
            // （libhwui: OpenGLPipeline::allocateHardwareBitmap）。Coil 默认用
            // ImageDecoder 生成 Config.HARDWARE 位图，正好踩中这个厂商 bug。
            // 关掉硬件位图改用软件位图：解码/绘制略耗 CPU，但不会再让整个进程闪退。
            .allowHardware(false)
            .crossfade(true)
            .build()
            .also { instance = it }
    }

    /**
     * 清空内存与磁盘缓存。
     *
     * 切换飞牛账号时必须调 —— 缓存是按 URL 索引的，而不同账号完全可能命中同一个
     * 地址键（同一台 NAS、同一套图床），一旦命中就会把上一个账号的照片画到下一个
     * 账号的界面上。这不是性能问题，是隐私问题。
     *
     * [context] 为 null（还没初始化过 ImageLoader）时什么都不做：没有实例就没有缓存。
     */
    @OptIn(ExperimentalCoilApi::class)
    fun clearCaches(context: Context?) {
        val loader = instance ?: return
        loader.memoryCache?.clear()
        // DiskCache.clear() 会删掉缓存目录里的所有文件；不用再手删目录 ——
        // 目录被删掉后 Coil 不会自动重建，下一次写入会失败。
        loader.diskCache?.clear()
    }
}

@Composable
fun MediaImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidth: Int = 640,
    cornerRadiusDp: Int = 0,
    /** 见 [MediaPlaceholder]：全屏舞台 / 屏保传 [MediaPlaceholder.HOLD] 保留上一张画面 */
    placeholder: MediaPlaceholder = MediaPlaceholder.SPINNER,
    decodedImage: Drawable? = null,
    onDecoded: ((Drawable) -> Unit)? = null,
) {
    val boxModifier = if (cornerRadiusDp > 0) {
        modifier.clip(RoundedCornerShape(cornerRadiusDp.dp))
    } else {
        modifier
    }

    if (decodedImage is BitmapDrawable) {
        Image(
            bitmap = decodedImage.bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = boxModifier,
            contentScale = contentScale,
        )
        return
    }

    if (decodedImage == null && !url.startsWith("http://") && !url.startsWith("https://") &&
        !url.startsWith("android.resource://")) {
        when (placeholder) {
            MediaPlaceholder.SPINNER -> Placeholder(colorForUrl(url), boxModifier)
            MediaPlaceholder.HOLD -> Box(boxModifier)
        }
        return
    }

    val context = LocalContext.current
    val tint = colorForUrl(url)
    // targetWidth 是按 1080p（density 2）调的像素值。4K 面板上密度会到 4，
    // 同样的卡片物理像素翻倍，还用原值就会发虚。这里只对「缩略图」按密度等比放大，
    // 封顶 1920 防止低内存电视解码过大位图；本来就是全屏/全景的大图（>=1920）保持原值。
    val effectiveWidth = effectiveImageWidth(targetWidth, LocalDensity.current.density)
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(decodedImage ?: url)
            .size(effectiveWidth)
            .crossfade(decodedImage == null)
            .build(),
        imageLoader = NasImageLoader.get(context),
        contentDescription = contentDescription,
        modifier = boxModifier,
        contentScale = contentScale,
        onSuccess = onDecoded?.let { callback -> { state -> callback(state.result.drawable) } },
        loading = {
            when (placeholder) {
                MediaPlaceholder.SPINNER -> SpinnerPlaceholder(tint)
                MediaPlaceholder.HOLD -> HoldPlaceholder()
            }
        },
        error = {
            // 失败也不再画锯齿「破图」图标：卡片给渐变底，全屏舞台保持透明让上一层画面留着。
            when (placeholder) {
                MediaPlaceholder.SPINNER -> Placeholder(tint)
                MediaPlaceholder.HOLD -> Box(Modifier.fillMaxSize())
            }
        },
    )
}

/** 卡片占位：渐变底 + 转圈（取代旧的锯齿破图图标） */
@Composable
private fun SpinnerPlaceholder(tint: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.65f), Color(0xFF14161D)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = Color.White.copy(alpha = 0.72f),
            strokeWidth = 3.dp,
        )
    }
}

/**
 * 全屏舞台 / 屏保占位：透明，保留下层（上一张）画面。
 * 只有加载真的很慢时才叠一个很轻的转圈，避免黑屏时用户以为死机。
 */
@Composable
private fun HoldPlaceholder() {
    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(HoldSpinnerDelayMs)
        showSpinner = true
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color.White.copy(alpha = 0.62f),
                strokeWidth = 3.dp,
            )
        }
    }
}

/** 静态空状态占位：只保留渐变底，不画任何图标（设置页「还没有视频」等用） */
@Composable
fun Placeholder(tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.65f), Color(0xFF14161D)),
                ),
            ),
    )
}

fun Modifier.clipRounded(dp: Int): Modifier =
    this.clip(RoundedCornerShape(dp.dp))
