package com.pleaseinconvenienceme.pim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "com.pleaseinconvenienceme.pim.WATCHDOG" -> {
                val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
                val restrictedApps = prefs.getStringSet(PrefsKeys.RESTRICTED_APPS, emptySet()) ?: emptySet()

                if (restrictedApps.isNotEmpty()) {
                    // Background starts can be refused on newer Android; never crash the
                    // receiver over it — the next start opportunity picks the service up.
                    try {
                        context.startForegroundService(Intent(context, AppMonitorService::class.java))
                    } catch (e: Exception) {
                        android.util.Log.e("PIM", "BootReceiver service start refused", e)
                    }
                }

                // Reschedule the next watchdog alarm to keep the chain alive
                if (intent.action == "com.pleaseinconvenienceme.pim.WATCHDOG") {
                    AppMonitorService.scheduleWatchdog(context)
                }
            }
        }
    }
}
