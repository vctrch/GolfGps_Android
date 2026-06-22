package com.vctrch.golfgps.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val METERS_PER_YARD = 0.9144
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun formattedYardage(yards: Int): String = yards.toString()

    fun yards(
        from: LatLng,
        to: LatLng,
    ): Int {
        val meters = haversineMeters(from, to)
        return (meters / METERS_PER_YARD).roundToInt()
    }

    fun centroid(coordinates: List<LatLng>): LatLng? {
        if (coordinates.isEmpty()) return null
        val lat = coordinates.sumOf { it.latitude } / coordinates.size
        val lon = coordinates.sumOf { it.longitude } / coordinates.size
        return LatLng(lat, lon)
    }

    fun bearing(
        from: LatLng,
        to: LatLng,
    ): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return Math.toDegrees(atan2(y, x))
    }

    fun coordinate(
        from: LatLng,
        toward: LatLng,
        distanceYards: Int,
    ): LatLng {
        return coordinate(from, bearing(from, toward), distanceYards)
    }

    private fun coordinate(
        origin: LatLng,
        bearingDegrees: Double,
        distanceYards: Int,
    ): LatLng {
        val distanceMeters = distanceYards * METERS_PER_YARD
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(origin.latitude)
        val lon1 = Math.toRadians(origin.longitude)

        val lat2 =
            asin(
                sin(lat1) * cos(distanceMeters / EARTH_RADIUS_METERS) +
                    cos(lat1) * sin(distanceMeters / EARTH_RADIUS_METERS) * cos(bearing),
            )
        val lon2 =
            lon1 +
                atan2(
                    sin(bearing) * sin(distanceMeters / EARTH_RADIUS_METERS) * cos(lat1),
                    cos(distanceMeters / EARTH_RADIUS_METERS) - sin(lat1) * sin(lat2),
                )

        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    private fun haversineMeters(
        from: LatLng,
        to: LatLng,
    ): Double {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val a = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        return EARTH_RADIUS_METERS * 2 * asin(sqrt(a))
    }
}
