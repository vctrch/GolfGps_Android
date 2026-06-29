package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.*
import com.vctrch.golfgps.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from the iOS `OSMHoleParserTests` so Android matches the reference behavior. */
class OSMHoleParserTest {
    private val center = TestFixtures.sampleOrigin

    @Test
    fun orientsTeeNearCourseCenterOnHoleOne() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.sampleGreen
        val elements =
            listOf(
                TestFixtures.overpassHoleElement(1, tee.latitude, tee.longitude, green.latitude, green.longitude),
            )

        val holes =
            OSMHoleParser.holeTargets(
                elements = elements,
                scorecard = listOf(ScorecardHole(1, 4, null)),
                courseCenter = center,
            )

        assertEquals(1, holes.size)
        assertEquals(1, holes[0].number)
        assertEquals(HoleTargetSource.OPEN_STREET_MAP, holes[0].source)
        assertTrue(holes[0].hasReliableGreenPosition)
        assertTrue(GeoMath.yards(center, holes[0].tee!!) < GeoMath.yards(center, holes[0].green))
        assertTrue(GeoMath.yards(holes[0].tee!!, holes[0].green) in 100..200)
    }

    @Test
    fun snapsGreenToNearestUnnumberedGreenPolygonCentre() {
        val tee = TestFixtures.sampleOrigin
        // Hole centreline stops at the green's front edge, 15 yds short of the green's centre.
        val centrelineEnd = TestFixtures.offset(northYards = 150.0)
        val greenCentre = TestFixtures.offset(northYards = 165.0)
        // An unnumbered (no `ref`) green polygon around the centre — the common OSM case.
        val corners =
            listOf(
                TestFixtures.offset(northYards = 160.0, eastYards = -5.0),
                TestFixtures.offset(northYards = 160.0, eastYards = 5.0),
                TestFixtures.offset(northYards = 170.0, eastYards = 5.0),
                TestFixtures.offset(northYards = 170.0, eastYards = -5.0),
            )
        val greenPolygon =
            OverpassElement(
                type = "way",
                id = 999,
                tags = mapOf("golf" to "green"),
                geometry = corners.map { OverpassNode(it.latitude, it.longitude) },
            )
        val holeWay =
            TestFixtures.overpassHoleElement(
                1,
                tee.latitude,
                tee.longitude,
                centrelineEnd.latitude,
                centrelineEnd.longitude,
            )
        val elements = listOf(holeWay, greenPolygon)

        val holes = OSMHoleParser.holeTargets(elements, listOf(ScorecardHole(1, 4, null)), center)

        assertEquals(1, holes.size)
        // The green should snap to the polygon centre (~165 yds), not stop at the centreline end (150 yds).
        assertTrue(GeoMath.yards(center, holes[0].green) > GeoMath.yards(center, centrelineEnd))
        assertTrue(GeoMath.yards(holes[0].green, greenCentre) <= 3)
    }

    @Test
    fun reversesWayDirectionWhenEndpointsAreFlipped() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.sampleGreen
        val elements =
            listOf(
                TestFixtures.overpassHoleElement(1, green.latitude, green.longitude, tee.latitude, tee.longitude),
            )

        val holes =
            OSMHoleParser.holeTargets(elements, listOf(ScorecardHole(1, 4, null)), center)

        assertEquals(1, holes.size)
        assertTrue(GeoMath.yards(center, holes[0].tee!!) < GeoMath.yards(center, holes[0].green))
    }

    @Test
    fun prefersPinCoordinateForGreen() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.sampleGreen
        val pin = TestFixtures.offset(northYards = 155.0, eastYards = 5.0)
        val elements =
            listOf(
                TestFixtures.overpassHoleElement(1, tee.latitude, tee.longitude, green.latitude, green.longitude),
                TestFixtures.overpassPinElement(1, pin.latitude, pin.longitude),
            )

        val holes =
            OSMHoleParser.holeTargets(elements, listOf(ScorecardHole(1, 4, null)), center)

        assertEquals(1, holes.size)
        assertEquals(pin.latitude, holes[0].green.latitude, 0.0)
        assertEquals(pin.longitude, holes[0].green.longitude, 0.0)
    }

    @Test
    fun keepsDistantHolesWhenCourseAreaIsTrusted() {
        val tee = TestFixtures.offset(northYards = 3_360.0)
        val farGreen = TestFixtures.offset(northYards = 3_520.0)
        val elements =
            listOf(
                TestFixtures.overpassHoleElement(1, tee.latitude, tee.longitude, farGreen.latitude, farGreen.longitude),
            )

        val filtered = OSMHoleParser.holeTargets(elements, emptyList(), center)
        val trusted = OSMHoleParser.holeTargets(elements, emptyList(), center, trustCourseArea = true)

        assertTrue(filtered.isEmpty())
        assertEquals(1, trusted.size)
    }

    @Test
    fun parsesFairwayComponentsForCountryClubStyleCourses() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.sampleGreen
        val elements =
            listOf(
                TestFixtures.overpassFairwayElement(1, tee.latitude, tee.longitude, green.latitude, green.longitude),
            )

        val holes =
            OSMHoleParser.holeTargets(
                elements,
                listOf(ScorecardHole(1, 4, null)),
                center,
                trustCourseArea = true,
            )

        assertEquals(1, holes.size)
        assertEquals(1, holes[0].number)
        assertTrue(holes[0].hasReliableGreenPosition)
        assertTrue(GeoMath.yards(holes[0].tee!!, holes[0].green) in 100..200)
    }

    @Test
    fun parsesTeeAndGreenWithoutFairway() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.sampleGreen
        val elements =
            listOf(
                TestFixtures.overpassTeeElement(3, tee.latitude, tee.longitude),
                TestFixtures.overpassGreenElement(3, green.latitude, green.longitude),
            )

        val holes =
            OSMHoleParser.holeTargets(
                elements,
                listOf(ScorecardHole(3, 4, null)),
                center,
                trustCourseArea = true,
            )

        assertEquals(1, holes.size)
        assertEquals(3, holes[0].number)
        assertTrue(holes[0].hasReliableGreenPosition)
    }

    @Test
    fun mergesFairwayComponentsAlongsideHoleWays() {
        val tee1 = TestFixtures.sampleTee
        val green1 = TestFixtures.sampleGreen
        val tee2 = TestFixtures.offset(northYards = 300.0)
        val green2 = TestFixtures.offset(northYards = 450.0)
        val elements =
            listOf(
                TestFixtures.overpassHoleElement(1, tee1.latitude, tee1.longitude, green1.latitude, green1.longitude),
                TestFixtures.overpassFairwayElement(
                    2,
                    tee2.latitude,
                    tee2.longitude,
                    green2.latitude,
                    green2.longitude,
                ),
            )

        val holes =
            OSMHoleParser.holeTargets(
                elements,
                listOf(ScorecardHole(1, 4, null), ScorecardHole(2, 4, null)),
                center,
                trustCourseArea = true,
            )

        assertEquals(listOf(1, 2), holes.map { it.number })
    }

    @Test
    fun parsesHoleTagOnGreen() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.sampleGreen
        val elements =
            listOf(
                OverpassElement(
                    id = 30_001,
                    tags = mapOf("golf" to "tee", "hole" to "4"),
                    lat = tee.latitude,
                    lon = tee.longitude,
                ),
                OverpassElement(
                    id = 30_002,
                    tags = mapOf("golf" to "green", "hole" to "4"),
                    lat = green.latitude,
                    lon = green.longitude,
                ),
            )

        val holes =
            OSMHoleParser.holeTargets(
                elements,
                listOf(ScorecardHole(4, 4, null)),
                center,
                trustCourseArea = true,
            )

        assertEquals(1, holes.size)
        assertEquals(4, holes[0].number)
        assertTrue(holes[0].hasReliableGreenPosition)
    }

    @Test
    fun preservesOrientationWhenHoleNumbersSkip() {
        val hole1 =
            TestFixtures.overpassFairwayElement(
                1,
                TestFixtures.sampleTee.latitude,
                TestFixtures.sampleTee.longitude,
                TestFixtures.sampleGreen.latitude,
                TestFixtures.sampleGreen.longitude,
            )
        val tee8 = TestFixtures.offset(northYards = 2_000.0)
        val green8 = TestFixtures.offset(northYards = 2_150.0)
        val hole8 =
            TestFixtures.overpassFairwayElement(8, tee8.latitude, tee8.longitude, green8.latitude, green8.longitude)

        val holes =
            OSMHoleParser.holeTargets(
                listOf(hole1, hole8),
                listOf(ScorecardHole(1, 4, null), ScorecardHole(8, 4, null)),
                center,
                trustCourseArea = true,
            )

        assertEquals(2, holes.size)
        val eight = holes.first { it.number == 8 }
        assertTrue(GeoMath.yards(eight.tee!!, eight.green) > 100)
    }

    @Test
    fun parsesHoleNumberFromNameLabels() {
        assertEquals(6, OSMHoleParser.holeNumber(mapOf("golf" to "fairway", "name" to "Hole 6")))
        assertEquals(12, OSMHoleParser.holeNumber(mapOf("golf" to "green", "name" to "#12")))
        assertEquals(4, OSMHoleParser.holeNumber(mapOf("golf" to "tee", "ref" to "04")))
    }

    @Test
    fun parsesFairwayTaggedByNameInsteadOfRef() {
        val tee = TestFixtures.sampleTee
        val green = TestFixtures.sampleGreen
        val elements =
            listOf(
                OverpassElement(
                    id = 40_001,
                    tags = mapOf("golf" to "fairway", "name" to "Hole 6"),
                    geometry =
                        listOf(
                            OverpassNode(tee.latitude, tee.longitude),
                            OverpassNode(green.latitude, green.longitude),
                        ),
                ),
            )

        val holes =
            OSMHoleParser.holeTargets(
                elements,
                listOf(ScorecardHole(6, 4, null)),
                center,
                trustCourseArea = true,
            )

        assertEquals(1, holes.size)
        assertEquals(6, holes[0].number)
        assertTrue(holes[0].hasReliableGreenPosition)
    }

    @Test
    fun infersGapHolesBetweenMappedFairways() {
        val hole5Tee = TestFixtures.sampleTee
        val hole5Green = TestFixtures.offset(northYards = 150.0)
        val hole8Tee = TestFixtures.offset(northYards = 900.0)
        val hole8Green = TestFixtures.offset(northYards = 1_050.0)
        val elements =
            listOf(
                TestFixtures.overpassFairwayElement(
                    5,
                    hole5Tee.latitude,
                    hole5Tee.longitude,
                    hole5Green.latitude,
                    hole5Green.longitude,
                ),
                TestFixtures.overpassFairwayElement(
                    8,
                    hole8Tee.latitude,
                    hole8Tee.longitude,
                    hole8Green.latitude,
                    hole8Green.longitude,
                ),
            )
        val scorecard = (1..8).map { ScorecardHole(it, 4, null) }

        val holes = OSMHoleParser.holeTargets(elements, scorecard, center, trustCourseArea = true)

        val six = holes.first { it.number == 6 }
        val seven = holes.first { it.number == 7 }
        assertEquals(HoleTargetSource.OPEN_STREET_MAP_INFERRED, six.source)
        assertEquals(HoleTargetSource.OPEN_STREET_MAP_INFERRED, seven.source)
        assertTrue(six.hasReliableGreenPosition)
        assertTrue(GeoMath.yards(hole5Green, six.green) > 100)
        assertTrue(GeoMath.yards(six.green, seven.green) > 80)
        assertTrue(GeoMath.yards(seven.green, hole8Tee) < GeoMath.yards(hole5Green, hole8Tee))
        assertNotNull(seven.tee)
        assertTrue(GeoMath.yards(seven.tee!!, seven.green) >= 80)
    }

    @Test
    fun filtersUnreasonablyShortHoles() {
        val tee = TestFixtures.sampleTee
        val shortGreen = TestFixtures.offset(northYards = 5.0)
        val elements =
            listOf(
                TestFixtures.overpassHoleElement(
                    1,
                    tee.latitude,
                    tee.longitude,
                    shortGreen.latitude,
                    shortGreen.longitude,
                ),
            )

        assertTrue(OSMHoleParser.holeTargets(elements, emptyList(), center).isEmpty())
    }
}
