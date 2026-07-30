package com.pleaseinconvenienceme.pim

import android.content.Context

enum class TrialState {
    PRE_TRIAL,
    IN_TRIAL,
    EXPIRED
}

object TrialHelper {

    // Users who installed before this date are grandfathered in (no trial, full access).
    // Set this to the publish date of the trial update.
    private const val TRIAL_CUTOFF_MILLIS = 1777507199000L // April 29, 2026 23:59:59 UTC — update if publish date changes
    private const val DEV_IGNORE_LEGACY = "dev_ignore_legacy"
    private const val DEV_FORCE_LEGACY = "dev_force_legacy"

    fun isLegacyUser(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        // Dev overrides
        if (prefs.getBoolean(DEV_FORCE_LEGACY, false)) return true
        if (prefs.getBoolean(DEV_IGNORE_LEGACY, false)) return false
        // Real check
        val installDate = prefs.getLong(PrefsKeys.INSTALL_DATE, 0L)
        return installDate in 1 until TRIAL_CUTOFF_MILLIS
    }

    fun isDevLegacy(context: Context): Boolean? {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(DEV_FORCE_LEGACY, false)) return true
        if (prefs.getBoolean(DEV_IGNORE_LEGACY, false)) return false
        return null // no override, using real value
    }

    fun setDevLegacy(context: Context, legacy: Boolean?) {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        when (legacy) {
            true -> prefs.edit().putBoolean(DEV_FORCE_LEGACY, true).putBoolean(DEV_IGNORE_LEGACY, false).apply()
            false -> prefs.edit().putBoolean(DEV_FORCE_LEGACY, false).putBoolean(DEV_IGNORE_LEGACY, true).apply()
            null -> prefs.edit().putBoolean(DEV_FORCE_LEGACY, false).putBoolean(DEV_IGNORE_LEGACY, false).apply()
        }
    }

    fun isPurchased(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        return prefs.getBoolean(PrefsKeys.IS_PURCHASED, false)
    }

    fun hasFullAccess(context: Context): Boolean {
        if (!BuildConfig.ENFORCE_LIMIT) return true
        if (isPurchased(context)) return true
        if (isLegacyUser(context)) return true
        return getTrialState(context) != TrialState.EXPIRED
    }

    fun getTrialState(context: Context): TrialState {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        val trialStart = prefs.getLong(PrefsKeys.TRIAL_START, 0L)
        if (trialStart == 0L) return TrialState.PRE_TRIAL
        val elapsed = System.currentTimeMillis() - trialStart
        return if (elapsed < PrefsKeys.TRIAL_DURATION_MS) TrialState.IN_TRIAL
        else TrialState.EXPIRED
    }

    fun getTrialDaysRemaining(context: Context): Int {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        val trialStart = prefs.getLong(PrefsKeys.TRIAL_START, 0L)
        if (trialStart == 0L) return 7
        val remaining = PrefsKeys.TRIAL_DURATION_MS - (System.currentTimeMillis() - trialStart)
        if (remaining <= 0) return 0
        return (remaining / (24 * 60 * 60 * 1000)).toInt() + 1
    }

    fun getTrialDay(context: Context): Int {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        val trialStart = prefs.getLong(PrefsKeys.TRIAL_START, 0L)
        if (trialStart == 0L) return 0
        val elapsed = System.currentTimeMillis() - trialStart
        return ((elapsed / (24 * 60 * 60 * 1000)) + 1).toInt().coerceIn(1, 8)
    }

    fun startTrial(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        if (prefs.getLong(PrefsKeys.TRIAL_START, 0L) == 0L) {
            prefs.edit().putLong(PrefsKeys.TRIAL_START, System.currentTimeMillis()).apply()
            return true
        }
        return false
    }

    fun setTrialDay(context: Context, day: Int) {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        val backdated = System.currentTimeMillis() - ((day - 1) * 24L * 60 * 60 * 1000)
        prefs.edit()
            .putLong(PrefsKeys.TRIAL_START, backdated)
            .remove(PrefsKeys.TRIAL_CARD_LAST_SHOWN)
            .apply()
    }

    fun resetTrial(context: Context) {
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().remove(PrefsKeys.TRIAL_START).apply()
    }
}
