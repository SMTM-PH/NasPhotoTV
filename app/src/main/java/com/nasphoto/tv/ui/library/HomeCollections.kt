package com.nasphoto.tv.ui.library

import com.nasphoto.tv.data.displayPhotos
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.ui.LibraryState
import java.time.LocalDate

internal data class HomeCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<MediaItem>,
) {
    val cover: MediaItem get() = items.first()
}

/** Home features meaningful collections; full library browsing remains separate. */
internal fun homeCollections(state: LibraryState, approvedIds: Set<String>): List<HomeCollection> {
    val photos = displayPhotos(state.albums, state.mediaByAlbum, approvedIds)
    if (photos.isEmpty()) return emptyList()
    val memories = buildMemories(photos, LocalDate.now()).take(6).map {
        HomeCollection("memory:${it.key}", it.title, "${it.items.size} 张照片 · 往年回忆", it.items)
    }
    val albums = state.albums.filter { it.id in approvedIds }.mapNotNull { album ->
        val items = state.mediaByAlbum[album.id].orEmpty().filter { it.type == MediaType.PHOTO }
            .distinctBy { it.id }.sortedBy { it.dateTaken }
        if (items.isEmpty()) null else {
            val first = items.first().dateTaken.toString()
            val last = items.last().dateTaken.toString()
            HomeCollection("album:${album.id}", album.name,
                "${items.size} 张照片 · ${if (first == last) first else "$first — $last"}", items)
        }
    }
    return (memories + albums).take(24)
}
