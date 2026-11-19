package com.hbfr.figurinhas.purchases

import android.app.Activity
import android.content.Context
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.hbfr.figurinhas.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Pro: compra única (não consumível) no Google Play, com.hbfr.figurinhas.pro, R$ 19,90.
 * Sem servidor: o Play guarda a compra; o app consulta ao abrir e guarda o último estado para usar sem internet.
 */
class PurchaseManager(context: Context) : PurchasesUpdatedListener {
    companion object {
        const val PRODUCT_ID = "com.hbfr.figurinhas.pro"
        const val FREE_STICKER_LIMIT = 5
    }

    enum class Outcome { Purchased, Pending, Cancelled, Failed }

    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val owned = MutableStateFlow(prefs.getBoolean("owned", false))
    private val testUnlock = MutableStateFlow(BuildConfig.TEST_UNLOCK && prefs.getBoolean("testUnlock", false))
    private val _isPro = MutableStateFlow(owned.value || testUnlock.value)
    val isPro: StateFlow<Boolean> = _isPro
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price
    private val _storeError = MutableStateFlow(false)
    val storeError: StateFlow<Boolean> = _storeError
    private var details: ProductDetails? = null
    private var pendingResult: ((Outcome) -> Unit)? = null

    fun start() = scope.launch { refresh() }

    private fun update() { _isPro.value = owned.value || testUnlock.value }

    fun setTestUnlock(on: Boolean) {
        if (!BuildConfig.TEST_UNLOCK) return
        prefs.edit().putBoolean("testUnlock", on).apply()
        testUnlock.value = on; update()
    }
    val isTestUnlocked get() = testUnlock.value

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) {
                    if (cont.isActive) cont.resume(r.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    /** Consulta preço e compras. Devolve true se conseguiu falar com o Google Play. */
    suspend fun refresh(): Boolean {
        if (!connect()) { _storeError.value = true; return false }
        val pd = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
        )).build())
        details = pd.productDetailsList?.firstOrNull()
        _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        _storeError.value = details == null
        val res = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        if (res.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            handle(res.purchasesList, fromQuery = true)
        }
        return true
    }

    fun purchase(activity: Activity, onResult: (Outcome) -> Unit) {
        val d = details ?: run { onResult(Outcome.Failed); return }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        )).build()
        pendingResult = onResult
        val r = client.launchBillingFlow(activity, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingResult = null
            onResult(if (r.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) Outcome.Purchased.also { scope.launch { refresh() } } else Outcome.Failed)
        }
    }

    override fun onPurchasesUpdated(r: BillingResult, purchases: MutableList<Purchase>?) {
        val cb = pendingResult; pendingResult = null
        when (r.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                scope.launch { handle(purchases.orEmpty(), fromQuery = false) }
                val pending = purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }
                cb?.invoke(if (pending) Outcome.Pending else Outcome.Purchased)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> cb?.invoke(Outcome.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { scope.launch { refresh() }; cb?.invoke(Outcome.Purchased) }
            else -> cb?.invoke(Outcome.Failed)
        }
    }

    private suspend fun handle(list: List<Purchase>, fromQuery: Boolean) {
        val pro = list.filter { PRODUCT_ID in it.products }
        val bought = pro.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (bought != null && !bought.isAcknowledged) {
            // Sem confirmar em 3 dias, o Google devolve o dinheiro.
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(bought.purchaseToken).build())
        }
        val has = bought != null
        if (fromQuery || has) {
            owned.value = has
            prefs.edit().putBoolean("owned", has).apply()
            update()
        }
    }
}
