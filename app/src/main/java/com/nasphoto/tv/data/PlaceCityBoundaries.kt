/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import java.io.Reader

/** Bundled city boundaries in WGS84; never sends the photo's coordinates to a service. */
internal class PlaceCityBoundaries private constructor(private val polygons: List<Polygon>) {
    fun find(latitude: Double, longitude: Double): City? =
        polygons.firstOrNull { it.contains(latitude, longitude) }?.city

    data class City(val code: String, val name: String, val region: String)

    private data class Polygon(val city: City, val bounds: DoubleArray, val rings: List<DoubleArray>) {
        fun contains(latitude: Double, longitude: Double): Boolean =
            longitude >= bounds[0] && latitude >= bounds[1] &&
                longitude <= bounds[2] && latitude <= bounds[3] &&
                rings.first().containsPoint(latitude, longitude) &&
                rings.drop(1).none { it.containsPoint(latitude, longitude) }

        private fun DoubleArray.containsPoint(latitude: Double, longitude: Double): Boolean {
            var inside = false
            var previous = size - 2
            for (current in indices step 2) {
                val y = this[current + 1]
                val previousY = this[previous + 1]
                if ((y > latitude) != (previousY > latitude)) {
                    val edgeX = this[current] +
                        (this[previous] - this[current]) * (latitude - y) / (previousY - y)
                    if (longitude < edgeX) inside = !inside
                }
                previous = current
            }
            return inside
        }
    }

    companion object {
        fun read(reader: Reader): PlaceCityBoundaries = reader.buffered().useLines { lines ->
            PlaceCityBoundaries(lines.filterNot { it.startsWith('#') }.map { line ->
                val (code, name, region, bounds, vertices) = line.split('\t', limit = 5)
                Polygon(City(code, name, region), bounds.coordinates(), vertices.split('|').map { it.coordinates() })
            }.toList())
        }

        private fun String.coordinates(): DoubleArray = split(',').map(String::toDouble).toDoubleArray()
    }
}
