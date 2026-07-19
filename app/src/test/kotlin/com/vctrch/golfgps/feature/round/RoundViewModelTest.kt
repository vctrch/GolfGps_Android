package com.vctrch.golfgps.feature.round

import com.vctrch.golfgps.data.analytics.GolfAnalytics
import com.vctrch.golfgps.data.local.*
import com.vctrch.golfgps.data.repository.CourseRepository
import com.vctrch.golfgps.domain.HoleTargetSource
import com.vctrch.golfgps.domain.LatLng
import com.vctrch.golfgps.feature.auto.ActiveRoundSession
import com.vctrch.golfgps.location.LocationRepository
import com.vctrch.golfgps.location.LocationUiStatus
import com.vctrch.golfgps.testing.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var osm: FakeOsmGolfSource
    private lateinit var repository: CourseRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var analytics: GolfAnalytics
    private lateinit var activeRoundSession: ActiveRoundSession

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        api = FakeOpenGolfApi()
        osm = FakeOsmGolfSource()
        repository =
            CourseRepository(
                api,
                osm,
                CourseDataCache(InMemoryCachedCourseDao(), Json { ignoreUnknownKeys = true }),
            )
        locationRepository = mockk(relaxed = true)
        every { locationRepository.locationUpdates() } returns emptyFlow()
        every { locationRepository.status } returns MutableStateFlow(LocationUiStatus())
        analytics = mockk(relaxed = true)
        activeRoundSession = ActiveRoundSession()
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
            analytics = analytics,
            activeRoundSession = activeRoundSession,
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
    fun selectCourse_enrichesGreensFromOsmInBackground() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val loaded = TestFixtures.loadedCourse()
            api.loadResult = loaded.summary to loaded.scorecard
            val osmGreen = LatLng(loaded.summary.latitude + 0.001, loaded.summary.longitude)
            osm.holes =
                listOf(
                    TestFixtures.holeTarget(
                        number = 1,
                        source = HoleTargetSource.OPEN_STREET_MAP,
                        green = osmGreen,
                    ),
                )

            viewModel.selectCourse(loaded.summary)
            advanceUntilIdle()

            val hole1 = viewModel.uiState.value.loadedCourse?.holes?.first { it.number == 1 }
            assertEquals(osmGreen, hole1?.green)
            assertEquals(HoleTargetSource.OPEN_STREET_MAP, hole1?.source)
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
            assertTrue(viewModel.uiState.value.isRoundUnavailable)
        }

    @Test
    fun retryCourseLoad_reloadsLastSelectedCourse() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val loaded = TestFixtures.loadedCourse()
            api.loadShouldFail = true

            viewModel.selectCourse(loaded.summary)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isRoundUnavailable)

            api.loadShouldFail = false
            api.loadResult = loaded.summary to loaded.scorecard
            viewModel.retryCourseLoad()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isRoundReady)
            assertNull(viewModel.uiState.value.courseLoadError)
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
            assertEquals(loaded.summary, viewModel.uiState.value.lastSelectedCourse)
        }
}
