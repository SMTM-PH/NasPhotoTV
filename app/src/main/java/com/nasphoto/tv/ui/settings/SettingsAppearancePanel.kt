/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.ui.theme.DarkPalette
import com.nasphoto.tv.ui.theme.LightPalette
import com.nasphoto.tv.ui.theme.ProvideTvPalette
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import com.nasphoto.tv.ui.theme.resolveTvPalette

/**
 * 设置 → 外观：两块用真实 token 渲染的迷你预览并排展示，
 * 让用户在不离开设置页的情况下看清两套配色的差别。
 *
 * 右栏本身跟随当前外观；预览块各自用 [ProvideTvPalette] 强制指定调色板。
 */
@Composable
fun SettingsAppearancePanel(store: SettingsStore) {
    val current = resolveTvPalette(store.appearance)
    Column(
        Modifier
            .fillMaxSize()
            .padding(TvDimen.SpaceS),
        verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
    ) {
        Text(
            "外观模式",
            fontSize = TvType.Section,
            fontWeight = FontWeight.SemiBold,
            color = TvColor.TextPrimary,
        )
        Text("当前：${store.appearance.fullLabel}", fontSize = TvType.Body, color = TvColor.TextSecondary)

        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
        ) {
            // RowScope 的 weight 必须在 ProvideTvPalette 的 content lambda 之外取好。
            val lightMod = Modifier.weight(1f)
            val darkMod = Modifier.weight(1f)
            ProvideTvPalette(LightPalette) {
                AppearanceMiniPreview(
                    title = "浅色 · 画廊白",
                    selected = current.isLight,
                    modifier = lightMod,
                )
            }
            ProvideTvPalette(DarkPalette) {
                AppearanceMiniPreview(
                    title = "深色 · 影院黑",
                    selected = !current.isLight,
                    modifier = darkMod,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(DarkPalette.stage),
            )
            Spacer(Modifier.width(TvDimen.SpaceXs))
            Text(
                "看图、播放和屏保始终使用深色背景。",
                fontSize = TvType.Caption,
                color = TvColor.TextSecondary,
            )
        }
    }
}

@Composable
private fun AppearanceMiniPreview(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(TvDimen.RadiusL))
            .background(TvColor.Bg)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) TvColor.FocusRing else TvColor.Divider,
                shape = RoundedCornerShape(TvDimen.RadiusL),
            )
            .padding(TvDimen.SpaceS),
        verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
    ) {
        Text(title, fontSize = TvType.Caption, fontWeight = FontWeight.SemiBold, color = TvColor.TextPrimary)

        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
        ) {
            PreviewPhoto(Brush.linearGradient(listOf(TvColor.AccentAlt, TvColor.Accent)), focused = selected, weight = 1.4f)
            PreviewPhoto(Brush.linearGradient(listOf(TvColor.Warm, TvColor.Warn)), focused = false, weight = 1f)
            PreviewPhoto(Brush.linearGradient(listOf(TvColor.Accent, TvColor.Stage)), focused = false, weight = 1f)
        }

        Text(
            if (selected) "当前使用" else "可切换",
            fontSize = TvType.Tiny,
            color = if (selected) TvColor.Accent else TvColor.TextSecondary,
        )
    }
}

@Composable
private fun RowScope.PreviewPhoto(
    brush: Brush,
    focused: Boolean,
    weight: Float,
) {
    Box(
        Modifier
            .weight(weight)
            .fillMaxSize()
            .clip(RoundedCornerShape(TvDimen.RadiusS))
            .background(brush)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) TvColor.FocusRing else TvColor.Divider,
                shape = RoundedCornerShape(TvDimen.RadiusS),
            ),
    )
}
