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
 * 加载中 / 失败 / 非 http 地址（Compose Preview 的假数据）统一显示渐变占位，
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
            .components {
                // GIF / 动态 WebP / 动画 HEIF：API 28+ 用系统 ImageDecoder，旧版本用 GifDecoder
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
            .also { instance = it }
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

    if (!url.startsWith("http://") && !url.startsWith("https://")) {
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
