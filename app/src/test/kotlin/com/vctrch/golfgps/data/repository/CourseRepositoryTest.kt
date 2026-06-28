package com.vctrch.golfgps.data.repository

import com.vctrch.golfgps.data.local.*
import com.vctrch.golfgps.domain.HoleTargetSource
import com.vctrch.golfgps.domain.LatLng
import com.vctrch.golfgps.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CourseRepositoryTest {
    private lateinit var api: FakeOpenGolfApi
    private lateinit var osm: FakeOsmGolfSource
    private lateinit var cache: CourseDataCache
    private lateinit var repository: CourseRepository

    @Before
    fun setUp() {
        api = FakeOpenGolfApi()
        osm = FakeOsmGolfSource()
        cache = CourseDataCache(InMemoryCachedCourseDao(), Json { ignoreUnknownKeys = true })
        repository = CourseRepository(api, osm, cache)
    }

    @Test
    fun searchCourses_delegatesToApi() =
        runTest {
            val expected = listOf(TestFixtures.summary())
            api.searchResults = expected

            val results = repository.searchCourses("torrey")

            assertEquals(expected, results)
            assertEquals("torrey", api.lastSearchQuery)
        }

    @Test
    fun loadCourseBasics_savesToCache() =
        runTest {
            val loaded = TestFixtures.loadedCourse()
            api.loadResult = loaded.summary to loaded.scorecard

            val result = repository.loadCourseBasics(loaded.summary)

            assertEquals(loaded.summary, result.summary)
            assertEquals(loaded.scorecard, result.scorecard)
            assertEquals(2, result.holes.size)
            val cached = cache.cachedBasics(loaded.summary.id)
            assertEquals(result.summary, cached?.summary)
        }

    @Test
    fun loadCourseBasics_fallsBackToCacheWhenApiFails() =
        runTest {
            val loaded = TestFixtures.loadedCourse()
            api.loadResult = loaded.summary to loaded.scorecard
            repository.loadCourseBasics(loaded.summary)

            api.loadShouldFail = true
            val fallback = repository.loadCourseBasics(loaded.summary)

            assertEquals(loaded.summary.id, fallback.summary.id)
            assertEquals(2, fallback.holes.size)
        }

    @Test(expected = IllegalStateException::class)
    fun loadCourseBasics_throwsWhenApiAndCacheMiss() =
        runTest {
            api.loadShouldFail = true
            repository.loadCourseBasics(TestFixtures.summary(id = "missing-course"))
        }

    @Test
    fun loadCourseBasics_mergesAlreadyCachedOsmGreens() =
        runTest {
            val loaded = TestFixtures.loadedCourse()
            api.loadResult = loaded.summary to loaded.scorecard
            val osmGreen = LatLng(40.0, -80.0)
            cache.saveOsmHoles(
                loaded.summary.id,
                loaded.summary,
                listOf(
                    TestFixtures.holeTarget(
                        number = 1,
                        source = HoleTargetSource.OPEN_STREET_MAP,
                        green = osmGreen,
                    ),
                ),
            )

            val result = repository.loadCourseBasics(loaded.summary)

            val hole1 = result.holes.first { it.number == 1 }
            assertEquals(osmGreen, hole1.green)
            assertEquals(HoleTargetSource.OPEN_STREET_MAP, hole1.source)
        }

    @Test
    fun enrichWithOsmGreens_fetchesMergesAndCaches() =
        runTest {
            val loaded = TestFixtures.loadedCourse()
            val osmGreen = LatLng(41.0, -81.0)
            osm.holes =
                listOf(
                    TestFixtures.holeTarget(
                        number = 1,
                        source = HoleTargetSource.OPEN_STREET_MAP,
                        green = osmGreen,
                    ),
                )

            val enriched = repository.enrichWithOsmGreens(loaded)

            val hole1 = enriched!!.holes.first { it.number == 1 }
            assertEquals(osmGreen, hole1.green)
            assertEquals(HoleTargetSource.OPEN_STREET_MAP, hole1.source)
            val hole2 = enriched.holes.first { it.number == 2 }
            assertEquals(HoleTargetSource.SCORECARD_FALLBACK, hole2.source)
            assertEquals(osmGreen, cache.cachedOsmHoles(loaded.summary.id)?.first { it.number == 1 }?.green)
        }

    @Test
    fun enrichWithOsmGreens_returnsNullWhenNoOsmData() =
        runTest {
            assertNull(repository.enrichWithOsmGreens(TestFixtures.loadedCourse()))
        }
}
