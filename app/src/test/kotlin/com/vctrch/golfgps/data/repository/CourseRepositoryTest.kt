package com.vctrch.golfgps.data.repository

import com.vctrch.golfgps.data.local.*
import com.vctrch.golfgps.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CourseRepositoryTest {
    private lateinit var api: FakeOpenGolfApi
    private lateinit var cache: CourseDataCache
    private lateinit var repository: CourseRepository

    @Before
    fun setUp() {
        api = FakeOpenGolfApi()
        cache = CourseDataCache(InMemoryCachedCourseDao(), Json { ignoreUnknownKeys = true })
        repository = CourseRepository(api, cache)
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

            val result = repository.loadCourseBasics(loaded.summary.id)

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
            repository.loadCourseBasics(loaded.summary.id)

            api.loadShouldFail = true
            val fallback = repository.loadCourseBasics(loaded.summary.id)

            assertEquals(loaded.summary.id, fallback.summary.id)
            assertEquals(2, fallback.holes.size)
        }

    @Test(expected = IllegalStateException::class)
    fun loadCourseBasics_throwsWhenApiAndCacheMiss() =
        runTest {
            api.loadShouldFail = true
            repository.loadCourseBasics("missing-course")
        }
}
