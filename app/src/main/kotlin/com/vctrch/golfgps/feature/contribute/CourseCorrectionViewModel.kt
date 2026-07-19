package com.vctrch.golfgps.feature.contribute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vctrch.golfgps.BuildConfig
import com.vctrch.golfgps.data.opengolf.OpenGolfAuthStore
import com.vctrch.golfgps.data.opengolf.OpenGolfContributeClient
import com.vctrch.golfgps.data.opengolf.OpenGolfCorrectionField
import com.vctrch.golfgps.data.opengolf.OpenGolfCorrectionSubmission
import com.vctrch.golfgps.data.opengolf.OpenGolfTermsChallenge
import com.vctrch.golfgps.data.opengolf.OpenGolfTermsStore
import com.vctrch.golfgps.domain.GolfDataError
import com.vctrch.golfgps.domain.GolfDataException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CourseCorrectionViewModel
    @Inject
    constructor(
        private val authStore: OpenGolfAuthStore,
        private val contributeClient: OpenGolfContributeClient,
        private val termsStore: OpenGolfTermsStore,
    ) : ViewModel() {
        sealed class PresentedSheet {
            data object SignIn : PresentedSheet()

            data class Terms(val challenge: OpenGolfTermsChallenge) : PresentedSheet()
        }

        data class UiState(
            val note: String = "",
            val correctionField: OpenGolfCorrectionField = OpenGolfCorrectionField.PHONE,
            val correctionValue: String = "",
            val isSubmitting: Boolean = false,
            val statusMessage: String? = null,
            val errorMessage: String? = null,
            val presentedSheet: PresentedSheet? = null,
        ) {
            val canSubmit: Boolean
                get() = !isSubmitting && correctionValue.trim().isNotEmpty()
        }

        private val _uiState = MutableStateFlow(UiState())
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        val authState = authStore.state

        fun openGolfAuthStore(): OpenGolfAuthStore = authStore

        fun summarySubtitle(isSignedIn: Boolean): String {
            return if (isSignedIn) {
                "Signed in — mark tee/green/pin on the map above."
            } else {
                "Sign in with OpenGolf, then mark locations on the hole map."
            }
        }

        fun presentSignIn() {
            _uiState.update { it.copy(presentedSheet = PresentedSheet.SignIn) }
        }

        fun dismissSheet() {
            _uiState.update { it.copy(presentedSheet = null) }
        }

        fun setCorrectionField(field: OpenGolfCorrectionField) {
            _uiState.update { it.copy(correctionField = field) }
        }

        fun setCorrectionValue(value: String) {
            _uiState.update { it.copy(correctionValue = value) }
        }

        fun setNote(note: String) {
            _uiState.update { it.copy(note = note) }
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

        fun signOut() {
            authStore.signOut()
        }

        fun submitCorrection(courseId: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
                val auth = authStore.state.value
                if (!auth.isSignedIn) {
                    _uiState.update { it.copy(presentedSheet = PresentedSheet.SignIn) }
                    return@launch
                }
                val apiKey = BuildConfig.OPENGOLF_API_KEY
                if (apiKey.isBlank()) {
                    _uiState.update {
                        it.copy(errorMessage = "OPENGOLF_API_KEY is not configured in local.properties.")
                    }
                    return@launch
                }
                val value = _uiState.value.correctionValue.trim()
                if (value.isEmpty()) return@launch

                _uiState.update { it.copy(isSubmitting = true) }
                try {
                    val result =
                        contributeClient.submitCorrection(
                            OpenGolfCorrectionSubmission(
                                courseId = courseId,
                                field = _uiState.value.correctionField,
                                proposedValue = value,
                                note = _uiState.value.note.takeIf { it.isNotBlank() },
                            ),
                            appApiKey = apiKey,
                            accessToken = auth.accessToken,
                        )
                    val status = result.status ?: "submitted"
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage =
                                "Correction $status. OpenGolf reviews course facts before they apply.",
                            correctionValue = "",
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
