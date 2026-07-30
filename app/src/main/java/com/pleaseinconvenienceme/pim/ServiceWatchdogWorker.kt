package com.pleaseinconvenienceme.pim

import android.content.Context
import android.content.Intent
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ServiceWatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        val restrictedApps = prefs.getStringSet(PrefsKeys.RESTRICTED_APPS, emptySet()) ?: emptySet()
        if (restrictedApps.isNotEmpty()) {
            // Workers run in the background, where foreground-service starts can be
            // refused on newer Android; never crash over it — the next start
            // opportunity picks the service up.
            try {
                applicationContext.startForegroundService(
                    Intent(applicationContext, AppMonitorService::class.java)
                )
            } catch (e: Exception) {
                android.util.Log.e("PIM", "Watchdog worker service start refused", e)
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "pim_service_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
