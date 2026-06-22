package com.vctrch.golfgps.domain

import com.vctrch.golfgps.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoleTargetTest {
    @Test
    fun hasReliableGreenPosition_trueForOsmSources() {
        val osmHole = TestFixtures.holeTarget(source = HoleTargetSource.OPEN_STREET_MAP)
        val inferredHole = TestFixtures.holeTarget(source = HoleTargetSource.OPEN_STREET_MAP_INFERRED)

        assertTrue(osmHole.hasReliableGreenPosition)
        assertTrue(inferredHole.hasReliableGreenPosition)
    }

    @Test
    fun hasReliableGreenPosition_falseForScorecardFallback() {
        val fallback = TestFixtures.holeTarget(source = HoleTargetSource.SCORECARD_FALLBACK)
        assertFalse(fallback.hasReliableGreenPosition)
    }

    @Test
    fun holeLengthYards_nullWithoutTee() {
        val hole = TestFixtures.holeTarget(tee = null)
        assertNull(hole.holeLengthYards())
    }

    @Test
    fun holeLengthYards_computesTeeToGreenDistance() {
        val tee = LatLng(32.8328, -117.2713)
        val green = LatLng(32.8338, -117.2703)
        val hole = TestFixtures.holeTarget(tee = tee, green = green)

        assertEquals(GeoMath.yards(tee, green), hole.holeLengthYards())
    }
}
