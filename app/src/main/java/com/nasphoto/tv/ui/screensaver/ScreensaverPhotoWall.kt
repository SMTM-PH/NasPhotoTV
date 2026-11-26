/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.screensaver

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.common.MediaPlaceholder
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvImage

/** 用一张主照片和最多两张小照片组合展示当前屏保的相册。 */
@Composable
internal fun ScreensaverPhotoWall(
    photos: List<MediaItem>,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val artwork = remember(photos, index) { wallPhotoUrls(photos, index) }
    BoxWithConstraints(modifier.fillMaxSize().background(TvColor.Bg)) {
        val lead = artwork.firstOrNull() ?: return@BoxWithConstraints
        val inset = (maxWidth / 45).coerceIn(TvDimen.SpaceXs, TvDimen.SpaceM)
        val spacing = Arrangement.spacedBy(TvDimen.SpaceXs)
        Row(Modifier.fillMaxSize().padding(inset), horizontalArrangement = spacing) {
            PhotoWallTile(lead, TvImage.DetailBackdropPx, Modifier.weight(2f).fillMaxHeight())
            if (artwork.size > 1) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = spacing) {
                    artwork.drop(1).forEach { url ->
                        PhotoWallTile(url, TvImage.MediaThumbPx, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoWallTile(url: String, targetWidth: Int, modifier: Modifier) {
    Crossfade(
        targetState = url,
        modifier = modifier.clip(RoundedCornerShape(TvDimen.RadiusS)).background(TvColor.Surface),
        animationSpec = tween(durationMillis = 480),
        label = "photoWallTile",
    ) { imageUrl ->
        MediaImage(
            url = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            targetWidth = targetWidth,
            placeholder = MediaPlaceholder.HOLD,
        )
    }
}

private fun wallPhotoUrls(photos: List<MediaItem>, index: Int): List<String> {
    if (photos.isEmpty()) return emptyList()
    val start = Math.floorMod(index, photos.size)
    val usedIds = HashSet<String>()
    return buildList(3) {
        for (offset in photos.indices) {
            val photo = photos[(start + offset) % photos.size]
            if (photo.type != MediaType.PHOTO) continue
            val url = photo.thumbnailUrl.ifBlank { photo.url }
            if (url.isBlank() || url in this) continue
            if (photo.id.isNotBlank() && !usedIds.add(photo.id)) continue
            add(url)
            if (size == 3) break
        }
    }
}
