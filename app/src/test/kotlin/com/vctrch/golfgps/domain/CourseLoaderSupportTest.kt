package com.vctrch.golfgps.domain

import com.vctrch.golfgps.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseLoaderSupportTest {
    @Test
    fun fallbackHoles_usesCourseCenterForEveryGreen() {
        val summary = TestFixtures.summary(latitude = 33.1, longitude = -117.2)
        val scorecard = TestFixtures.scorecard()

        val holes = CourseLoaderSupport.fallbackHoles(scorecard, summary)

        assertEquals(2, holes.size)
        holes.forEach { hole ->
            assertEquals(LatLng(33.1, -117.2), hole.green)
            assertNull(hole.tee)
            assertEquals(HoleTargetSource.SCORECARD_FALLBACK, hole.source)
        }
    }

    @Test
    fun fallbackHoles_preservesScorecardMetadata() {
        val summary = TestFixtures.summary()
        val scorecard = TestFixtures.scorecard()

        val holes = CourseLoaderSupport.fallbackHoles(scorecard, summary)

        assertEquals(1, holes[0].number)
        assertEquals(4, holes[0].par)
        assertEquals(2, holes[1].number)
        assertEquals(5, holes[1].par)
    }
}
