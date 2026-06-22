package com.vctrch.golfgps.feature.round

import com.vctrch.golfgps.data.local.*
import com.vctrch.golfgps.data.repository.CourseRepository
import com.vctrch.golfgps.location.LocationRepository
import com.vctrch.golfgps.testing.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoundViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var api: FakeOpenGolfApi
    private lateinit var repository: CourseRepository
    private lateinit var locationRepository: LocationRepository

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        api = FakeOpenGolfApi()
        repository =
            CourseRepository(
                api,
                CourseDataCache(InMemoryCachedCourseDao(), Json { ignoreUnknownKeys = true }),
            )
        locationRepository = mockk(relaxed = true)
        every { locationRepository.locationUpdates() } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): RoundViewModel {
        return RoundViewModel(
            courseRepository = repository,
            userPreferencesRepository = createTestPreferencesRepository(),
            locationRepository = locationRepository,
        )
    }

    @Test
    fun onSearchQueryChange_shortQuery_clearsResults() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            api.searchResults = listOf(TestFixtures.summary())

            viewModel.onSearchQueryChange("pe")
            advanceTimeBy(350)
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.searchResults.size)

            viewModel.onSearchQueryChange("p")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.searchResults.isEmpty())
            assertFalse(viewModel.uiState.value.isSearching)
        }

    @Test
    fun onSearchQueryChange_debouncesApiCall() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            api.searchResults = listOf(TestFixtures.summary())

            viewModel.onSearchQueryChange("pe")
            advanceTimeBy(200)
            assertTrue(viewModel.uiState.value.searchResults.isEmpty())

            advanceTimeBy(200)
            advanceUntilIdle()

            assertEquals("pe", api.lastSearchQuery)
            assertEquals(1, viewModel.uiState.value.searchResults.size)
        }

    @Test
    fun selectCourse_success_loadsRound() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val loaded = TestFixtures.loadedCourse()
            api.loadResult = loaded.summary to loaded.scorecard

            viewModel.selectCourse(loaded.summary)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isRoundReady)
            assertEquals(loaded.summary, viewModel.uiState.value.loadedCourse?.summary)
            assertFalse(viewModel.uiState.value.isLoadingCourse)
        }

    @Test
    fun selectCourse_failure_setsErrorMessage() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            api.loadShouldFail = true

            viewModel.selectCourse(TestFixtures.summary())
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.loadedCourse)
            assertEquals(
                "Couldn't load course. Check your connection and try again.",
                viewModel.uiState.value.courseLoadError,
            )
        }

    @Test
    fun holeNavigation_movesBetweenLoadedHoles() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val loaded = TestFixtures.loadedCourse()
            api.loadResult = loaded.summary to loaded.scorecard
            viewModel.selectCourse(loaded.summary)
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.selectedHoleNumber)

            viewModel.nextHole()
            assertEquals(2, viewModel.uiState.value.selectedHoleNumber)

            viewModel.previousHole()
            assertEquals(1, viewModel.uiState.value.selectedHoleNumber)
        }

    @Test
    fun endRound_clearsLoadedCourse() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val loaded = TestFixtures.loadedCourse()
            api.loadResult = loaded.summary to loaded.scorecard
            viewModel.selectCourse(loaded.summary)
            advanceUntilIdle()

            viewModel.endRound()

            assertNull(viewModel.uiState.value.loadedCourse)
            assertEquals(1, viewModel.uiState.value.selectedHoleNumber)
        }
}
