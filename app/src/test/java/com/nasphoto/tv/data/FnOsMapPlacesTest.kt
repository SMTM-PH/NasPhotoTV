package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FnOsMapPlacesTest {
    @Test
    fun splitsLargeBoundsDeduplicatesEdgesAndLoadsMediaByPlace() = runBlocking<Unit> {
        val areaRequests = mutableListOf<Map<String, String>>()
        val detailOffsets = mutableListOf<String>()
        val map = FnOsMapPlaces(
            getJson = { path, params ->
                val query = params.toMap()
                when (path.substringAfterLast('/')) {
                    "mapBoundary" -> JSONObject("""{"minLat":30,"maxLat":30,"minLon":20,"maxLon":120}""")
                    "previewAllPoint" -> {
                        areaRequests += query
                        JSONObject(
                            """{"list":[{"lat":30,"lon":80,"count":2,
                            "poster":{"id":7,"photoUUID":"sample","dateTime":"2026:11:20 00:00:00"}}]}""",
                        )
                    }
                    "photoList" -> {
                        assertEquals("80.0", query["west"])
                        assertEquals("80.0", query["east"])
                        assertEquals("30.0", query["south"])
                        assertEquals("0", query["lonVert"])
                        detailOffsets += query.getValue("offset")
                        JSONObject("""{"list":[{"id":${if (query["offset"] == "0") 7 else 8}}]}""")
                    }
                    else -> error("Unexpected API")
                }
            },
            parseMedia = { MediaItem(it.getLong("id").toString(), "", MediaType.PHOTO, "") },
            baseUrl = "https://nas.test",
        )

        val album = map.getAlbums(revision = 1).single()
        assertEquals(listOf("20.0", "80.0"), areaRequests.map { it["west"] })
        assertEquals(listOf("80.0", "120.0"), areaRequests.map { it["east"] })
        assertEquals(2, album.itemCount)
        assertEquals("map:300:800", album.id)
        assertEquals("https://nas.test/p/api/v1/stream/p/t/7/xs/sample", album.coverUrl)
        assertEquals(listOf("7", "8"), map.getMedia(album.id, revision = 1).map { it.id })
        assertEquals(listOf("0", "1"), detailOffsets)
        assertEquals(2, areaRequests.size)
    }
}
