/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasphoto.tv.data.MusicUploadServer
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.SlideshowMusic
import com.nasphoto.tv.data.SlideshowProfile
import com.nasphoto.tv.ui.library.TvFocusBox
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * 背景音乐面板：显示手机上传地址、已上传曲目，开关背景音乐。
 *
 * 面板打开期间启动上传服务，关闭时立即停止 —— 平时不占端口。
 */
@Composable
fun MusicOverlay(
    store: SettingsStore,
    feedback: SettingsFeedback,
    onClose: () -> Unit,
    albumProfile: SlideshowProfile? = null,
    onAlbumProfileChange: ((SlideshowProfile) -> SlideshowProfile) -> Unit = {},
) {
    val context = LocalContext.current
    val server = remember(context) { MusicUploadServer(context) }
    var serverReady by remember(server) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val closeFocus = remember { FocusRequester() }

    DisposableEffect(server) {
        serverReady = server.start()
        onDispose { server.stop() }
    }

    val musicEnabled = albumProfile?.musicEnabled ?: store.screensaver.musicEnabled
    val urls = remember(server, serverReady, refresh) {
        if (serverReady) server.urls else emptyList()
    }
    val tracks = remember(refresh, feedback.musicVersion) { SlideshowMusic.tracks(context) }

    SettingsDialog(onClose) {
        LaunchedEffect(Unit) {
            withFrameMillis { }
            closeFocus.requestFocus()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Back || e.key == Key.Escape)) {
                        onClose(); true
                    } else false
                },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(TvColor.BgElevated, RoundedCornerShape(TvDimen.RadiusXL))
                    .border(1.dp, TvColor.Divider, RoundedCornerShape(TvDimen.RadiusXL))
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("幻灯片背景音乐", fontSize = TvType.Section, fontWeight = FontWeight.SemiBold, color = TvColor.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (albumProfile == null) {
                        "手机连同一个 Wi‑Fi，用浏览器打开下面的地址上传音频。幻灯片和屏保会循环播放。"
                    } else {
                        "手机连同一个 Wi‑Fi，用浏览器打开下面的地址上传音频，再为此相册选一首。"
                    },
                    fontSize = TvType.Tiny, color = TvColor.TextTertiary,
                )
                Spacer(Modifier.height(TvDimen.SpaceM))

                if (urls.isEmpty()) {
                    Text("未检测到局域网地址：请确认电视已联网后重新打开本面板", fontSize = TvType.Caption, color = TvColor.Danger)
                } else {
                    urls.forEach { u ->
                        Text(u, fontSize = 22.sp, color = TvColor.Accent, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(TvDimen.SpaceM))
                Text(
                    if (musicEnabled) "背景音乐：开" else "背景音乐：关",
                    fontSize = TvType.Body, color = if (musicEnabled) TvColor.Accent else TvColor.TextSecondary,
                )
                Spacer(Modifier.height(TvDimen.SpaceM))

                Text(
                    if (albumProfile == null) "已上传（播放最新一首）" else "此相册的配乐",
                    fontSize = TvType.Caption,
                    color = TvColor.TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                if (albumProfile != null) {
                    val choices = listOf(null) + tracks.map { it.name }
                    choices.forEach { name ->
                        val selected = name == albumProfile.trackName
                        TvFocusBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadiusDp = 12,
                            onConfirm = { onAlbumProfileChange { it.copy(trackName = name, musicEnabled = true) } },
                        ) { focused ->
                            val label = name ?: "最新上传"
                            Pill(if (selected) "✓  $label" else label, focused)
                        }
                    }
                    val selectedName = albumProfile.trackName
                    if (selectedName != null && tracks.none { it.name == selectedName }) {
                        Text("已选曲目不可用，请重新选择", fontSize = TvType.Tiny, color = TvColor.TextSecondary)
                    }
                }
                if (tracks.isEmpty()) {
                    Text("还没有上传音乐", fontSize = TvType.Caption, color = TvColor.TextTertiary)
                } else if (albumProfile == null) {
                    tracks.take(6).forEachIndexed { i, f ->
                        Text(
                            "${if (i == 0) "▶ " else "   "}${f.name}",
                            fontSize = TvType.Caption,
                            color = if (i == 0) TvColor.TextPrimary else TvColor.TextSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(TvDimen.SpaceM))
                Row(horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM), verticalAlignment = Alignment.CenterVertically) {
                    TvFocusBox(
                        onConfirm = {
                            if (albumProfile == null) {
                                store.updateScreensaver { it.copy(musicEnabled = !it.musicEnabled) }
                            } else {
                                onAlbumProfileChange { it.copy(musicEnabled = !it.musicEnabled) }
                            }
                            feedback.musicVersion++
                        },
                        cornerRadiusDp = 12,
                        focusedScale = 1.04f,
                    ) { focused -> Pill(if (musicEnabled) "关闭音乐" else "开启音乐", focused) }
                    TvFocusBox(
                        onConfirm = { refresh++; feedback.musicVersion++ },
                        cornerRadiusDp = 12,
                        focusedScale = 1.04f,
                    ) { focused -> Pill("刷新列表", focused) }
                    TvFocusBox(
                        focusRequester = closeFocus,
                        onConfirm = onClose,
                        cornerRadiusDp = 12,
                        focusedScale = 1.04f,
                    ) { focused -> Pill("完成", focused) }
                    Spacer(Modifier.width(TvDimen.SpaceS))
                    Text("BACK 也可关闭", fontSize = TvType.Tiny, color = TvColor.TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, focused: Boolean) {
    Text(
        label,
        fontSize = TvType.Caption,
        color = if (focused) TvColor.TextPrimary else TvColor.TextSecondary,
        modifier = Modifier
            .background(
                if (focused) TvColor.Accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                if (focused) TvColor.Accent else TvColor.Divider,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
