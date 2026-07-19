package com.vctrch.golfgps.feature.contribute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vctrch.golfgps.data.opengolf.OpenGolfAuthStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OpenGolfAccountViewModel
    @Inject
    constructor(
        private val authStore: OpenGolfAuthStore,
    ) : ViewModel() {
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
    }
