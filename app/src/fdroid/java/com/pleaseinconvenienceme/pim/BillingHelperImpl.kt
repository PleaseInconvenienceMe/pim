package com.pleaseinconvenienceme.pim

import android.app.Activity
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BillingHelperImpl : BillingHelper {
    override val isPurchased: StateFlow<Boolean> = MutableStateFlow(true) // always unlocked
    override val price: StateFlow<String?> = MutableStateFlow(null)
    override fun initialize(activity: ComponentActivity) {}
    override fun launchBillingFlow(activity: Activity) {}
    override fun refreshPurchases() {}
}
