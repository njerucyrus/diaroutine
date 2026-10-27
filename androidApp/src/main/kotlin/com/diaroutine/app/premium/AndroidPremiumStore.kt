package com.diaroutine.app.premium

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.diaroutine.app.BuildConfig
import com.diaroutine.premium.PremiumAccess
import com.diaroutine.premium.PremiumPlan
import com.diaroutine.premium.PremiumState
import com.diaroutine.premium.PremiumStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Premium through a Google Play subscription. The 3-month free trial is an offer on
 * the subscription's base plans, set up in Play Console; Play lists it only to people
 * eligible for it and handles the trial, renewal and cancellation itself.
 *
 * The last known answer is kept on the device so Premium works offline and in the
 * background (reminders, the weekly backup) without waiting for Play.
 */
class AndroidPremiumStore(private val context: Context) : PremiumStore, PurchasesUpdatedListener {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var product: ProductDetails? = null
    private var purchased = prefs.getBoolean(KEY_ACTIVE, false)

    private val mutableState = MutableStateFlow(
        PremiumState(
            access = when {
                simulated() || purchased -> PremiumAccess.Premium
                prefs.contains(KEY_ACTIVE) -> PremiumAccess.Free
                else -> PremiumAccess.Unknown
            },
            canSimulate = BuildConfig.DEBUG,
        ),
    )
    override val state: StateFlow<PremiumState> = mutableState.asStateFlow()

    /** Set by the visible activity, which Play's purchase screen is shown over. */
    var activity: Activity? = null

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun refresh() {
        if (client.isReady) {
            queryPurchases()
            queryPlans()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    queryPurchases()
                    queryPlans()
                } else {
                    mutableState.update {
                        it.copy(plansAvailable = false, access = if (it.access == PremiumAccess.Unknown) PremiumAccess.Free else it.access)
                    }
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    override fun purchase(plan: PremiumPlan) {
        val details = product ?: return refresh()
        val host = activity ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(plan.offerToken)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(host, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> handle(purchases.orEmpty())
            BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> Unit // Cancelled or failed: nothing changes.
        }
    }

    override fun manageSubscription() {
        val url = "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=${context.packageName}"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun simulate(premium: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit { putBoolean(KEY_SIMULATED, premium) }
        publish()
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) handle(purchases)
        }
    }

    private fun queryPlans() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            product = details.productDetailsList.firstOrNull()
            val plans = product?.let(::plansOf).orEmpty()
            mutableState.update { it.copy(plans = plans, plansAvailable = result.responseCode == BillingResponseCode.OK && plans.isNotEmpty()) }
        }
    }

    /** Unlocks for a paid or trialling subscription, and acknowledges new ones (Play refunds unacknowledged ones). */
    private fun handle(purchases: List<Purchase>) {
        val premium = purchases.filter { PRODUCT_ID in it.products }
        val active = premium.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        active.filterNot { it.isAcknowledged }.forEach { purchase ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { }
        }
        purchased = active.isNotEmpty()
        prefs.edit { putBoolean(KEY_ACTIVE, purchased) }
        mutableState.update { it.copy(pending = premium.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING }) }
        publish()
    }

    private fun publish() {
        mutableState.update { it.copy(access = if (purchased || simulated()) PremiumAccess.Premium else PremiumAccess.Free) }
    }

    private fun simulated() = BuildConfig.DEBUG && prefs.getBoolean(KEY_SIMULATED, false)

    /** One plan per base plan, using the free-trial offer when Play lists one for this person. */
    private fun plansOf(details: ProductDetails): List<PremiumPlan> =
        details.subscriptionOfferDetails.orEmpty().groupBy { it.basePlanId }.mapNotNull { (basePlanId, offers) ->
            val offer = offers.firstOrNull { it.freeTrialPhase() != null } ?: offers.firstOrNull { it.offerId == null } ?: offers.first()
            val recurring = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
            val period = when (recurring.billingPeriod.last()) {
                'Y' -> "year"
                'M' -> "month"
                'W' -> "week"
                else -> "period"
            }
            PremiumPlan(
                id = basePlanId,
                title = when (period) {
                    "year" -> "Yearly"
                    "month" -> "Monthly"
                    else -> basePlanId.replaceFirstChar { it.uppercase() }
                },
                price = recurring.formattedPrice,
                period = period,
                freeTrial = offer.freeTrialPhase()?.billingPeriod?.let(::describe),
                offerToken = offer.offerToken,
            )
        }

    private fun ProductDetails.SubscriptionOfferDetails.freeTrialPhase() =
        pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros == 0L }

    companion object {
        /** The subscription in Play Console; its base plans are "monthly" and "yearly". */
        const val PRODUCT_ID = "diaroutine_premium"
        private const val PREFS = "premium"
        private const val KEY_ACTIVE = "active"
        private const val KEY_SIMULATED = "simulated"

        /** The last known answer, for work done in the background. */
        fun isPremium(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ACTIVE, false) || (BuildConfig.DEBUG && prefs.getBoolean(KEY_SIMULATED, false))
        }

        /** An ISO 8601 period as words: "P3M" → "3 months". */
        internal fun describe(iso: String): String {
            val match = Regex("""P(\d+)([DWMY])""").matchEntire(iso) ?: return iso
            val n = match.groupValues[1].toInt()
            val unit = when (match.groupValues[2]) {
                "D" -> "day"
                "W" -> "week"
                "M" -> "month"
                else -> "year"
            }
            return if (n == 1) "1 $unit" else "$n ${unit}s"
        }
    }
}
