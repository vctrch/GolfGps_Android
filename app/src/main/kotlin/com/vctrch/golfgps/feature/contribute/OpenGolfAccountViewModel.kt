package com.vctrch.golfgps.feature.contribute

import androidx.lifecycle.ViewModel
import com.vctrch.golfgps.data.opengolf.OpenGolfAuthStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OpenGolfAccountViewModel
    @Inject
    constructor(
        private val authStore: OpenGolfAuthStore,
    ) : ViewModel() {
        val authState = authStore.state

        fun openGolfAuthStore(): OpenGolfAuthStore = authStore
    }
