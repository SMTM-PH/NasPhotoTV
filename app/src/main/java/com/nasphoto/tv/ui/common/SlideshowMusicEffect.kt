package com.nasphoto.tv.ui.common

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.SlideshowMusic

/**
 * 幻灯片背景音乐：把用户上传的音频循环播放。
 *
 * [active] 为 true（正在放幻灯片 / 屏保）且设置里开启音乐、且确有上传文件时才播放。
 * 组件销毁或 active 变 false 时立即释放播放器，不会在后台偷偷出声。
 */
@Composable
fun SlideshowMusicEffect(active: Boolean) {
    val context = LocalContext.current
    val enabled = SettingsStore.screensaver.musicEnabled
    val track = remember(active, enabled) {
        if (active && enabled) SlideshowMusic.latest(context) else null
    }
    val volume = SettingsStore.screensaver.musicVolume

    DisposableEffect(track?.absolutePath, volume) {
        val file = track
        if (file == null || !file.exists()) {
            return@DisposableEffect onDispose { }
        }
        var player: ExoPlayer? = null
        runCatching {
            player = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
                setMediaItem(ExoMediaItem.fromUri(Uri.fromFile(file)))
                repeatMode = Player.REPEAT_MODE_ONE
                this.volume = volume
                prepare()
                playWhenReady = true
            }
        }
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }
}
