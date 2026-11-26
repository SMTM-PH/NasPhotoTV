/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType

/** Only explicitly approved, currently existing albums contribute photos. Empty stays empty. */
fun displayPhotos(
    albums: List<Album>,
    mediaByAlbum: Map<String, List<MediaItem>>,
    approvedAlbumIds: Set<String>,
): List<MediaItem> = albums.asSequence()
    .filter { it.id in approvedAlbumIds }
    .flatMap { mediaByAlbum[it.id].orEmpty().asSequence() }
    .filter { it.type == MediaType.PHOTO }
    .distinctBy { it.id }
    .sortedByDescending { it.dateTaken }
    .toList()
