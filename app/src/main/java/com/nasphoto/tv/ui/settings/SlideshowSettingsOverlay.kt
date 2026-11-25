package com.nasphoto.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.SlideshowMusic
import com.nasphoto.tv.data.SlideshowProfile
import com.nasphoto.tv.ui.common.ScreenIdleGuard
import com.nasphoto.tv.ui.library.DetailAction
import com.nasphoto.tv.ui.library.TvFocusBox
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun SlideshowSettingsOverlay(
    collectionTitle: String,
    slideshowKey: String,
    onClose: () -> Unit,
    onPlay: () -> Unit,
) {
    val store = SettingsStore
    val context = LocalContext.current
    val feedback = remember { SettingsFeedback() }
    val savedProfile = store.albumSlideshow(slideshowKey)
    val profile = savedProfile ?: SlideshowProfile.from(store.screensaver)
    val intervalFocus = remember { FocusRequester() }
    val musicFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val previewFocus = remember { FocusRequester() }
    var returnFocus by remember { mutableStateOf(intervalFocus) }
    var previewOpen by remember { mutableStateOf(false) }

    fun updateProfile(change: (SlideshowProfile) -> SlideshowProfile) {
        val current = store.albumSlideshow(slideshowKey) ?: SlideshowProfile.from(store.screensaver)
        store.setAlbumSlideshow(slideshowKey, change(current))
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            ScreenIdleGuard.hold()
            delay(10_000)
        }
    }

    if (previewOpen) {
        SlideshowPreviewOverlay(profile, onClose = { previewOpen = false })
    } else if (feedback.musicPanel) {
        MusicOverlay(
            store, feedback,
            onClose = { feedback.musicPanel = false },
            albumProfile = profile,
            onAlbumProfileChange = ::updateProfile,
        )
    } else {
        val track = remember(context, profile.trackName, feedback.musicVersion) {
            SlideshowMusic.selected(context, profile.trackName)
        }
        val musicStatus = when {
            !profile.musicEnabled -> "已关闭"
            track != null -> track.name
            profile.trackName != null -> "曲目不可用"
            else -> "未上传"
        }
        SettingsDialog(onClose) {
            LaunchedEffect(Unit) {
                withFrameMillis { }
                returnFocus.requestFocus()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
                contentAlignment = Alignment.Center,
            ) {
                val shape = RoundedCornerShape(TvDimen.RadiusL)
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .background(TvColor.BgElevated, shape)
                        .border(1.dp, TvColor.Divider, shape)
                        .verticalScroll(rememberScrollState())
                        .padding(TvDimen.SpaceM),
                    verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
                ) {
                    Text("放映设置", fontSize = TvType.Section, fontWeight = FontWeight.SemiBold, color = TvColor.TextPrimary)
                    Text(
                        "$collectionTitle · ${if (savedProfile == null) "使用默认设置" else "已记住此相册的设置"}",
                        fontSize = TvType.Caption,
                        color = TvColor.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SettingsRow(
                        key = SettingsKey.SS_SLIDESHOW_INTERVAL,
                        store = store,
                        feedback = feedback,
                        focused = false,
                        focusRequester = intervalFocus,
                        onFocus = {},
                        valueOverride = SettingsStore.formatInterval(profile.intervalSec),
                        onConfirm = { updateProfile { it.stepInterval(reverse = false) } },
                        onStepLeft = { updateProfile { it.stepInterval(reverse = true) } },
                        onStepRight = { updateProfile { it.stepInterval(reverse = false) } },
                    )
                    SettingsRow(
                        key = SettingsKey.SS_SLIDESHOW_TRANSITION,
                        store = store,
                        feedback = feedback,
                        focused = false,
                        focusRequester = null,
                        onFocus = {},
                        valueOverride = profile.transition.label,
                        onConfirm = { updateProfile { it.stepTransition(reverse = false) } },
                        onStepLeft = { updateProfile { it.stepTransition(reverse = true) } },
                        onStepRight = { updateProfile { it.stepTransition(reverse = false) } },
                    )
                    fun toggleFrame() = updateProfile {
                        val frame = if (it.frame == SlideshowProfile.Frame.NONE) {
                            SlideshowProfile.Frame.WHITE
                        } else {
                            SlideshowProfile.Frame.NONE
                        }
                        it.copy(frame = frame)
                    }
                    SettingsFocusRow(onConfirm = ::toggleFrame, onAdjust = { toggleFrame(); true }) { focused ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = TvDimen.SpaceM),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "相框",
                                Modifier.weight(1f).padding(start = TvDimen.SpaceM),
                                fontSize = TvType.Body,
                                color = if (focused) TvColor.TextPrimary else TvColor.TextSecondary,
                            )
                            ValueChip(profile.frame.label, focused)
                        }
                    }
                    SettingsRow(
                        key = SettingsKey.SS_MUSIC,
                        store = store,
                        feedback = feedback,
                        focused = false,
                        focusRequester = musicFocus,
                        onFocus = {},
                        valueOverride = musicStatus,
                        hintOverride = "选择已上传的曲目，或静音放映",
                        modifier = Modifier.focusProperties { down = playFocus },
                        onConfirm = {
                            returnFocus = musicFocus
                            feedback.musicPanel = true
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = TvDimen.SpaceXs),
                        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "← → 调整 · 返回关闭",
                            modifier = Modifier.weight(1f),
                            fontSize = TvType.Caption,
                            color = TvColor.TextSecondary,
                        )
                        DetailAction("预览效果", focusRequester = previewFocus, onClick = {
                            returnFocus = previewFocus
                            previewOpen = true
                        })
                        if (savedProfile != null) {
                            TvFocusBox(
                                modifier = Modifier.heightIn(min = TvDimen.CinemaHeaderHeight),
                                cornerRadiusDp = 10,
                                onConfirm = {
                                    intervalFocus.requestFocus()
                                    store.setAlbumSlideshow(slideshowKey, null)
                                },
                            ) {
                                Text(
                                    "恢复默认",
                                    modifier = Modifier.align(Alignment.Center).padding(TvDimen.SpaceS),
                                    fontSize = TvType.Caption,
                                    color = TvColor.TextPrimary,
                                )
                            }
                        }
                        TvFocusBox(
                            modifier = Modifier
                                .heightIn(min = TvDimen.CinemaHeaderHeight)
                                .background(TvColor.Accent, RoundedCornerShape(TvDimen.RadiusS)),
                            cornerRadiusDp = 10,
                            focusedScale = 1.02f,
                            focusRequester = playFocus,
                            onUp = { musicFocus.requestFocus() },
                            onConfirm = onPlay,
                        ) {
                            Text(
                                "开始放映",
                                modifier = Modifier.align(Alignment.Center)
                                    .padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceS),
                                fontSize = TvType.Caption,
                                fontWeight = FontWeight.SemiBold,
                                color = TvColor.InkOnAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}
