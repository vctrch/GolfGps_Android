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

    @Test
    fun isPlayerOnHole_trueWhenStandingOnTee() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.offset(northYards = 380.0)
        val hole = TestFixtures.holeTarget(tee = tee, green = green)

        assertTrue(hole.isPlayerOnHole(tee))
    }

    @Test
    fun isPlayerOnHole_trueWhenMidHole() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.offset(northYards = 380.0)
        val hole = TestFixtures.holeTarget(tee = tee, green = green)
        val midHole = TestFixtures.offset(northYards = 190.0)

        assertTrue(hole.isPlayerOnHole(midHole))
    }

    @Test
    fun isPlayerOnHole_falseWhenFarAway() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.offset(northYards = 380.0)
        val hole = TestFixtures.holeTarget(tee = tee, green = green)
        val farAway = TestFixtures.offset(northYards = 5_000.0)

        assertFalse(hole.isPlayerOnHole(farAway))
    }

    @Test
    fun isPlayerOnHole_withoutTeeUsesGreenProximity() {
        val green = TestFixtures.sampleGreen
        val hole = TestFixtures.holeTarget(tee = null, green = green)
        val near = TestFixtures.offset(northYards = 142.0 + 100.0)
        val far = TestFixtures.offset(northYards = 142.0 + 600.0)

        assertTrue(hole.isPlayerOnHole(near))
        assertFalse(hole.isPlayerOnHole(far))
    }

    @Test
    fun playerYardsToGreen_returnsDistanceWhenOnHole() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.offset(northYards = 380.0)
        val hole = TestFixtures.holeTarget(tee = tee, green = green)
        val midHole = TestFixtures.offset(northYards = 190.0)

        assertEquals(GeoMath.yards(midHole, green), hole.playerYardsToGreen(midHole))
    }

    @Test
    fun playerYardsToGreen_nullWhenOffHole() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.offset(northYards = 380.0)
        val hole = TestFixtures.holeTarget(tee = tee, green = green)
        val farAway = TestFixtures.offset(northYards = 5_000.0)

        assertNull(hole.playerYardsToGreen(farAway))
    }
}
