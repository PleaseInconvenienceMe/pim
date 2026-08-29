package com.pleaseinconvenienceme.pim

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BillingHelperImpl : BillingHelper {

    private companion object {
        const val PRODUCT_ID = "pim_unlimited"
    }

    private val _isPurchased = MutableStateFlow(false)
    override val isPurchased: StateFlow<Boolean> = _isPurchased

    private val _price = MutableStateFlow<String?>(null)
    override val price: StateFlow<String?> = _price

    private lateinit var billingClient: BillingClient
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun initialize(activity: ComponentActivity) {
        appContext = activity.applicationContext
        // Restore persisted purchase state immediately
        val prefs = activity.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        _isPurchased.value = prefs.getBoolean(PrefsKeys.IS_PURCHASED, false)

        billingClient = BillingClient.newBuilder(activity)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(activity, purchase)
                    }
                }
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

        connect()
    }

    private fun connect(onConnected: (() -> Unit)? = null) {
        if (billingClient.isReady) {
            onConnected?.invoke()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryExistingPurchases(appContext)
                    queryPrice()
                    onConnected?.invoke()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnection will happen lazily via refreshPurchases or launchBillingFlow
            }
        })
    }

    override fun refreshPurchases() {
        if (!::billingClient.isInitialized) return
        connect { queryExistingPurchases(appContext) }
    }

    /**
     * Params for looking up the single one-time product. Shared by the price query and the
     * purchase flow so there is one place to touch when the Play Billing query API changes.
     */
    private fun productDetailsParams(): QueryProductDetailsParams {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        return QueryProductDetailsParams.newBuilder().setProductList(productList).build()
    }

    override fun launchBillingFlow(activity: Activity) {
        if (!::billingClient.isInitialized || !billingClient.isReady) return

        billingClient.queryProductDetailsAsync(productDetailsParams()) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val productDetails = detailsResult.productDetailsList.firstOrNull() ?: return@queryProductDetailsAsync

            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            activity.runOnUiThread {
                billingClient.launchBillingFlow(activity, flowParams)
            }
        }
    }

    private fun queryPrice() {
        billingClient.queryProductDetailsAsync(productDetailsParams()) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val priceStr = detailsResult.productDetailsList.firstOrNull()
                    ?.oneTimePurchaseOfferDetails?.formattedPrice
                if (priceStr != null) {
                    _price.value = priceStr
                    appContext.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
                        .edit().putString("cached_price", priceStr).apply()
                }
            }
        }
    }

    private fun queryExistingPurchases(context: Context) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val activeEntitlement = purchases.any {
                it.products.contains(PRODUCT_ID) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (activeEntitlement) {
                for (purchase in purchases) {
                    handlePurchase(context, purchase)
                }
            } else {
                clearPurchased(context)
            }
        }
    }

    private fun clearPurchased(context: Context) {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PrefsKeys.IS_PURCHASED, false)) {
            prefs.edit().putBoolean(PrefsKeys.IS_PURCHASED, false).apply()
            _isPurchased.value = false
        }
    }

    private fun handlePurchase(context: Context, purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        savePurchased(context)

        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            scope.launch {
                billingClient.acknowledgePurchase(ackParams)
            }
        }
    }

    private fun savePurchased(context: Context) {
        context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
            .edit().putBoolean(PrefsKeys.IS_PURCHASED, true).apply()
        _isPurchased.value = true
    }
}
