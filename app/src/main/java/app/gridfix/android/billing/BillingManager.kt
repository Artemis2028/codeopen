package app.gridfix.android.billing

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.billingStore by preferencesDataStore(name = "billing")

/**
 * GridFix Pro subscription state via Google Play Billing.
 *
 * Entitlement model: an active (purchased) subscription unlocks the app. The
 * last known result is cached in DataStore so a subscriber who opens the app
 * offline — days into a field problem — is never locked out; the cache is
 * corrected the next time Play answers. Free trials are configured on the
 * products in Play Console, not here: eligible users simply see an offer
 * whose first pricing phase is free.
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    enum class State { CHECKING, ENTITLED, LOCKED }

    data class Plan(
        val productId: String,
        val title: String,          // "Monthly" / "Annual"
        val price: String,          // formatted recurring price, e.g. "$1.99"
        val period: String,         // "month" / "year"
        val priceMicros: Long,      // recurring price in micros, for savings math
        val trialDays: Int,         // 0 when the user has no free-trial offer
        val offerToken: String,
        val details: ProductDetails,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val stateFlow = MutableStateFlow(State.CHECKING)
    val state: StateFlow<State> = stateFlow
    private val plansFlow = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = plansFlow
    private val noticeFlow = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = noticeFlow

    private var client: BillingClient? = null

    companion object {
        const val MONTHLY = "gridfix_pro_monthly"
        const val ANNUAL = "gridfix_pro_annual"
        const val MANAGE_URL =
            "https://play.google.com/store/account/subscriptions?package=app.gridfix.android"
        private val ENTITLED_KEY = booleanPreferencesKey("entitled")
    }

    fun start() {
        scope.launch {
            // Seed from cache first so a known subscriber is unlocked instantly,
            // network or not; Play's answer then confirms or corrects it.
            val cached = context.billingStore.data.first()[ENTITLED_KEY] ?: false
            if (cached && stateFlow.value == State.CHECKING) stateFlow.value = State.ENTITLED
            connect()
        }
    }

    fun close() {
        client?.endConnection()
        client = null
    }

    /** Paywall "Restore purchases" / retry: reconnect if needed, re-query everything. */
    fun restore() {
        noticeFlow.value = null
        val c = client
        if (c != null && c.isReady) {
            refreshPurchases()
            queryPlans()
        } else {
            connect()
        }
    }

    private fun connect() {
        val c = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshPurchases()
                    queryPlans()
                } else {
                    fallBackToCache()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Keep the current state; restore() reconnects on demand.
            }
        })
    }

    private fun refreshPurchases() {
        val c = client ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        c.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                applyPurchases(purchases)
            } else {
                fallBackToCache()
            }
        }
    }

    private fun applyPurchases(purchases: List<Purchase>) {
        val active = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // Google refunds unacknowledged purchases after 3 days — acknowledge promptly.
        active.filter { !it.isAcknowledged }.forEach { p ->
            val c = client ?: return@forEach
            val ack = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(p.purchaseToken)
                .build()
            c.acknowledgePurchase(ack) { }
        }
        val entitled = active.isNotEmpty()
        val pending = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        scope.launch {
            context.billingStore.edit { it[ENTITLED_KEY] = entitled }
            stateFlow.value = if (entitled) State.ENTITLED else State.LOCKED
            if (!entitled && pending) {
                noticeFlow.value = "Purchase pending — finish payment, then tap Restore purchases"
            }
        }
    }

    private fun fallBackToCache() {
        scope.launch {
            val cached = context.billingStore.data.first()[ENTITLED_KEY] ?: false
            stateFlow.value = if (cached) State.ENTITLED else State.LOCKED
            if (!cached) {
                noticeFlow.value = "Google Play billing is not reachable right now"
            }
        }
    }

    private fun queryPlans() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(MONTHLY, ANNUAL).map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()
        c.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val list = detailsResult.productDetailsList.mapNotNull { toPlan(it) }
            plansFlow.value = list.sortedBy { if (it.productId == MONTHLY) 0 else 1 }
        }
    }

    /**
     * Pick the offer to sell: prefer one containing a free phase (the trial —
     * Play only returns offers this user is eligible for), else the base plan.
     * The recurring price is the last non-free pricing phase.
     */
    private fun toPlan(pd: ProductDetails): Plan? {
        val offers = pd.subscriptionOfferDetails ?: return null
        if (offers.isEmpty()) return null
        val chosen = offers.firstOrNull { o ->
            o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        } ?: offers.first()
        val phases = chosen.pricingPhases.pricingPhaseList
        val paid = phases.lastOrNull { it.priceAmountMicros > 0L } ?: return null
        val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
        val period = when {
            paid.billingPeriod.endsWith("Y") -> "year"
            paid.billingPeriod.endsWith("W") -> "week"
            else -> "month"
        }
        return Plan(
            productId = pd.productId,
            title = if (period == "year") "Annual" else "Monthly",
            price = paid.formattedPrice,
            period = period,
            priceMicros = paid.priceAmountMicros,
            trialDays = trial?.let { periodDays(it.billingPeriod) } ?: 0,
            offerToken = chosen.offerToken,
            details = pd,
        )
    }

    private fun periodDays(iso: String): Int {
        val n = iso.filter { it.isDigit() }.toIntOrNull() ?: 0
        return when {
            iso.endsWith("W") -> n * 7
            iso.endsWith("M") -> n * 30
            iso.endsWith("Y") -> n * 365
            else -> n
        }
    }

    fun launchPurchase(activity: Activity, plan: Plan) {
        val c = client ?: return
        noticeFlow.value = null
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(plan.details)
                        .setOfferToken(plan.offerToken)
                        .build()
                )
            )
            .build()
        c.launchBillingFlow(activity, flow)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) refreshPurchases() else applyPurchases(purchases)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            else -> noticeFlow.value = "Purchase did not complete (Play error ${result.responseCode})"
        }
    }
}
