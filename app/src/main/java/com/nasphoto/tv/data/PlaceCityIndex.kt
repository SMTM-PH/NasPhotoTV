/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.PhotoLocation
import java.io.Reader
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

internal data class PlaceLabel(
    val id: String,
    val title: String,
    val subtitle: String,
    val isCity: Boolean = false,
)

/** The bundled city points and boundaries are queried on-device. */
internal class PlaceCityIndex private constructor(
    private val cities: List<City>,
    private val boundaries: PlaceCityBoundaries?,
) {
    fun findBoundary(latitude: Double, longitude: Double, countryCode: String, name: String): PlaceLabel? {
        if (countryCode.isNotEmpty() && countryCode != "CN") return null
        val city = boundaries?.find(latitude, longitude) ?: return null
        if (name.isNotBlank() && normalizeCityName(name) != normalizeCityName(city.name)) return null
        return PlaceLabel(
            id = "city:CN:${city.code}",
            title = city.name,
            subtitle = placeSubtitle(city.name, city.region, "CN"),
            isCity = true,
        )
    }

    fun find(latitude: Double, longitude: Double, countryCode: String, name: String = ""): City? {
        val targetName = normalizeCityName(name)
        val lower = latitude - SearchDegrees
        val upper = latitude + SearchDegrees
        val from = cities.binarySearchBy(lower, selector = City::latitude).let { if (it >= 0) it else -it - 1 }
        val longitudeScale = cos(Math.toRadians(latitude))
        var closest: City? = null
        var closestDistance = Double.POSITIVE_INFINITY
        for (index in from until cities.size) {
            val city = cities[index]
            if (city.latitude > upper) break
            if (countryCode.isNotBlank() && city.countryCode != countryCode) continue
            if (targetName.isNotEmpty() && !city.matches(targetName)) continue
            val north = (city.latitude - latitude) * KilometersPerDegree
            val longitudeDelta = abs(city.longitude - longitude)
            val east = minOf(longitudeDelta, 360.0 - longitudeDelta) * longitudeScale * KilometersPerDegree
            val distance = north * north + east * east
            val radius = if (targetName.isNotEmpty()) SearchKilometers else city.radius
            if (distance <= radius * radius && distance < closestDistance) {
                closest = city
                closestDistance = distance
            }
        }
        return closest
    }

    data class City(
        val latitude: Double,
        val longitude: Double,
        val name: String,
        val asciiName: String,
        val countryCode: String,
        val population: Int,
    ) {
        val radius: Double get() = when {
            population >= 5_000_000 -> 55.0
            population >= 1_000_000 -> 40.0
            population >= 100_000 -> 29.0
            population >= 10_000 -> 19.0
            else -> 12.0
        }

        fun matches(normalizedName: String): Boolean =
            normalizeCityName(name) == normalizedName || normalizeCityName(asciiName) == normalizedName

        fun label(region: String): PlaceLabel = PlaceLabel(
            id = "city:$countryCode:$latitude:$longitude",
            title = name,
            subtitle = placeSubtitle(name, region, countryCode),
            isCity = true,
        )
    }

    companion object {
        private const val KilometersPerDegree = 111.2
        private const val SearchKilometers = 75.0
        private const val SearchDegrees = SearchKilometers / KilometersPerDegree

        fun read(reader: Reader, boundaries: PlaceCityBoundaries? = null): PlaceCityIndex = reader.buffered().useLines { lines ->
            PlaceCityIndex(lines.filterNot { it.startsWith('#') }.mapNotNull { line ->
                val fields = line.split('\t')
                if (fields.size != 6) return@mapNotNull null
                val latitude = fields[0].toDoubleOrNull() ?: return@mapNotNull null
                val longitude = fields[1].toDoubleOrNull() ?: return@mapNotNull null
                val population = fields[5].toIntOrNull() ?: return@mapNotNull null
                City(latitude, longitude, fields[2], fields[3], fields[4], population)
            }.sortedBy(City::latitude).toList(), boundaries)
        }
    }
}

internal object OfflinePlaceCities {
    val index: PlaceCityIndex? by lazy {
        val assets = AppContextHolder.context?.assets ?: return@lazy null
        val boundaries = runCatching {
            assets.open("place-boundaries-cn.tsv").reader().let(PlaceCityBoundaries::read)
        }.getOrNull()
        runCatching { PlaceCityIndex.read(assets.open("place-cities.tsv").reader(), boundaries) }.getOrNull()
    }
}

internal fun resolvePlaceLabel(
    location: PhotoLocation?,
    latitude: Double? = location?.latitude,
    longitude: Double? = location?.longitude,
    cities: PlaceCityIndex? = OfflinePlaceCities.index,
): PlaceLabel? {
    val country = location?.countryCode.orEmpty()
    val region = location?.region.orEmpty()
    val locality = location?.locality.orEmpty()
    val hasCoordinates = latitude != null && longitude != null && latitude in -90.0..90.0 &&
        longitude in -180.0..180.0
    if (hasCoordinates && (latitude != 0.0 || longitude != 0.0)) {
        val lat = checkNotNull(latitude)
        val lon = checkNotNull(longitude)
        cities?.findBoundary(lat, lon, country, locality)?.let { return it }
        cities?.find(lat, lon, country, locality)?.let {
            return cities.findBoundary(it.latitude, it.longitude, it.countryCode, it.name) ?: it.label(region)
        }
    }
    if (locality.isNotBlank() || region.isNotBlank()) {
        val title = locality.ifBlank { region }
        val scope = listOf(country, region, title).joinToString("") { "${it.length}:$it" }
        return PlaceLabel("named:$scope", title, placeSubtitle(title, region, country), locality.isNotBlank())
    }
    if (hasCoordinates) {
        return PlaceLabel("unidentified:$country", "未识别城市", placeSubtitle("", "", country))
    }
    return country.takeIf(String::isNotBlank)?.let {
        PlaceLabel("country:$it", Locale("", it).getDisplayCountry(Locale.SIMPLIFIED_CHINESE), "")
    }
}

private fun normalizeCityName(value: String): String = value.trim().lowercase(Locale.ROOT).removeSuffix("市")

private fun placeSubtitle(city: String, region: String, countryCode: String): String {
    val country = countryCode.takeIf(String::isNotBlank)?.let {
        Locale("", it).getDisplayCountry(Locale.SIMPLIFIED_CHINESE)
    }.orEmpty()
    return listOf(region, country).distinct()
        .filter { it.isNotBlank() && normalizeCityName(it) != normalizeCityName(city) }.joinToString(" · ")
}
