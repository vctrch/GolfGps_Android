package com.vctrch.golfgps.feature.contribute

import com.vctrch.golfgps.data.opengolf.OpenGolfAuthStore
import com.vctrch.golfgps.data.opengolf.OpenGolfConfig
import com.vctrch.golfgps.data.opengolf.OpenGolfContributeClient
import com.vctrch.golfgps.data.opengolf.OpenGolfIngestResult
import com.vctrch.golfgps.data.opengolf.OpenGolfMomentSubmission
import com.vctrch.golfgps.data.opengolf.OpenGolfMomentType
import com.vctrch.golfgps.data.opengolf.OpenGolfTermsStore
import com.vctrch.golfgps.domain.GolfDataException
import com.vctrch.golfgps.domain.LatLng
import com.vctrch.golfgps.testing.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HoleContributionViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var authStore: OpenGolfAuthStore
    private lateinit var contributeClient: OpenGolfContributeClient
    private lateinit var termsStore: OpenGolfTermsStore
    private lateinit var authState: MutableStateFlow<OpenGolfAuthStore.State>
    private val openGolfConfig = TestFixtures.openGolfConfig()

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        authState = MutableStateFlow(OpenGolfAuthStore.State())
        authStore = mockk(relaxed = true)
        every { authStore.state } returns authState
        contributeClient = mockk(relaxed = true)
        every { contributeClient.successMessage(any(), any()) } answers {
            val type = firstArg<OpenGolfMomentType>()
            val hole = secondArg<Int>()
            "${type.label} saved for hole $hole."
        }
        termsStore = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(config: OpenGolfConfig = openGolfConfig) =
        HoleContributionViewModel(authStore, contributeClient, termsStore, config)

    private fun signedIn() {
        authState.value =
            OpenGolfAuthStore.State(
                phase = OpenGolfAuthStore.Phase.SIGNED_IN,
                email = "golfer@example.com",
                playerId = "ogid_abcdef",
                accessToken = "token",
            )
    }

    @Test
    fun markHere_opensContributeSheetWithDraft() {
        val vm = createViewModel()
        val at = LatLng(36.5, -121.9)

        vm.markHere(at)

        assertEquals(at, vm.uiState.value.draftCoordinate)
        assertFalse(vm.uiState.value.isPlaceMode)
        assertTrue(vm.uiState.value.presentedSheet is HoleContributionViewModel.PresentedSheet.Contribute)
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun beginPlaceMode_whenSignedOut_promptsSignIn() {
        val vm = createViewModel()

        vm.beginPlaceMode()

        assertFalse(vm.uiState.value.isPlaceMode)
        assertTrue(vm.uiState.value.presentedSheet is HoleContributionViewModel.PresentedSheet.SignIn)
    }

    @Test
    fun beginPlaceMode_whenSignedIn_entersPlaceMode() {
        signedIn()
        val vm = createViewModel()

        vm.beginPlaceMode()

        assertTrue(vm.uiState.value.isPlaceMode)
        assertNull(vm.uiState.value.presentedSheet)
    }

    @Test
    fun beginPlaceMode_withoutApiKey_setsError() {
        signedIn()
        val vm = createViewModel(TestFixtures.openGolfConfig(apiKey = ""))

        vm.beginPlaceMode()

        assertFalse(vm.uiState.value.isPlaceMode)
        assertTrue(vm.uiState.value.errorMessage!!.contains("OPENGOLF_API_KEY"))
    }

    @Test
    fun resetForHoleChange_clearsDraftAndMessages() {
        val vm = createViewModel()
        vm.markHere(LatLng(1.0, 2.0))
        vm.setNote("tip")

        vm.resetForHoleChange()

        assertNull(vm.uiState.value.draftCoordinate)
        assertEquals("", vm.uiState.value.note)
        assertNull(vm.uiState.value.presentedSheet)
    }

    @Test
    fun handleAuthPhase_dismissesSignInWhenSignedIn() {
        val vm = createViewModel()
        vm.beginPlaceMode()
        assertTrue(vm.uiState.value.presentedSheet is HoleContributionViewModel.PresentedSheet.SignIn)

        vm.handleAuthPhase(OpenGolfAuthStore.Phase.SIGNED_IN)

        assertNull(vm.uiState.value.presentedSheet)
    }

    @Test
    fun submitDraft_callsSubmitMomentWithMappedSubmission() =
        runTest(dispatcher) {
            signedIn()
            val submission = slot<OpenGolfMomentSubmission>()
            coEvery {
                contributeClient.submitMoment(capture(submission), any(), any())
            } returns OpenGolfIngestResult(ok = true, ingested = 1)

            val vm = createViewModel()
            val draft = LatLng(36.568, -121.949)
            vm.markHere(draft)
            vm.setSelectedType(OpenGolfMomentType.GREEN)
            vm.setNote("center")

            vm.submitDraft(
                courseId = "course-1",
                holeNumber = 7,
                userLocation = draft,
                userAccuracyMeters = 3.5,
                dismissOnSuccess = true,
            )
            advanceUntilIdle()

            coVerify(exactly = 1) {
                contributeClient.submitMoment(any(), "test-opengolf-key", "token")
            }
            assertEquals(OpenGolfMomentType.GREEN, submission.captured.momentType)
            assertEquals("course-1", submission.captured.courseId)
            assertEquals(7, submission.captured.hole)
            assertEquals("ogid_abcdef", submission.captured.playerId)
            assertEquals("center", submission.captured.note)
            assertEquals(3.5, submission.captured.accuracyMeters)
            assertEquals(36.568, submission.captured.latitude, 0.0)
            assertNull(vm.uiState.value.draftCoordinate)
            assertEquals("Green saved for hole 7.", vm.uiState.value.statusMessage)
            assertNull(submission.captured.sessionId)
            assertNull(submission.captured.strokes)
            assertTrue(submission.captured.dedupKey.startsWith("course-1-7-green-nosession-"))
            assertNull(vm.uiState.value.presentedSheet)
        }

    @Test
    fun submitDraft_termsRequired_presentsTermsSheet() =
        runTest(dispatcher) {
            signedIn()
            coEvery {
                contributeClient.submitMoment(any(), any(), any())
            } throws GolfDataException.termsAcceptanceRequired("2024-01", "https://example.com/terms")

            val vm = createViewModel()
            vm.markHere(LatLng(36.0, -121.0))

            vm.submitDraft(
                courseId = "course-1",
                holeNumber = 1,
                userLocation = null,
                userAccuracyMeters = null,
                dismissOnSuccess = false,
            )
            advanceUntilIdle()

            val sheet = vm.uiState.value.presentedSheet
            assertTrue(sheet is HoleContributionViewModel.PresentedSheet.Terms)
            val terms = sheet as HoleContributionViewModel.PresentedSheet.Terms
            assertEquals("2024-01", terms.challenge.version)
            assertEquals("https://example.com/terms", terms.challenge.termsUrl)
            assertFalse(vm.uiState.value.isSubmitting)
        }

    @Test
    fun submitDraft_withoutApiKey_setsErrorAndSkipsNetwork() =
        runTest(dispatcher) {
            signedIn()
            val vm = createViewModel(TestFixtures.openGolfConfig(apiKey = ""))
            vm.markHere(LatLng(36.0, -121.0))

            vm.submitDraft(
                courseId = "course-1",
                holeNumber = 1,
                userLocation = null,
                userAccuracyMeters = null,
                dismissOnSuccess = true,
            )
            advanceUntilIdle()

            assertTrue(vm.uiState.value.errorMessage!!.contains("OPENGOLF_API_KEY"))
            coVerify(exactly = 0) { contributeClient.submitMoment(any(), any(), any()) }
        }
}
