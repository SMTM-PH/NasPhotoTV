/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.SlideshowProfile
import com.nasphoto.tv.ui.library.DetailAction
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

@Composable
internal fun SlideshowPreviewOverlay(profile: SlideshowProfile, onClose: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    var replay by remember { mutableIntStateOf(0) }

    SettingsDialog(onClose) {
        LaunchedEffect(Unit) {
            withFrameMillis { }
            closeFocus.requestFocus()
        }
        Box(
            Modifier.fillMaxSize().padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth()
                    .background(TvColor.BgElevated, RoundedCornerShape(TvDimen.RadiusL))
                    .verticalScroll(rememberScrollState())
                    .padding(TvDimen.SpaceM),
                verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
            ) {
                Text("放映效果预览", color = TvColor.TextPrimary, fontSize = TvType.Section, fontWeight = FontWeight.SemiBold)
                Text(
                    "示例照片 · ${profile.transition.label} · ${profile.frame.label}",
                    color = TvColor.TextSecondary,
                    fontSize = TvType.Caption,
                )
                SlideshowMotionSample(profile, replay, Modifier.fillMaxWidth().height(230.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS)) {
                    Spacer(Modifier.weight(1f))
                    DetailAction("再看一次", onClick = { replay++ })
                    DetailAction("返回设置", focusRequester = closeFocus, onClick = onClose)
                }
            }
        }
    }
}
