package com.vctrch.golfgps.feature.support

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vctrch.golfgps.data.billing.BillingRepository
import com.vctrch.golfgps.data.billing.TipProduct
import com.vctrch.golfgps.data.billing.TipResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SupportViewModel
    @Inject
    constructor(
        private val billingRepository: BillingRepository,
    ) : ViewModel() {
        val tipProducts: StateFlow<List<TipProduct>> =
            billingRepository.tipProducts.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )

        val tipResults: SharedFlow<TipResult> = billingRepository.tipResults

        init {
            billingRepository.start()
        }

        fun onTipClicked(
            activity: Activity,
            product: TipProduct,
        ) {
            billingRepository.launchTip(activity, product)
        }
    }
