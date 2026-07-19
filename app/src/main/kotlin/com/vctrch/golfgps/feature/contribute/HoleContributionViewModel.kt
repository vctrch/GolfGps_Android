package com.vctrch.golfgps.feature.contribute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vctrch.golfgps.data.opengolf.OpenGolfAuthStore
import com.vctrch.golfgps.data.opengolf.OpenGolfConfig
import com.vctrch.golfgps.data.opengolf.OpenGolfContributeClient
import com.vctrch.golfgps.data.opengolf.OpenGolfMomentSubmission
import com.vctrch.golfgps.data.opengolf.OpenGolfMomentType
import com.vctrch.golfgps.data.opengolf.OpenGolfTermsChallenge
import com.vctrch.golfgps.data.opengolf.OpenGolfTermsStore
import com.vctrch.golfgps.domain.GolfDataError
import com.vctrch.golfgps.domain.GolfDataException
import com.vctrch.golfgps.domain.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class HoleContributionViewModel
    @Inject
    constructor(
        private val authStore: OpenGolfAuthStore,
        private val contributeClient: OpenGolfContributeClient,
        private val termsStore: OpenGolfTermsStore,
        private val openGolfConfig: OpenGolfConfig,
    ) : ViewModel() {
        sealed class PresentedSheet {
            data object Contribute : PresentedSheet()

            data object SignIn : PresentedSheet()

            data class Terms(val challenge: OpenGolfTermsChallenge) : PresentedSheet()
        }

        data class UiState(
            val isPlaceMode: Boolean = false,
            val draftCoordinate: LatLng? = null,
            val selectedType: OpenGolfMomentType = OpenGolfMomentType.TEE,
            val note: String = "",
            val isSubmitting: Boolean = false,
            val statusMessage: String? = null,
            val errorMessage: String? = null,
            val presentedSheet: PresentedSheet? = null,
        ) {
            val coordinateSummary: String
                get() {
                    val c = draftCoordinate ?: return "No location selected"
                    return "%.5f, %.5f".format(c.latitude, c.longitude)
                }

            val canSubmit: Boolean get() = !isSubmitting && draftCoordinate != null
        }

        private val _uiState = MutableStateFlow(UiState())
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        val authState = authStore.state

        fun requestSignInCode(
            email: String,
            createAccount: Boolean,
        ) {
            viewModelScope.launch {
                authStore.requestSignInCode(
                    email,
                    if (createAccount) {
                        OpenGolfAuthStore.AuthIntent.CREATE_ACCOUNT
                    } else {
                        OpenGolfAuthStore.AuthIntent.SIGN_IN
                    },
                )
            }
        }

        fun verifyCode(code: String) {
            viewModelScope.launch { authStore.verifyCode(code) }
        }

        fun signOut() {
            authStore.signOut()
        }

        fun resetForHoleChange() {
            _uiState.value = UiState()
        }

        fun beginPlaceMode() {
            _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
            if (!authStore.state.value.isSignedIn) {
                _uiState.update { it.copy(presentedSheet = PresentedSheet.SignIn) }
                return
            }
            if (openGolfConfig.apiKey.isBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "OPENGOLF_API_KEY is not configured in local.properties.")
                }
                return
            }
            _uiState.update { it.copy(isPlaceMode = true) }
        }

        fun cancelPlaceMode() {
            _uiState.update { it.copy(isPlaceMode = false, draftCoordinate = null, errorMessage = null) }
        }

        fun markHere(at: LatLng) {
            _uiState.update {
                it.copy(
                    draftCoordinate = at,
                    isPlaceMode = false,
                    presentedSheet = PresentedSheet.Contribute,
                )
            }
        }

        fun useGps(coordinate: LatLng) {
            _uiState.update { it.copy(draftCoordinate = coordinate) }
        }

        fun setSelectedType(type: OpenGolfMomentType) {
            _uiState.update { it.copy(selectedType = type) }
        }

        fun setNote(note: String) {
            _uiState.update { it.copy(note = note) }
        }

        fun dismissSheet() {
            _uiState.update { it.copy(presentedSheet = null) }
        }

        fun handleAuthPhase(phase: OpenGolfAuthStore.Phase) {
            if (phase == OpenGolfAuthStore.Phase.SIGNED_IN &&
                _uiState.value.presentedSheet is PresentedSheet.SignIn
            ) {
                _uiState.update { it.copy(presentedSheet = null) }
            }
        }

        fun acceptTerms(version: String) {
            viewModelScope.launch {
                termsStore.accept(version)
                _uiState.update { it.copy(presentedSheet = null) }
            }
        }

        fun submitDraft(
            courseId: String,
            holeNumber: Int,
            userLocation: LatLng?,
            userAccuracyMeters: Double?,
            dismissOnSuccess: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
                val auth = authStore.state.value
                if (!auth.isSignedIn) {
                    _uiState.update { it.copy(presentedSheet = PresentedSheet.SignIn) }
                    return@launch
                }
                val apiKey = openGolfConfig.apiKey
                if (apiKey.isBlank()) {
                    _uiState.update {
                        it.copy(errorMessage = "OPENGOLF_API_KEY is not configured in local.properties.")
                    }
                    return@launch
                }
                val draft = _uiState.value.draftCoordinate ?: return@launch
                val usingUserGps =
                    userLocation != null &&
                        abs(userLocation.latitude - draft.latitude) < 0.00001 &&
                        abs(userLocation.longitude - draft.longitude) < 0.00001

                _uiState.update { it.copy(isSubmitting = true) }
                try {
                    val type = _uiState.value.selectedType
                    contributeClient.submitMoment(
                        submission =
                            OpenGolfMomentSubmission(
                                momentType = type,
                                latitude = draft.latitude,
                                longitude = draft.longitude,
                                accuracyMeters = if (usingUserGps) userAccuracyMeters else null,
                                courseId = courseId,
                                hole = holeNumber,
                                playerId = auth.playerId,
                                note = _uiState.value.note,
                                dedupKey =
                                    "$courseId-$holeNumber-${type.rawValue}-" +
                                        "${System.currentTimeMillis() / 1000}",
                            ),
                        appApiKey = apiKey,
                        accessToken = auth.accessToken,
                    )
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = contributeClient.successMessage(type, holeNumber),
                            note = "",
                            draftCoordinate = null,
                            presentedSheet = if (dismissOnSuccess) null else it.presentedSheet,
                        )
                    }
                } catch (e: GolfDataException) {
                    _uiState.update { it.copy(isSubmitting = false) }
                    if (e.error == GolfDataError.TERMS_ACCEPTANCE_REQUIRED &&
                        e.termsVersion != null &&
                        e.termsUrl != null
                    ) {
                        _uiState.update {
                            it.copy(
                                presentedSheet =
                                    PresentedSheet.Terms(
                                        OpenGolfTermsChallenge(e.termsVersion, e.termsUrl),
                                    ),
                            )
                        }
                    } else {
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = e.message ?: "Submit failed.")
                    }
                }
            }
        }
    }
