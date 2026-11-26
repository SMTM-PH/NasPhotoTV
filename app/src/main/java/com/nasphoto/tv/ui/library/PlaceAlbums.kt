/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.library

import com.nasphoto.tv.data.resolvePlaceLabel
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.AlbumType
import com.nasphoto.tv.data.model.MediaItem

internal data class PlaceAlbum(val album: Album, val media: List<MediaItem>)

/** Group the current account's timeline as it arrives, with the newest media as the cover. */
internal fun placeAlbums(timeline: List<MediaItem>): List<PlaceAlbum> =
    timeline.mapNotNull { item -> item.location?.let { resolvePlaceLabel(it)?.let { label -> label to item } } }
        .groupBy { it.first.id }.values
        .sortedByDescending { it.first().first.isCity }
        .map { group ->
            val place = group.firstOrNull { it.first.subtitle.isNotBlank() }?.first ?: group.first().first
            val unique = group.map { it.second }.distinctBy { it.id }
            val cover = unique.first()
            PlaceAlbum(
                album = Album(
                    id = place.id,
                    name = place.title,
                    coverUrl = cover.thumbnailUrl.ifBlank { cover.url },
                    itemCount = unique.size,
                    type = AlbumType.SMART_PLACE,
                    subtitle = place.subtitle,
                    isSmart = true,
                ),
                media = unique,
            )
        }
