/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.PhotoLocation
import java.io.StringReader
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FnOsMapPlacesTest {
    @Test
    fun resolvesRuralPointsByCityBoundaryAndRespectsNasCityAndPolygonHoles() {
        val boundaries = PlaceCityBoundaries.read(StringReader(
            "430100\t长沙\t湖南省\t110,27,115,30\t110,27,115,27,115,30,110,30|112,28,113,28,113,29,112,29",
        ))
        val cities = PlaceCityIndex.read(StringReader(""), boundaries)

        val rural = resolvePlaceLabel(null, 29.5, 114.5, cities)
        assertEquals("city:CN:430100", rural?.id)
        assertEquals("长沙", rural?.title)
        assertEquals("湖南省 · 中国", rural?.subtitle)
        assertEquals(true, rural?.isCity)
        assertEquals(false, resolvePlaceLabel(null, 28.5, 112.5, cities)?.isCity)
        val nas = PhotoLocation("CN", "飞牛地名", "", 29.5, 114.5)
        assertEquals("飞牛地名", resolvePlaceLabel(nas, cities = cities)?.title)
        assertEquals("未识别城市", resolvePlaceLabel(null, 0.0, 0.0, cities)?.title)
    }

    @Test
    fun mergesCityPointsAndDoesNotReadAnotherCityInsideTheirBoundingBox() = runBlocking<Unit> {
        val cities = PlaceCityIndex.read(StringReader("31.22\t121.43\t上海\tShanghai\tCN\t20000000"))
        val detailBounds = mutableListOf<Pair<String, String>>()
        val map = FnOsMapPlaces(
            getJson = { path, params ->
                val query = params.toMap()
                when (path.substringAfterLast('/')) {
                    "mapBoundary" -> JSONObject("""{"minLat":31,"maxLat":40,"minLon":100,"maxLon":122}""")
                    "previewAllPoint" -> JSONObject(
                        """{"list":[
                            {"lat":31.19,"lon":121.35,"count":1,"poster":{"location":{"city":"上海市","countryCode":"CN"}}},
                            {"lat":31.23,"lon":121.45,"count":1},
                            {"lat":31.21,"lon":121.40,"count":1,"cityName":"另一座城"},
                            {"lat":39.99,"lon":100.85,"count":1}
                        ]}""",
                    )
                    "photoList" -> {
                        val west = query.getValue("west")
                        val east = query.getValue("east")
                        detailBounds += west to east
                        val id = when (west) {
                            "121.35" -> 1
                            "121.45" -> 2
                            else -> error("Read the wrong city's bounds")
                        }
                        JSONObject("""{"list":[{"id":$id}]}""")
                    }
                    else -> error("Unexpected API")
                }
            },
            parseMedia = { MediaItem(it.getLong("id").toString(), "", MediaType.PHOTO, "") },
            baseUrl = "https://nas.test",
            cityIndex = { cities },
        )

        val albums = map.getAlbums(revision = 1)
        val shanghai = albums.first()
        assertEquals("上海", shanghai.name)
        assertEquals(2, shanghai.itemCount)
        assertEquals(listOf("上海", "另一座城", "未识别城市"), albums.map { it.name })
        assertEquals(listOf("1", "2"), map.getMedia(shanghai.id, 1).map { it.id })
        assertEquals(listOf("121.35" to "121.35", "121.45" to "121.45"), detailBounds)
    }

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
                            "poster":{"id":7,"photoUUID":"sample","dateTime":"2026:11:20 00:00:00"}},
                            {"lat":30,"lon":110,"count":1}]}""",
                        )
                    }
                    "photoList" -> {
                        val west = query.getValue("west")
                        assertEquals(west, query["east"])
                        assertEquals("30.0", query["south"])
                        assertEquals("0", query["lonVert"])
                        detailOffsets += query.getValue("offset")
                        val id = when (west) {
                            "80.0" -> if (query["offset"] == "0") 7 else 8
                            "110.0" -> 9
                            else -> error("Unidentified points should load in their own areas")
                        }
                        JSONObject("""{"list":[{"id":$id}]}""")
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
        assertEquals(3, album.itemCount)
        assertEquals("map:unidentified:", album.id)
        assertEquals("https://nas.test/p/api/v1/stream/p/t/7/xs/sample", album.coverUrl)
        assertEquals(listOf("7", "8", "9"), map.getMedia(album.id, revision = 1).map { it.id })
        assertEquals(listOf("0", "1", "0"), detailOffsets)
        assertEquals(2, areaRequests.size)
    }
}
