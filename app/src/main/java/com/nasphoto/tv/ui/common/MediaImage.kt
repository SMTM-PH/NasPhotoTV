package com.nasphoto.tv.ui.common

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nasphoto.tv.data.fn.FnOsTransport

/**
 * ================= 通用媒体加载组件 =================
 *
 * 真实 NAS 图片走 Coil，与 API 共用 [FnOsTransport.client]：
 *  - 自签证书的 TOFU 信任；
 *  - AccessToken 由拦截器动态注入（重登后无需重建 ImageLoader）。
 *
 * 支持 http(s) NAS 图片和本地 android.resource 示例；加载中 / 失败 / 其他地址显示渐变占位，
 * 保证 10-foot UI 不会出现白块或崩溃。
 */

/** 由 url 生成一个稳定的「主题色」，用于加载中 / 失败占位 */
fun colorForUrl(url: String): Color {
    var h = url.hashCode()
    h = h xor (h ushr 16)
    val hue = ((h and 0x7fffffff) % 360) / 360f
    return Color.hsv(hue * 360f, 0.45f, 0.55f)
}

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
) {
    val boxModifier = if (cornerRadiusDp > 0) {
        modifier.clip(RoundedCornerShape(cornerRadiusDp.dp))
    } else {
        modifier
    }

    if (!url.startsWith("http://") && !url.startsWith("https://") &&
        !url.startsWith("android.resource://")) {
        Placeholder(colorForUrl(url), boxModifier)
        return
    }

    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .size(targetWidth)
            .crossfade(true)
            .build(),
        imageLoader = NasImageLoader.get(context),
        contentDescription = contentDescription,
        modifier = boxModifier,
        contentScale = contentScale,
        loading = { Placeholder(colorForUrl(url)) },
        error = { Placeholder(colorForUrl(url)) },
    )
}

/** 占位图：渐变 + 虚线相框图标 */
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
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.White.copy(alpha = 0.5f),
        )
    }
}

fun Modifier.clipRounded(dp: Int): Modifier =
    this.clip(RoundedCornerShape(dp.dp))
