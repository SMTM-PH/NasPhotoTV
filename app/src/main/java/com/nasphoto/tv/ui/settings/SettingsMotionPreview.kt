/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.R
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.SlideshowProfile
import com.nasphoto.tv.data.model.ScreensaverSettings.SlideshowTransition
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import com.nasphoto.tv.ui.viewer.ShowcaseZoom
import com.nasphoto.tv.ui.viewer.SlideshowPhotoFrame
import com.nasphoto.tv.ui.viewer.slideshowTransition
import kotlinx.coroutines.delay

private const val PreviewHoldMs = 1500

@Composable
internal fun SettingsSlideshowPanel(store: SettingsStore, focusedKey: SettingsKey, replay: Int) {
    val profile = SlideshowProfile.from(store.screensaver)
    val changingMotion = focusedKey == SettingsKey.SS_SLIDESHOW_TRANSITION
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val previewHeight = (maxHeight * 0.62f).coerceIn(108.dp, 212.dp)
        Column(verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceS)) {
            PanelTitle("放映预览", "${profile.transition.label} · 每张 ${profile.intervalSec} 秒")
            SlideshowMotionSample(
                profile = profile,
                replay = replay,
                modifier = Modifier.fillMaxWidth().height(previewHeight)
                    .clip(RoundedCornerShape(TvDimen.RadiusM)),
                playMotion = changingMotion,
            )
            if (changingMotion) {
                Text("调整动效后，用示例照片播放一次", color = TvColor.TextSecondary, fontSize = TvType.Caption,
                    fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun SlideshowMotionSample(
    profile: SlideshowProfile,
    replay: Int,
    modifier: Modifier = Modifier,
    playMotion: Boolean = true,
) {
    key(profile.transition, profile.frame, replay, playMotion) {
        var photo by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            if (playMotion) {
                delay(PreviewHoldMs.toLong())
                photo = 1
            }
        }
        AnimatedContent(
            targetState = photo,
            modifier = modifier.background(TvColor.Bg).clipToBounds(),
            transitionSpec = { slideshowTransition(profile.transition) },
            label = "slideshowPreview",
        ) { index ->
            val painter = painterResource(
                if (index == 0) R.drawable.slideshow_preview_lake else R.drawable.slideshow_preview_ocean,
            )
            val aspect = painter.intrinsicSize.width / painter.intrinsicSize.height
            SlideshowPhotoFrame(profile.frame, aspect, Modifier.fillMaxSize()) {
                ShowcaseZoom(
                    enabled = playMotion && profile.transition == SlideshowTransition.SHOWCASE,
                    key = index,
                    intervalMs = PreviewHoldMs,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}
