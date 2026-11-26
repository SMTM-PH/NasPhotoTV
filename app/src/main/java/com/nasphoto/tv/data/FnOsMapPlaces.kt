/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.AlbumType
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.PhotoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

/** Read fnOS map aggregation by area and open a place with the map's paged media endpoint. */
internal class FnOsMapPlaces(
    private val getJson: suspend (String, List<Pair<String, String>>) -> JSONObject,
    private val parseMedia: (JSONObject) -> MediaItem?,
    private val baseUrl: String,
    private val cityIndex: () -> PlaceCityIndex? = { OfflinePlaceCities.index },
) {
    private var index: Index? = null

    suspend fun getAlbums(revision: Int): List<Album> {
        index?.takeIf { it.revision == revision }?.let { return it.places.values.map(Place::album) }
        val boundary = getJson("$MapPath/preview/mapBoundary", emptyList())
        val bounds = Bounds(
            north = boundary.optDouble("maxLat", 90.0).coerceIn(-90.0, 90.0),
            east = boundary.optDouble("maxLon", 180.0).coerceIn(-180.0, 180.0),
            south = boundary.optDouble("minLat", -90.0).coerceIn(-90.0, 90.0),
            west = boundary.optDouble("minLon", -180.0).coerceIn(-180.0, 180.0),
        )
        check(bounds.north >= bounds.south && bounds.east >= bounds.west) { "飞牛地图范围无效" }
        val points = LinkedHashMap<Pair<Double, Double>, Point>()
        for ((south, north) in ranges(bounds.south, bounds.north, 45.0)) {
            for ((west, east) in ranges(bounds.west, bounds.east, 60.0)) {
                val result = getJson("$MapPath/previewAllPoint", Bounds(north, east, south, west).params())
                val list = result.optJSONArray("list") ?: error("飞牛地图未返回聚合点列表")
                for (i in 0 until list.length()) {
                    val json = list.optJSONObject(i) ?: continue
                    val lat = json.optDouble("lat")
                    val lon = json.optDouble("lon")
                    val count = json.optInt("count")
                    if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || count <= 0) continue
                    val poster = json.optJSONObject("poster")
                    points.putIfAbsent(lat to lon, Point(lat, lon, count, poster, mapLocation(json, poster)))
                }
            }
        }
        val places = withContext(Dispatchers.Default) { buildPlaces(points.values) }
        currentCoroutineContext().ensureActive()
        index = Index(revision, places.associateBy { it.album.id })
        return places.map(Place::album)
    }

    suspend fun getMedia(id: String, revision: Int): List<MediaItem> {
        getAlbums(revision)
        val current = checkNotNull(index)
        current.media[id]?.let { return it }
        val place = current.places[id] ?: error("这个地点已更新，请重新打开")
        val media = LinkedHashMap<String, MediaItem>()
        for (area in place.areas) {
            for (item in getAreaMedia(area)) media.putIfAbsent(item.id, item)
        }
        check(media.size >= place.album.itemCount) { "飞牛地点媒体未读完整，请刷新后重试" }
        currentCoroutineContext().ensureActive()
        return media.values.sortedByDescending { it.dateTaken }.also { current.media[id] = it }
    }

    private suspend fun getAreaMedia(area: Area): Collection<MediaItem> {
        val media = LinkedHashMap<String, MediaItem>()
        var offset = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val params = area.bounds.params() + listOf(
                "startTime" to "1970:01:01 00:00:00",
                "endTime" to "2099:12:31 23:59:59",
                "offset" to offset.toString(),
                "limit" to "500",
            )
            val result = getJson("$MapPath/photoList", params)
            val list = result.optJSONArray("list") ?: error("飞牛地图未返回媒体列表")
            if (list.length() == 0) break
            val before = media.size
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i)?.let(parseMedia) ?: continue
                media.putIfAbsent(item.id, item)
            }
            check(media.size > before) { "飞牛地点媒体未正确翻页，请刷新后重试" }
            if (media.size >= area.itemCount) break
            offset += list.length()
        }
        check(media.size >= area.itemCount) { "飞牛地点媒体未读完整，请刷新后重试" }
        return media.values
    }

    private suspend fun buildPlaces(points: Collection<Point>): List<Place> {
        val cities = cityIndex()
        val groups = points.map { point ->
            val label = checkNotNull(resolvePlaceLabel(point.location, point.lat, point.lon, cities))
            LabeledPoint(point, label)
        }.groupBy { it.label.id }
        return groups.map { (id, matching) ->
            currentCoroutineContext().ensureActive()
            val label = matching.firstOrNull { it.label.subtitle.isNotBlank() }?.label ?: matching.first().label
            val group = matching.map(LabeledPoint::point)
            val newest = group.maxBy { it.poster?.optString("dateTime").orEmpty() }.poster
            val album = Album(
                id = "map:$id",
                name = label.title,
                subtitle = label.subtitle,
                coverUrl = newest?.let { posterUrl(it, baseUrl) }.orEmpty(),
                itemCount = group.itemCount(),
                type = AlbumType.SMART_PLACE,
                isSmart = true,
            )
            val bounds = group.bounds()
            val foreign = if (group.size > 1) {
                groups.asSequence().filter { it.key != id }.flatMap { it.value.asSequence() }
                    .map(LabeledPoint::point).filter(bounds::contains).toList()
            } else {
                emptyList()
            }
            val areas = if (foreign.isEmpty() && !id.startsWith("unidentified:")) {
                listOf(Area(bounds, album.itemCount))
            } else {
                // fnOS only accepts a rectangle. Separate overlapping group bounds so details
                // remain exact even when the returned photo has no coordinates.
                group.groupBy(Point::gridCell).values.flatMap { cell ->
                    val cellBounds = cell.bounds()
                    if (foreign.none(cellBounds::contains)) {
                        listOf(Area(cellBounds, cell.itemCount()))
                    } else {
                        cell.map { Area(listOf(it).bounds(), it.count) }
                    }
                }
            }
            Place(album, areas, newest?.optString("dateTime").orEmpty(), label.isCity)
        }.sortedWith(compareByDescending<Place> { it.isCity }.thenByDescending { it.latestDate }.thenBy { it.album.id })
    }

    private fun mapLocation(point: JSONObject, poster: JSONObject?): PhotoLocation? {
        val direct = parseFnOsPhotoLocation(point)
        val cover = poster?.let(::parseFnOsPhotoLocation)
        if (direct == null || cover == null) return direct ?: cover
        val useCoverCity = direct.locality.isBlank() && cover.locality.isNotBlank()
        val named = if (useCoverCity) cover else direct
        val other = if (useCoverCity) direct else cover
        return named.copy(
            countryCode = named.countryCode.ifBlank { other.countryCode },
            region = named.region.ifBlank { other.region },
        )
    }

    private fun List<Point>.bounds() = Bounds(maxOf(Point::lat), maxOf(Point::lon), minOf(Point::lat), minOf(Point::lon))

    private fun List<Point>.itemCount() = sumOf { it.count.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun ranges(min: Double, max: Double, step: Double): List<Pair<Double, Double>> =
        generateSequence(min) { start -> (start + step).takeIf { it < max } }
            .map { start -> start to minOf(start + step, max) }.toList()

    private data class Index(val revision: Int, val places: Map<String, Place>) {
        val media = mutableMapOf<String, List<MediaItem>>()
    }
    private data class Point(val lat: Double, val lon: Double, val count: Int, val poster: JSONObject?, val location: PhotoLocation?) {
        val gridCell get() = (lat * 10).roundToInt() to (lon * 10).roundToInt()
    }
    private data class LabeledPoint(val point: Point, val label: PlaceLabel)
    private data class Area(val bounds: Bounds, val itemCount: Int)
    private data class Place(val album: Album, val areas: List<Area>, val latestDate: String, val isCity: Boolean)
    private data class Bounds(val north: Double, val east: Double, val south: Double, val west: Double) {
        fun contains(point: Point): Boolean = point.lat in south..north && point.lon in west..east

        fun params() = listOf(
            "north" to north.toString(),
            "east" to east.toString(),
            "south" to south.toString(),
            "west" to west.toString(),
            "lonVert" to "0",
        )
    }

    private companion object {
        const val MapPath = "/p/api/v1/map"
    }
}
