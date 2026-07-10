package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.HoleTargetSource
import com.vctrch.golfgps.domain.ScorecardHole
import com.vctrch.golfgps.domain.TeeMappingSource
import com.vctrch.golfgps.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OSMHoleTeeMatcherTest {
    @Test
    fun teeCoordinates_collectsUniqueGolfTees() {
        val tee = TestFixtures.sampleTee
        val nearDuplicate = TestFixtures.offset(northYards = 5.0)
        val farTee = TestFixtures.offset(northYards = 400.0)
        val elements =
            listOf(
                TestFixtures.overpassTeeElement(1, tee.latitude, tee.longitude),
                TestFixtures.overpassTeeElement(null, nearDuplicate.latitude, nearDuplicate.longitude),
                TestFixtures.overpassTeeElement(2, farTee.latitude, farTee.longitude),
                TestFixtures.overpassGreenElement(
                    1,
                    TestFixtures.sampleGreen.latitude,
                    TestFixtures.sampleGreen.longitude,
                ),
            )

        val tees = OSMHoleTeeMatcher.teeCoordinates(elements)

        assertEquals(2, tees.size)
    }

    @Test
    fun taggedTeesByHole_indexesByHoleRef() {
        val tee = TestFixtures.sampleTee
        val elements = listOf(TestFixtures.overpassTeeElement(7, tee.latitude, tee.longitude))

        val tagged = OSMHoleTeeMatcher.taggedTeesByHole(elements)

        assertEquals(tee, tagged[7])
    }

    @Test
    fun refine_keepsTaggedTeeWhenYardageMatches() {
        val green = TestFixtures.offset(northYards = 400.0)
        val taggedTee = TestFixtures.sampleTee
        val hole =
            HoleTarget(
                number = 1,
                par = 4,
                tee = null,
                green = green,
                source = HoleTargetSource.OPEN_STREET_MAP,
            )

        val refined =
            OSMHoleTeeMatcher.refine(
                holes = listOf(hole),
                candidateTees = listOf(taggedTee),
                taggedTeesByHole = mapOf(1 to taggedTee),
                scorecard = listOf(ScorecardHole(number = 1, par = 4, handicap = null, yardage = 400)),
            )

        assertEquals(taggedTee, refined.single().tee)
        assertEquals(TeeMappingSource.TAGGED, refined.single().teeSource)
    }

    @Test
    fun refine_matchesOrphanTeeUsingPublishedYardage() {
        val green = TestFixtures.offset(northYards = 380.0)
        val orphanTee = TestFixtures.sampleTee
        val distractor = TestFixtures.offset(northYards = 120.0, eastYards = 200.0)
        val hole =
            HoleTarget(
                number = 1,
                par = 4,
                tee = null,
                green = green,
                source = HoleTargetSource.OPEN_STREET_MAP,
            )

        val refined =
            OSMHoleTeeMatcher.refine(
                holes = listOf(hole),
                candidateTees = listOf(orphanTee, distractor),
                scorecard = listOf(ScorecardHole(number = 1, par = 4, handicap = null, yardage = 380)),
            )

        assertEquals(orphanTee, refined.single().tee)
        assertEquals(TeeMappingSource.MATCHED, refined.single().teeSource)
    }

    @Test
    fun refine_skipsHolesWithoutReliableGreen() {
        val hole =
            TestFixtures.holeTarget(
                number = 1,
                source = HoleTargetSource.SCORECARD_FALLBACK,
                tee = null,
            )
        assertTrue(!hole.hasReliableGreenPosition)

        val refined =
            OSMHoleTeeMatcher.refine(
                holes = listOf(hole),
                candidateTees = listOf(TestFixtures.sampleTee),
                scorecard = listOf(ScorecardHole(number = 1, par = 4, handicap = null, yardage = 380)),
            )

        assertEquals(null, refined.single().tee)
        assertEquals(null, refined.single().teeSource)
    }

    @Test
    fun refine_returnsUnchangedWhenNoCandidates() {
        val hole =
            TestFixtures.holeTarget(
                number = 1,
                source = HoleTargetSource.OPEN_STREET_MAP,
                tee = TestFixtures.sampleTee,
                teeSource = TeeMappingSource.FAIRWAY,
            )

        val refined =
            OSMHoleTeeMatcher.refine(
                holes = listOf(hole),
                candidateTees = emptyList(),
                scorecard = listOf(ScorecardHole(number = 1, par = 4, handicap = null)),
            )

        assertEquals(hole.tee, refined.single().tee)
        assertNotNull(refined.single().teeSource)
    }
}
