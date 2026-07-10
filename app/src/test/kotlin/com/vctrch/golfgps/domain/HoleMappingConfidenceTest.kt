package com.vctrch.golfgps.domain

import com.vctrch.golfgps.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoleMappingConfidenceTest {
    @Test
    fun greenConfidence_mapsFromSource() {
        assertEquals(
            GreenMappingConfidence.LOADING,
            TestFixtures.holeTarget(source = HoleTargetSource.SCORECARD_FALLBACK).greenMappingConfidence,
        )
        assertEquals(
            GreenMappingConfidence.ESTIMATED,
            TestFixtures.holeTarget(source = HoleTargetSource.OPEN_STREET_MAP_INFERRED).greenMappingConfidence,
        )
        assertEquals(
            GreenMappingConfidence.MAPPED,
            TestFixtures.holeTarget(source = HoleTargetSource.OPEN_STREET_MAP).greenMappingConfidence,
        )
    }

    @Test
    fun teeConfidence_requiresMappedGreen() {
        val fallback = TestFixtures.holeTarget(tee = TestFixtures.sampleTee)
        assertEquals(TeeMappingConfidence.UNAVAILABLE, fallback.teeMappingConfidence)

        val mappedNoTee =
            TestFixtures.holeTarget(source = HoleTargetSource.OPEN_STREET_MAP, tee = null)
        assertEquals(TeeMappingConfidence.NOT_MAPPED, mappedNoTee.teeMappingConfidence)

        val tagged =
            TestFixtures.holeTarget(
                source = HoleTargetSource.OPEN_STREET_MAP,
                tee = TestFixtures.sampleTee,
                teeSource = TeeMappingSource.TAGGED,
            )
        assertEquals(TeeMappingConfidence.MAPPED, tagged.teeMappingConfidence)
        assertTrue(tagged.hasConfirmedTeeOnMap)
        assertFalse(tagged.showsEstimatedQualifier)
    }

    @Test
    fun loadedCourse_countsConfidenceBuckets() {
        val course =
            LoadedCourse(
                summary = TestFixtures.summary(),
                scorecard = TestFixtures.scorecard(),
                holes =
                    listOf(
                        TestFixtures.holeTarget(number = 1, source = HoleTargetSource.OPEN_STREET_MAP),
                        TestFixtures.holeTarget(number = 2, source = HoleTargetSource.OPEN_STREET_MAP_INFERRED),
                        TestFixtures.holeTarget(number = 3, source = HoleTargetSource.SCORECARD_FALLBACK),
                    ),
            )
        assertEquals(1, course.greenMappedCount)
        assertEquals(1, course.greenEstimatedCount)
        assertEquals(1, course.greenLoadingCount)
    }
}
