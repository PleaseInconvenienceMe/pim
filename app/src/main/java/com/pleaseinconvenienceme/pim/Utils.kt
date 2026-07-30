package com.pleaseinconvenienceme.pim

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Grants a timed session for the given app, based on its per-app duration setting.
 */
fun grantAppSession(context: Context, appName: String) {
    val sessionPrefs = context.getSharedPreferences(PrefsKeys.SESSIONS, Context.MODE_PRIVATE)
    val durationPrefs = context.getSharedPreferences(PrefsKeys.DURATIONS, Context.MODE_PRIVATE)
    val sessionDuration = durationPrefs.getInt(appName, PrefsKeys.DEFAULT_SESSION_MINUTES)
    val sessionEnd = System.currentTimeMillis() + (sessionDuration * 60 * 1000L)
    sessionPrefs.edit().putLong(appName, sessionEnd).apply()
}

/**
 * Returns which session number the CURRENT open is — (completed sessions still inside the 60-min
 * window) + 1 — WITHOUT recording anything. Read-only: used when the task screen appears to
 * compute the reveal delay and the "(Nth session)" label. A fresh window (no prior session, or
 * the last completed one was over 60 min ago) makes this the 1st session.
 *
 * Counting is completions-only: cancelling a task must NOT escalate the delay — backing out is
 * the user resisting, and accidental opens shouldn't be punished. The counter is advanced
 * separately by recordCompletedSession(), called only when a task is actually finished.
 */
fun peekSessionNumber(context: Context, appName: String): Int {
    val prefs = context.getSharedPreferences(PrefsKeys.SESSIONS, Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val last = prefs.getLong(PrefsKeys.LAST_SESSION_PREFIX + appName, 0L)
    val count = prefs.getInt(PrefsKeys.SESSION_COUNT_PREFIX + appName, 0)
    val completedInWindow = if (last == 0L || now - last > PrefsKeys.SESSION_RESET_WINDOW_MS) 0 else count
    return completedInWindow + 1
}

/**
 * Records that a session was actually COMPLETED for this app: bumps the per-app session count
 * (reset to 1 if the previous completed session was over 60 min ago) and stamps the time. Call
 * only on task success, never on cancel — that is what makes the escalating delay count completed
 * sessions rather than mere attempts. Kept in step with peekSessionNumber().
 */
fun recordCompletedSession(context: Context, appName: String) {
    val prefs = context.getSharedPreferences(PrefsKeys.SESSIONS, Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val last = prefs.getLong(PrefsKeys.LAST_SESSION_PREFIX + appName, 0L)
    val prevCount = prefs.getInt(PrefsKeys.SESSION_COUNT_PREFIX + appName, 0)
    val newCount = if (last == 0L || now - last > PrefsKeys.SESSION_RESET_WINDOW_MS) 1 else prevCount + 1
    prefs.edit()
        .putLong(PrefsKeys.LAST_SESSION_PREFIX + appName, now)
        .putInt(PrefsKeys.SESSION_COUNT_PREFIX + appName, newCount)
        .apply()
}

fun logUsage(context: Context, appName: String, durationMs: Long) {
    if (durationMs <= 0) return
    val prefs = context.getSharedPreferences(PrefsKeys.USAGE_LOG, Context.MODE_PRIVATE)
    val arr = JSONArray(prefs.getString(appName, "[]"))
    arr.put(JSONObject().apply {
        put("t", System.currentTimeMillis())
        put("d", durationMs)
    })
    prefs.edit().putString(appName, arr.toString()).apply()
}

fun get7DayMinutes(context: Context, appName: String): Int {
    val prefs = context.getSharedPreferences(PrefsKeys.USAGE_LOG, Context.MODE_PRIVATE)
    val arr = JSONArray(prefs.getString(appName, "[]"))
    val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
    var totalMs = 0L
    for (i in 0 until arr.length()) {
        val entry = arr.getJSONObject(i)
        if (entry.getLong("t") >= cutoff) totalMs += entry.getLong("d")
    }
    if (totalMs <= 0) return 0
    return (totalMs / 60_000L).toInt().coerceAtLeast(1)
}

fun clearUsageLog(context: Context, appName: String) {
    context.getSharedPreferences(PrefsKeys.USAGE_LOG, Context.MODE_PRIVATE)
        .edit().remove(appName).apply()
}

fun formatUsageTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h hr"
        else -> "$h hr $m min"
    }
}

/**
 * Returns true if the ResolveInfo represents a PWA (Progressive Web App) shortcut.
 */
fun isPWA(resolveInfo: android.content.pm.ResolveInfo): Boolean {
    val packageName = resolveInfo.activityInfo.packageName
    val activityName = resolveInfo.activityInfo.name

    if (packageName.startsWith("org.chromium.webapk")) return true
    if (isPWAClassName(activityName)) return true
    if (packageName == "com.google.android.apps.chrome" && activityName.contains("webapp")) return true

    return false
}

/**
 * Returns true if the class name looks like a PWA activity.
 */
fun isPWAClassName(className: String): Boolean {
    return className.contains("WebappActivity") ||
        className.contains("WebApkActivity") ||
        className.contains("WebappLauncherActivity")
}
