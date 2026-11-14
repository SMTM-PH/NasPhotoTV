package com.nasphoto.tv.ui.common

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nasphoto.tv.data.fn.FnOsTransport

/** 本地调试地址 "raw/xxx" 映射到 res/raw；http(s) / file 等地址原样交给 ExoPlayer */
fun resolveMediaUri(context: Context, url: String): Uri {
    if (url.startsWith("raw/")) {
        val name = url.substringAfterLast('/')
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        if (id != 0) return Uri.parse("android.resource://${context.packageName}/$id")
    }
    return Uri.parse(url)
}

/**
 * 实况照片（Live Photo / Motion Photo）的动态片段。
 *
 * 约定：静图由调用方先画在下面，这里只负责把动态片段盖上去 ——
 * 播放失败、或片段还没出第一帧时，用户看到的仍然是那张照片，不会黑屏。
 *
 * 屏保与查看器都用同一份实现：静音、循环、跟随 [active] 自动播放/暂停。
 * 接真实 NAS 时把 [url] 换成 fnOS 的 `additional.thumbnail.videoUrl`，
 * 并在数据源层给 ExoPlayer 配一个带 `AccessToken` 头的 DataSource 即可。
 */
@Composable
fun LivePhotoVideo(
    url: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /** >0 = 用户请求播放；每递增一次播一遍后停在最后一帧（回到静态观感） */
    playRequest: Int = 0,
    onPlayingChanged: (Boolean) -> Unit = {},
) {
    if (url.isBlank()) return
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(FnOsTransport.mediaDataSourceFactory(context)),
            )
            .build()
            .apply {
                volume = 0f
                // 播一遍就停（不用 REPEAT_MODE_ONE 无限循环）：用户确认键每按一次播一次
                repeatMode = Player.REPEAT_MODE_OFF
                setMediaItem(MediaItem.fromUri(resolveMediaUri(context, url)))
                playWhenReady = false
                prepare()
            }
    }

    // 静态优先：active（进入看图）本身不再触发播放，只有明确的 playRequest 才播。
    // 每次请求都从头播一遍；播到末尾自动停在最后一帧。
    LaunchedEffect(exoPlayer, playRequest) {
        if (playRequest <= 0) {
            exoPlayer.pause()
            exoPlayer.seekTo(0)
            return@LaunchedEffect
        }
        exoPlayer.seekTo(0)
        exoPlayer.play()
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            kotlinx.coroutines.delay(200)
            onPlayingChanged(exoPlayer.isPlaying)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                resizeMode = if (contentScale == ContentScale.Crop) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                // 视频只是画面层：不抢焦点、不拦遥控器按键
                isFocusable = false
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                player = exoPlayer
            }
        },
        modifier = modifier,
        update = { view -> view.player = exoPlayer },
    )
}
