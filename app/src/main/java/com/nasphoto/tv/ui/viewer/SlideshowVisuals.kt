/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.viewer

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.SlideshowProfile
import com.nasphoto.tv.data.model.ScreensaverSettings.SlideshowTransition

internal fun slideshowTransition(transition: SlideshowTransition): ContentTransform =
    if (transition == SlideshowTransition.CUT) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        val spec = tween<Float>(900, easing = FastOutSlowInEasing)
        fadeIn(spec) togetherWith fadeOut(spec)
    }

@Composable
internal fun SlideshowPhotoFrame(
    frame: SlideshowProfile.Frame,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (frame == SlideshowProfile.Frame.NONE) {
        Box(modifier, contentAlignment = Alignment.Center) { content() }
    } else {
        Box(modifier.padding(horizontal = 24.dp, vertical = 18.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .aspectRatio(aspectRatio)
                    .border(4.dp, Color(0xFFF5F2EE))
                    .padding(4.dp)
                    .clipToBounds(),
            ) { content() }
        }
    }
}

@Composable
internal fun ShowcaseZoom(
    enabled: Boolean,
    key: Any,
    intervalMs: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(enabled, key, intervalMs) {
        scale.snapTo(1f)
        if (enabled) scale.animateTo(1.05f, tween(intervalMs, easing = LinearEasing))
    }
    Box(
        modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}
