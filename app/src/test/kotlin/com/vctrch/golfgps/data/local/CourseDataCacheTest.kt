package com.vctrch.golfgps.data.local

import com.vctrch.golfgps.domain.*
import com.vctrch.golfgps.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CourseDataCacheTest {
    private lateinit var cache: CourseDataCache

    @Before
    fun setUp() {
        cache = CourseDataCache(InMemoryCachedCourseDao(), Json { ignoreUnknownKeys = true })
    }

    @Test
    fun saveBasics_roundTripsThroughCache() =
        runTest {
            val loaded = TestFixtures.loadedCourse()

            cache.saveBasics(loaded)
            val cached = cache.cachedBasics(loaded.summary.id)

            assertEquals(loaded.summary, cached?.summary)
            assertEquals(loaded.scorecard, cached?.scorecard)
            assertEquals(2, cached?.holes?.size)
        }

    @Test
    fun cachedBasics_returnsNullWhenMissing() =
        runTest {
            assertNull(cache.cachedBasics("unknown"))
        }

    @Test
    fun saveOsmHoles_mergesByHoleNumber() =
        runTest {
            val summary = TestFixtures.summary()
            val loaded = TestFixtures.loadedCourse(summary.id)
            cache.saveBasics(loaded)

            val firstGreen = LatLng(32.8400, -117.2800)
            val updatedGreen = LatLng(32.8410, -117.2810)
            cache.saveOsmHoles(
                courseId = summary.id,
                summary = summary,
                holes =
                    listOf(
                        TestFixtures.holeTarget(
                            number = 1,
                            source = HoleTargetSource.OPEN_STREET_MAP,
                            green = firstGreen,
                        ),
                    ),
            )
            cache.saveOsmHoles(
                courseId = summary.id,
                summary = summary,
                holes =
                    listOf(
                        TestFixtures.holeTarget(
                            number = 1,
                            source = HoleTargetSource.OPEN_STREET_MAP,
                            green = updatedGreen,
                        ),
                        TestFixtures.holeTarget(
                            number = 2,
                            source = HoleTargetSource.OPEN_STREET_MAP_INFERRED,
                            green = LatLng(32.8420, -117.2820),
                        ),
                    ),
            )

            val holes = cache.cachedOsmHoles(summary.id)

            assertEquals(2, holes?.size)
            assertEquals(updatedGreen, holes?.first { it.number == 1 }?.green)
            assertEquals(HoleTargetSource.OPEN_STREET_MAP_INFERRED, holes?.first { it.number == 2 }?.source)
        }

    @Test
    fun saveOsmHoles_ignoresScorecardFallbackSources() =
        runTest {
            val summary = TestFixtures.summary()
            cache.saveOsmHoles(
                courseId = summary.id,
                summary = summary,
                holes =
                    listOf(
                        TestFixtures.holeTarget(source = HoleTargetSource.SCORECARD_FALLBACK),
                    ),
            )

            assertNull(cache.cachedOsmHoles(summary.id))
        }
}
