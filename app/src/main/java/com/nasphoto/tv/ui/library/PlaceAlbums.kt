package com.nasphoto.tv.ui.library

import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.AlbumType
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.PhotoLocation
import com.nasphoto.tv.data.model.locationAreaTitle
import java.util.Locale
import kotlin.math.roundToInt

internal data class PlaceAlbum(val album: Album, val media: List<MediaItem>)

/** Group the current account's timeline as it arrives, with the newest media as the cover. */
internal fun placeAlbums(timeline: List<MediaItem>): List<PlaceAlbum> =
    timeline.mapNotNull { item -> item.location?.let { placeName(it) to item } }
        .groupBy({ it.first }, { it.second })
        .map { (place, media) ->
            val unique = media.distinctBy { it.id }
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

private data class PlaceName(val id: String, val title: String, val subtitle: String)

private fun placeName(location: PhotoLocation): PlaceName {
    val country = location.countryCode.takeIf(String::isNotEmpty)?.let {
        Locale("", it).getDisplayCountry(Locale.SIMPLIFIED_CHINESE)
    }.orEmpty()
    val name = location.locality.ifBlank { location.region }
    if (name.isNotBlank()) {
        val scope = listOf(location.countryCode, location.region, name).joinToString("") { "${it.length}:$it" }
        val description = listOf(location.region, country).distinct()
            .filter { it.isNotBlank() && it != name }.joinToString(" · ")
        return PlaceName("named:$scope", name, description)
    }

    val latitude = location.latitude
    val longitude = location.longitude
    if (latitude != null && longitude != null) {
        // A tenth of a degree groups nearby media and displays an approximate area.
        val north = (latitude * 10).roundToInt()
        val east = (longitude * 10).roundToInt()
        val label = locationAreaTitle(north, east)
        return PlaceName("coordinates:${location.countryCode}:$north:$east", label, country)
    }

    return PlaceName("country:${location.countryCode}", country, "")
}
