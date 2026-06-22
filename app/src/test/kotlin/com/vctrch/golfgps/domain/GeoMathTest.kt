package com.vctrch.golfgps.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GeoMathTest {
    @Test
    fun yards_returnsPositiveDistance() {
        val from = LatLng(32.8328, -117.2713)
        val to = LatLng(32.8338, -117.2703)
        val yards = GeoMath.yards(from, to)
        assertTrue(yards in 100..500)
    }

    @Test
    fun yards_samePoint_isZero() {
        val point = LatLng(32.8328, -117.2713)
        assertEquals(0, GeoMath.yards(point, point))
    }

    @Test
    fun formattedYardage_hasNoGrouping() {
        assertEquals("142", GeoMath.formattedYardage(142))
    }

    @Test
    fun centroid_averagesCoordinates() {
        val points =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(2.0, 4.0),
            )
        val centroid = GeoMath.centroid(points)
        assertEquals(LatLng(1.0, 2.0), centroid)
    }

    @Test
    fun centroid_emptyList_returnsNull() {
        assertNull(GeoMath.centroid(emptyList()))
    }

    @Test
    fun bearing_north_isNearZero() {
        val from = LatLng(32.0, -117.0)
        val to = LatLng(33.0, -117.0)
        val bearing = GeoMath.bearing(from, to)
        assertTrue(abs(bearing) < 1.0 || abs(bearing - 360.0) < 1.0)
    }

    @Test
    fun coordinate_roundTrip_matchesOriginalDistance() {
        val from = LatLng(32.8328, -117.2713)
        val to = LatLng(32.8338, -117.2703)
        val distance = GeoMath.yards(from, to)
        val projected = GeoMath.coordinate(from, to, distance)
        val roundTrip = GeoMath.yards(from, projected)
        assertTrue(abs(roundTrip - distance) <= 1)
    }
}
