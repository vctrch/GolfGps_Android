package com.vctrch.golfgps.domain

import com.vctrch.golfgps.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {
    @Test
    fun yards_returnsPositiveDistance() {
        val from = LatLng(32.8328, -117.2713)
        val to = LatLng(32.8338, -117.2703)
        val yards = GeoMath.yards(from, to)
        assert(yards in 100..500)
    }

    @Test
    fun formattedYardage_hasNoGrouping() {
        assertEquals("142", GeoMath.formattedYardage(142))
    }
}
