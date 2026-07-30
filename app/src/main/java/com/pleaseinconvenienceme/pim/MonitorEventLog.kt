package com.pleaseinconvenienceme.pim

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MonitorEventLog {
    private const val PREF_ENABLED = "monitor_log_enabled"
    private const val MAX_AGE_MS = 30 * 60 * 1000L // keep last 30 minutes

    /** Each entry: (timestampMs, message). Newest are appended at the end. */
    val events = mutableStateListOf<Pair<Long, String>>()

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_ENABLED, enabled).apply()
        if (!enabled) clear()
    }

    fun log(context: Context, msg: String) {
        if (!isEnabled(context)) return
        // Mirror to Android's system log so a connected machine can pull the trace over USB
        // via `adb logcat -s PIMmon:*` without any on-device copy-paste.
        android.util.Log.i("PIMmon", msg)
        val now = System.currentTimeMillis()
        events.add(now to msg)
        val cutoff = now - MAX_AGE_MS
        while (events.isNotEmpty() && events.first().first < cutoff) {
            events.removeAt(0)
        }
    }

    fun clear() {
        events.clear()
    }

    fun formatTimestamp(ts: Long): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(ts))

    /** Adds a user marker and copies the recent log to clipboard. */
    fun snapshot(context: Context) {
        try {
            val now = System.currentTimeMillis()
            events.add(now to "*** USER-MARKED BUG HERE ***")
            // Cap the snapshot to the last 500 entries to keep clipboard payload manageable
            val recent = if (events.size > 500) events.takeLast(500) else events.toList()
            val text = buildString(recent.size * 60) {
                if (events.size > 500) {
                    append("(truncated to last 500 of ${events.size} entries)\n")
                }
                for ((ts, msg) in recent) {
                    append(formatTimestamp(ts))
                    append("  ")
                    append(msg)
                    append('\n')
                }
            }
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("PIM monitor log", text))
            android.widget.Toast.makeText(context, "Log copied (${recent.size} entries)", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            android.util.Log.e("PIM", "snapshot error", e)
            android.widget.Toast.makeText(context, "Snapshot failed: ${e.javaClass.simpleName}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
