/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.library

import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.parseFnOsPhotoLocation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceAlbumsTest {
    @Test
    fun groupsUnrecognizedCoordinatesAndKeepsTheNewestCover() {
        val first = media("1", """{"latitude":"31.2201","longitude":"121.4601","countryCode":"cn"}""")
        val second = media("2", """{"geoLatitude":31.23,"geoLongitude":121.48,"countryCode":"CN"}""")
        val unknown = media("3", """{"latitude":0,"longitude":0,"countryCode":""}""")

        val place = placeAlbums(listOf(first, second, first, unknown)).single()
        assertEquals("未识别城市", place.album.name)
        assertEquals("中国", place.album.subtitle)
        assertEquals(first.url, place.album.coverUrl)
        assertEquals(listOf(first, second), place.media)
        assertEquals(2, place.album.itemCount)
    }

    @Test
    fun separatesSameNamedCitiesInDifferentRegions() {
        val first = media("1", """{"location":{"city":"Springfield","region":"Illinois","countryCode":"US"}}""")
        val second = media("2", """{"location":{"city":"Springfield","region":"Missouri","countryCode":"US"}}""")

        val places = placeAlbums(listOf(first, second))
        assertEquals(listOf("Springfield", "Springfield"), places.map { it.album.name })
        assertNotEquals(places[0].album.id, places[1].album.id)
        assertEquals(listOf("Illinois · 美国", "Missouri · 美国"), places.map { it.album.subtitle })
    }

    @Test
    fun rejectsInvalidCoordinatePairsAndKeepsCountryOnlyMetadata() {
        assertNull(parseFnOsPhotoLocation(JSONObject("""{"latitude":91,"longitude":50,"countryCode":"invalid"}""")))
        val countryOnly = media("1", """{"latitude":null,"longitude":160,"countryCode":"jp"}""")
        assertEquals("日本", placeAlbums(listOf(countryOnly)).single().album.name)
    }

    private fun media(id: String, metadata: String) = MediaItem(
        id = id,
        title = id,
        type = MediaType.PHOTO,
        url = "https://nas.test/$id.jpg",
        location = parseFnOsPhotoLocation(JSONObject(metadata)),
    )
}
