package com.nasphoto.tv.ui.video

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nasphoto.tv.data.fn.FnOsTransport
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.PlaybackSettings
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.delay

/**
 * ================= 视频播放器（Media3 + NAS 鉴权串流） =================
 *
 * 遥控器：ENTER 播放/暂停 · ← → 快退/快进 10 秒 · ↑↓ 呼出/隐藏控制条 · BACK 退出。
 * 播放结束按 [PlaybackSettings.autoPlayNext] 自动进入下一条；[PlaybackSettings.loopMode] 单条循环。
 */
@Composable
fun VideoPlayerScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    contextTitle: String,
    settings: PlaybackSettings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    var index by remember { mutableIntStateOf(initialIndex.coerceIn(0, items.lastIndex)) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(FnOsTransport.mediaDataSourceFactory(context)),
            )
            .build()
            .apply {
                repeatMode = if (settings.loopMode) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    DisposableEffect(player, index) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    if (settings.autoPlayNext && index < items.lastIndex) {
                        index += 1
                    } else {
                        isPlaying = false
                        controlsVisible = true
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(index) {
        val item = items.getOrNull(index) ?: return@LaunchedEffect
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.url))
        player.prepare()
        player.play()
        buffering = true
        controlsVisible = true
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            delay(400)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, positionMs) {
        if (controlsVisible && isPlaying) {
            delay(3200)
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { focusRequester.requestFocus() }
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onClose()
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause,
                    -> {
                        if (player.isPlaying) player.pause() else player.play()
                        controlsVisible = true
                        true
                    }

                    Key.DirectionLeft -> {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                        controlsVisible = true
                        true
                    }

                    Key.DirectionRight -> {
                        player.seekTo(player.currentPosition + 10_000L)
                        controlsVisible = true
                        true
                    }

                    Key.DirectionUp, Key.DirectionDown -> {
                        controlsVisible = !controlsVisible
                        true
                    }

                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    isFocusable = false
                    isClickable = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.player = player },
        )

        if (buffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = TvColor.Accent,
                strokeWidth = 3.dp,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                            ),
                        )
                        .padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = items[index].title,
                            fontSize = TvType.Section,
                            fontWeight = FontWeight.SemiBold,
                            color = TvColor.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = contextTitle,
                            fontSize = TvType.Tiny,
                            color = TvColor.TextTertiary,
                        )
                    }
                    Text(
                        text = "${index + 1} / ${items.size}",
                        fontSize = TvType.Caption,
                        color = TvColor.TextSecondary,
                    )
                }

                Spacer(Modifier.weight(1f))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            ),
                        )
                        .padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
                ) {
                    ProgressTrack(positionMs = positionMs, durationMs = durationMs)
                    Spacer(Modifier.height(TvDimen.SpaceS))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = TvColor.TextPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(TvDimen.SpaceS))
                        Text(
                            text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                            fontSize = TvType.Caption,
                            color = TvColor.TextSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "ENTER 播放/暂停 · ←→ 快进 10 秒 · BACK 返回",
                            fontSize = TvType.Tiny,
                            color = TvColor.TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(TvColor.Divider, RoundedCornerShape(3.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .background(TvColor.Accent, RoundedCornerShape(3.dp)),
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
