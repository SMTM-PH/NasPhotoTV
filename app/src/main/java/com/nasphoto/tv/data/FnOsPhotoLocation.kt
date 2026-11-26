package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.PhotoLocation
import java.util.Locale
import org.json.JSONObject

/** The fnOS gallery model supplies decimal coordinates and an ISO country code. */
internal fun parseFnOsPhotoLocation(json: JSONObject): PhotoLocation? {
    val additional = json.optJSONObject("additional")
    val location = json.optJSONObject("location") ?: additional?.optJSONObject("location")
    val sources = listOfNotNull(json, location, json.optJSONObject("address"), location?.optJSONObject("address"))

    fun firstText(vararg keys: String): String = sources.firstNotNullOfOrNull { source ->
        keys.firstNotNullOfOrNull { key ->
            (source.opt(key) as? String)?.trim()?.take(80)?.takeIf(String::isNotBlank)
        }
    }.orEmpty()

    val coordinates = sources.firstNotNullOfOrNull { source ->
        coordinates(source, "latitude", "longitude")
            ?: coordinates(source, "geoLatitude", "geoLongitude")
            ?: coordinates(source, "lat", "lon")
    }
    val country = firstText("countryCode", "country_code").uppercase(Locale.ROOT).takeIf { it in countryCodes }.orEmpty()
    val locality = firstText("city", "locality", "cityName")
    val region = firstText("province", "region", "provinceName")
    if (coordinates == null && country.isBlank() && locality.isBlank() && region.isBlank()) return null
    return PhotoLocation(country, locality, region, coordinates?.first, coordinates?.second)
}

private val countryCodes by lazy { Locale.getISOCountries().toSet() }

private fun coordinates(json: JSONObject, latitude: String, longitude: String): Pair<Double, Double>? {
    val lat = json.optDouble(latitude, Double.NaN)
    val lon = json.optDouble(longitude, Double.NaN)
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || lat == 0.0 && lon == 0.0) return null
    return lat to lon
}
