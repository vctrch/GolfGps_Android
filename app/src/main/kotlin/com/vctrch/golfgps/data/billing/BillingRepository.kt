package com.vctrch.golfgps.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.vctrch.golfgps.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single, repurchasable tip the user can give to support the app.
 *
 * [productDetails] is null for the debug-only fallback tips that are surfaced when Play Billing is
 * unavailable (e.g. on an emulator without the Play Store). Those tips render in the UI but cannot
 * launch a real purchase flow.
 */
data class TipProduct(
    val productId: String,
    val name: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val productDetails: ProductDetails?,
)

/** Outcome of a tip attempt, surfaced to the UI for feedback. */
sealed interface TipResult {
    data object Thanks : TipResult

    data object Canceled : TipResult

    data class Error(val message: String) : TipResult
}

/**
 * Wraps the Google Play Billing client for one-time "tip" products.
 *
 * Tips are modeled as consumable in-app products: each purchase is consumed immediately so the
 * user can tip again. The product IDs below must match the in-app products configured in the
 * Google Play Console (Monetize → Products → In-app products).
 */
@Singleton
class BillingRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : PurchasesUpdatedListener {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val _tipProducts = MutableStateFlow<List<TipProduct>>(emptyList())
        val tipProducts: StateFlow<List<TipProduct>> = _tipProducts.asStateFlow()

        private val _tipResults = MutableSharedFlow<TipResult>(extraBufferCapacity = 1)
        val tipResults: SharedFlow<TipResult> = _tipResults.asSharedFlow()

        private val billingClient: BillingClient =
            BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
                )
                .build()

        /** Connects to Play Billing (idempotent) and loads tip product details. */
        fun start() {
            val state = billingClient.connectionState
            if (state == BillingClient.ConnectionState.CONNECTED ||
                state == BillingClient.ConnectionState.CONNECTING
            ) {
                if (state == BillingClient.ConnectionState.CONNECTED) {
                    scope.launch { queryTipProducts() }
                }
                return
            }
            billingClient.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            scope.launch { queryTipProducts() }
                        } else {
                            // Play Billing isn't available here (commonly an emulator without the
                            // Play Store). Show placeholder tips in debug builds so the card renders.
                            loadDebugFallbackProducts()
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        // Connection will be retried the next time start() is called.
                        loadDebugFallbackProducts()
                    }
                },
            )
        }

        private suspend fun queryTipProducts() {
            val productList =
                TIP_PRODUCT_IDS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
            val result = billingClient.queryProductDetails(params)
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                loadDebugFallbackProducts()
                return
            }
            val products =
                result.productDetailsList
                    .orEmpty()
                    .mapNotNull { details ->
                        val offer = details.oneTimePurchaseOfferDetails ?: return@mapNotNull null
                        TipProduct(
                            productId = details.productId,
                            name = details.name,
                            formattedPrice = offer.formattedPrice,
                            priceAmountMicros = offer.priceAmountMicros,
                            productDetails = details,
                        )
                    }
                    .sortedBy { it.priceAmountMicros }
            if (products.isEmpty()) {
                // No configured products reached us (e.g. testing on an emulator). Fall back to
                // placeholder tips in debug builds rather than leaving the card permanently empty.
                loadDebugFallbackProducts()
            } else {
                _tipProducts.value = products
            }
        }

        /**
         * Populates [tipProducts] with placeholder tips in debug builds when Play Billing can't
         * provide real ones. Released builds keep an empty list so nothing fake is ever shown.
         */
        private fun loadDebugFallbackProducts() {
            if (!BuildConfig.DEBUG) return
            if (_tipProducts.value.isNotEmpty()) return
            _tipProducts.value = DEBUG_FALLBACK_PRODUCTS
        }

        /** Launches the Play purchase dialog for the given tip. Must be called from an Activity. */
        fun launchTip(
            activity: Activity,
            product: TipProduct,
        ) {
            val productDetails = product.productDetails
            if (productDetails == null) {
                // Debug placeholder tip: there's no real Play product to purchase.
                _tipResults.tryEmit(TipResult.Error("Tips are unavailable in this build."))
                return
            }
            val productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            val flowParams =
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .build()
            billingClient.launchBillingFlow(activity, flowParams)
        }

        override fun onPurchasesUpdated(
            billingResult: BillingResult,
            purchases: MutableList<Purchase>?,
        ) {
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK ->
                    purchases?.forEach { purchase -> scope.launch { handlePurchase(purchase) } }
                BillingClient.BillingResponseCode.USER_CANCELED ->
                    _tipResults.tryEmit(TipResult.Canceled)
                else ->
                    _tipResults.tryEmit(TipResult.Error("Tip couldn't be completed. Please try again."))
            }
        }

        private suspend fun handlePurchase(purchase: Purchase) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
                return
            }
            // Consume the tip so the user is free to give another one later.
            val consumeParams =
                ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            val result = billingClient.consumePurchase(consumeParams)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _tipResults.tryEmit(TipResult.Thanks)
            } else {
                _tipResults.tryEmit(TipResult.Error("Couldn't finalize the tip."))
            }
        }

        companion object {
            /** Must match the in-app product IDs created in the Google Play Console. */
            val TIP_PRODUCT_IDS = listOf("tip_small", "tip_medium", "tip_large")

            /** Placeholder tips shown only in debug builds when Play Billing is unavailable. */
            private val DEBUG_FALLBACK_PRODUCTS =
                listOf(
                    TipProduct("tip_small", "Small tip", "$1.99", 1_990_000, null),
                    TipProduct("tip_medium", "Medium tip", "$4.99", 4_990_000, null),
                    TipProduct("tip_large", "Large tip", "$9.99", 9_990_000, null),
                )
        }
    }
