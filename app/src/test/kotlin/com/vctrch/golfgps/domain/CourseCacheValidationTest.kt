package com.vctrch.golfgps.domain

import com.vctrch.golfgps.data.remote.OSMHoleParser
import com.vctrch.golfgps.testing.TestFixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseCacheValidationTest {
    @Test
    fun osmHolesMatchCourse_rejectsWrongFacilityCluster() {
        val summary = TestFixtures.summary()
        val wrongOrigin = TestFixtures.offset(northYards = 6_000.0)
        val wrongCourseHoles =
            (1..9).map { number ->
                TestFixtures.holeTarget(
                    number = number,
                    source = HoleTargetSource.OPEN_STREET_MAP,
                    tee = TestFixtures.offset(northYards = number * 120.0, origin = wrongOrigin),
                    green = TestFixtures.offset(northYards = number * 120.0 + 80.0, origin = wrongOrigin),
                )
            }

        assertFalse(CourseCacheValidation.osmHolesMatchCourse(wrongCourseHoles, summary))
    }

    @Test
    fun osmHolesMatchCourse_acceptsClusterNearCourseCenter() {
        val summary =
            TestFixtures.summary(
                latitude = TestFixtures.sampleOrigin.latitude,
                longitude = TestFixtures.sampleOrigin.longitude,
            )
        val holes =
            (1..9).map { number ->
                TestFixtures.holeTarget(
                    number = number,
                    source = HoleTargetSource.OPEN_STREET_MAP,
                    tee = TestFixtures.offset(northYards = number * 120.0),
                    green = TestFixtures.offset(northYards = number * 120.0 + 80.0),
                )
            }

        assertTrue(CourseCacheValidation.osmHolesMatchCourse(holes, summary))
    }

    @Test
    fun osmHolesMatchCourse_acceptsElongatedLayout() {
        val summary =
            TestFixtures.summary(
                latitude = TestFixtures.sampleOrigin.latitude,
                longitude = TestFixtures.sampleOrigin.longitude,
            )
        val frontNine =
            (1..9).map { number ->
                TestFixtures.holeTarget(
                    number = number,
                    source = HoleTargetSource.OPEN_STREET_MAP,
                    tee = TestFixtures.offset(northYards = number * 100.0),
                    green = TestFixtures.offset(northYards = number * 100.0 + 80.0),
                )
            }
        val backNine =
            (10..18).map { number ->
                TestFixtures.holeTarget(
                    number = number,
                    source = HoleTargetSource.OPEN_STREET_MAP,
                    tee = TestFixtures.offset(northYards = 2_500.0 + (number - 9) * 120.0),
                    green = TestFixtures.offset(northYards = 2_500.0 + (number - 9) * 120.0 + 80.0),
                )
            }

        assertTrue(CourseCacheValidation.osmHolesMatchCourse(frontNine + backNine, summary))
    }

    @Test
    fun osmHolesMatchCourse_acceptsSalinaStyleGapFilledHoles() {
        val summary =
            TestFixtures.summary(
                latitude = TestFixtures.sampleOrigin.latitude,
                longitude = TestFixtures.sampleOrigin.longitude,
            )
        val scorecard = (1..8).map { ScorecardHole(it, 4, null) }
        val center = TestFixtures.sampleOrigin
        val baseline =
            scorecard.map {
                TestFixtures.holeTarget(
                    number = it.number,
                    source = HoleTargetSource.SCORECARD_FALLBACK,
                    green = center,
                )
            }
        val osm =
            listOf(
                TestFixtures.holeTarget(
                    number = 5,
                    source = HoleTargetSource.OPEN_STREET_MAP,
                    tee = TestFixtures.sampleTee,
                    green = TestFixtures.offset(northYards = 150.0),
                ),
                TestFixtures.holeTarget(
                    number = 8,
                    source = HoleTargetSource.OPEN_STREET_MAP,
                    tee = TestFixtures.offset(northYards = 900.0),
                    green = TestFixtures.offset(northYards = 1_050.0),
                ),
            )
        val merged = OSMHoleParser.mergeHoles(baseline, osm, scorecard)

        assertTrue(CourseCacheValidation.osmHolesMatchCourse(merged, summary))
        assertTrue(merged.first { it.number == 6 }.hasReliableGreenPosition)
        assertTrue(merged.first { it.number == 7 }.hasReliableGreenPosition)
    }

    @Test
    fun basicsMatchSearchResult_requiresNearbyCenter() {
        val search = TestFixtures.summary()
        val matching = search.copy(name = "Renamed")
        val drifted = search.copy(latitude = search.latitude + 0.1)

        assertTrue(CourseCacheValidation.basicsMatchSearchResult(matching, search))
        assertFalse(CourseCacheValidation.basicsMatchSearchResult(drifted, search))
    }

    @Test
    fun shouldInvalidateOsmCache_whenCoordinatesDrift() {
        val incoming = TestFixtures.summary()
        assertTrue(
            CourseCacheValidation.shouldInvalidateOsmCache(
                existingLatitude = incoming.latitude + 0.01,
                existingLongitude = incoming.longitude,
                existingOsmId = incoming.osmId,
                existingHolesCount = incoming.holesCount,
                hasOsmHoles = true,
                incoming = incoming,
            ),
        )
        assertFalse(
            CourseCacheValidation.shouldInvalidateOsmCache(
                existingLatitude = incoming.latitude,
                existingLongitude = incoming.longitude,
                existingOsmId = incoming.osmId,
                existingHolesCount = incoming.holesCount,
                hasOsmHoles = true,
                incoming = incoming,
            ),
        )
    }
}
