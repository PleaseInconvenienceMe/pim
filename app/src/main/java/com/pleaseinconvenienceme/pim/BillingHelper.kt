package com.pleaseinconvenienceme.pim

import android.app.Activity
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.StateFlow

interface BillingHelper {
    val isPurchased: StateFlow<Boolean>
    val price: StateFlow<String?>
    fun initialize(activity: ComponentActivity)
    fun launchBillingFlow(activity: Activity)
    fun refreshPurchases()
}
