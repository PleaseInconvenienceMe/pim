package com.pleaseinconvenienceme.pim

import android.content.Context
import java.security.MessageDigest

/**
 * Resolves per-app settings with fallback to global settings.
 * Keys are stored as "$appName::$settingKey" in the APP_OVERRIDES prefs file.
 */
class AppSettingsResolver(private val context: Context, private val appName: String) {

    private val overridePrefs
        get() = context.getSharedPreferences(PrefsKeys.APP_OVERRIDES, Context.MODE_PRIVATE)

    private val globalPrefs
        get() = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)

    private val prefix = "$appName::"

    private fun overrideKey(key: String) = "$prefix$key"

    fun getInt(key: String, defaultValue: Int): Int {
        val k = overrideKey(key)
        return if (overridePrefs.contains(k)) overridePrefs.getInt(k, defaultValue)
        else globalPrefs.getInt(key, defaultValue)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val k = overrideKey(key)
        return if (overridePrefs.contains(k)) overridePrefs.getBoolean(k, defaultValue)
        else globalPrefs.getBoolean(key, defaultValue)
    }

    fun getString(key: String, defaultValue: String?): String? {
        val k = overrideKey(key)
        return if (overridePrefs.contains(k)) overridePrefs.getString(k, defaultValue)
        else globalPrefs.getString(key, defaultValue)
    }

    fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? {
        val k = overrideKey(key)
        return if (overridePrefs.contains(k)) overridePrefs.getStringSet(k, defaultValue)
        else globalPrefs.getStringSet(key, defaultValue)
    }

    fun hasAnyOverrides(): Boolean {
        return overridePrefs.all.keys.any { it.startsWith(prefix) }
    }

    fun clearAllOverrides() {
        val editor = overridePrefs.edit()
        overridePrefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun isLocked(): Boolean =
        globalPrefs.getBoolean(PrefsKeys.LOCK_ALL_RESTRICTED, PrefsKeys.DEFAULT_LOCK_ALL_RESTRICTED)

    fun checkPassword(password: String): Boolean {
        val stored = globalPrefs.getString(PrefsKeys.GLOBAL_LOCK_HASH, null)
            ?: return false
        return stored == hashPassword(password)
    }

    fun hasGlobalPassword(): Boolean =
        globalPrefs.contains(PrefsKeys.GLOBAL_LOCK_HASH)

    fun setGlobalPassword(password: String) {
        globalPrefs.edit()
            .putString(PrefsKeys.GLOBAL_LOCK_HASH, hashPassword(password))
            .apply()
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun matchesGlobals(): Boolean {
        val prefs = globalPrefs
        fun g(key: String, default: Int) = prefs.getInt(key, default)
        fun o(key: String, default: Int) = overridePrefs.getInt(overrideKey(key), default)

        if (o(PrefsKeys.TASK_TYPE, PrefsKeys.DEFAULT_TASK_TYPE) != g(PrefsKeys.TASK_TYPE, PrefsKeys.DEFAULT_TASK_TYPE)) return false
        if (o(PrefsKeys.DIFFICULTY_LEVEL, PrefsKeys.DEFAULT_DIFFICULTY) != g(PrefsKeys.DIFFICULTY_LEVEL, PrefsKeys.DEFAULT_DIFFICULTY)) return false
        if (o(PrefsKeys.REVEAL_SECONDS, PrefsKeys.DEFAULT_REVEAL_SECONDS) != g(PrefsKeys.REVEAL_SECONDS, PrefsKeys.DEFAULT_REVEAL_SECONDS)) return false
        if (o(PrefsKeys.REPEAT_DELAY_INCREMENT, PrefsKeys.DEFAULT_REPEAT_DELAY_INCREMENT) != g(PrefsKeys.REPEAT_DELAY_INCREMENT, PrefsKeys.DEFAULT_REPEAT_DELAY_INCREMENT)) return false
        if (o(PrefsKeys.TYPING_DIFFICULTY, PrefsKeys.DEFAULT_TYPING_DIFFICULTY) != g(PrefsKeys.TYPING_DIFFICULTY, PrefsKeys.DEFAULT_TYPING_DIFFICULTY)) return false
        if (o(PrefsKeys.TYPING_CHAR_SET, PrefsKeys.DEFAULT_TYPING_CHAR_SET) != g(PrefsKeys.TYPING_CHAR_SET, PrefsKeys.DEFAULT_TYPING_CHAR_SET)) return false
        if (o(PrefsKeys.TYPING_LENGTH, PrefsKeys.DEFAULT_TYPING_LENGTH) != g(PrefsKeys.TYPING_LENGTH, PrefsKeys.DEFAULT_TYPING_LENGTH)) return false
        if (o(PrefsKeys.TAPPING_DIFFICULTY, PrefsKeys.DEFAULT_TAPPING_DIFFICULTY) != g(PrefsKeys.TAPPING_DIFFICULTY, PrefsKeys.DEFAULT_TAPPING_DIFFICULTY)) return false
        if (o(PrefsKeys.TAPPING_DOT_COUNT, PrefsKeys.DEFAULT_TAPPING_DOTS) != g(PrefsKeys.TAPPING_DOT_COUNT, PrefsKeys.DEFAULT_TAPPING_DOTS)) return false
        if (o(PrefsKeys.TAPPING_DOT_DELAY, PrefsKeys.DEFAULT_TAPPING_DOT_DELAY) != g(PrefsKeys.TAPPING_DOT_DELAY, PrefsKeys.DEFAULT_TAPPING_DOT_DELAY)) return false
        if (o(PrefsKeys.CUSTOM_OPERANDS, PrefsKeys.DEFAULT_OPERANDS) != g(PrefsKeys.CUSTOM_OPERANDS, PrefsKeys.DEFAULT_OPERANDS)) return false
        if (o(PrefsKeys.CUSTOM_RANGE_MIN, PrefsKeys.DEFAULT_RANGE_MIN) != g(PrefsKeys.CUSTOM_RANGE_MIN, PrefsKeys.DEFAULT_RANGE_MIN)) return false
        if (o(PrefsKeys.CUSTOM_RANGE_MAX, PrefsKeys.DEFAULT_RANGE_MAX) != g(PrefsKeys.CUSTOM_RANGE_MAX, PrefsKeys.DEFAULT_RANGE_MAX)) return false
        val defaultOps = setOf("+")
        val overrideOps = overridePrefs.getStringSet(overrideKey(PrefsKeys.CUSTOM_OPERATIONS), defaultOps) ?: defaultOps
        val globalOps = prefs.getStringSet(PrefsKeys.CUSTOM_OPERATIONS, defaultOps) ?: defaultOps
        if (overrideOps != globalOps) return false
        if (overridePrefs.getBoolean(overrideKey(PrefsKeys.OVERLAY_TIMER), PrefsKeys.DEFAULT_OVERLAY_TIMER) != prefs.getBoolean(PrefsKeys.OVERLAY_TIMER, PrefsKeys.DEFAULT_OVERLAY_TIMER)) return false
        if (o(PrefsKeys.OVERLAY_TIMER_SIZE, PrefsKeys.DEFAULT_OVERLAY_SIZE) != g(PrefsKeys.OVERLAY_TIMER_SIZE, PrefsKeys.DEFAULT_OVERLAY_SIZE)) return false

        return true
    }

    /**
     * Captures all current per-app override values (including lock_hash if set) as a map.
     * Returns empty map if no overrides exist.
     */
    fun saveSnapshot(): Map<String, Any?> {
        val snapshot = mutableMapOf<String, Any?>()
        val allOverrides = overridePrefs.all
        for ((key, value) in allOverrides) {
            if (key.startsWith(prefix)) {
                snapshot[key] = value
            }
        }
        return snapshot
    }

    /**
     * Restores a previously saved snapshot, replacing all current overrides
     * (including lock_hash) with the snapshot values.
     */
    fun restoreSnapshot(snapshot: Map<String, Any?>) {
        val editor = overridePrefs.edit()
        // Remove all current overrides including lock_hash
        overridePrefs.all.keys
            .filter { it.startsWith(prefix) }
            .forEach { editor.remove(it) }
        // Write back snapshot values
        for ((key, value) in snapshot) {
            when (value) {
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
            }
        }
        editor.apply()
    }

    fun snapshotGlobals() {
        val prefs = globalPrefs
        val editor = overridePrefs.edit()

        // Clear any existing overrides for this app first
        overridePrefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }

        fun k(key: String) = overrideKey(key)

        editor.putInt(k(PrefsKeys.TASK_TYPE), prefs.getInt(PrefsKeys.TASK_TYPE, PrefsKeys.DEFAULT_TASK_TYPE))
        editor.putInt(k(PrefsKeys.DIFFICULTY_LEVEL), prefs.getInt(PrefsKeys.DIFFICULTY_LEVEL, PrefsKeys.DEFAULT_DIFFICULTY))
        editor.putInt(k(PrefsKeys.REVEAL_SECONDS), prefs.getInt(PrefsKeys.REVEAL_SECONDS, PrefsKeys.DEFAULT_REVEAL_SECONDS))
        editor.putInt(k(PrefsKeys.REPEAT_DELAY_INCREMENT), prefs.getInt(PrefsKeys.REPEAT_DELAY_INCREMENT, PrefsKeys.DEFAULT_REPEAT_DELAY_INCREMENT))
        editor.putInt(k(PrefsKeys.TYPING_DIFFICULTY), prefs.getInt(PrefsKeys.TYPING_DIFFICULTY, PrefsKeys.DEFAULT_TYPING_DIFFICULTY))
        editor.putInt(k(PrefsKeys.TYPING_CHAR_SET), prefs.getInt(PrefsKeys.TYPING_CHAR_SET, PrefsKeys.DEFAULT_TYPING_CHAR_SET))
        editor.putInt(k(PrefsKeys.TYPING_LENGTH), prefs.getInt(PrefsKeys.TYPING_LENGTH, PrefsKeys.DEFAULT_TYPING_LENGTH))
        editor.putInt(k(PrefsKeys.TAPPING_DIFFICULTY), prefs.getInt(PrefsKeys.TAPPING_DIFFICULTY, PrefsKeys.DEFAULT_TAPPING_DIFFICULTY))
        editor.putInt(k(PrefsKeys.TAPPING_DOT_COUNT), prefs.getInt(PrefsKeys.TAPPING_DOT_COUNT, PrefsKeys.DEFAULT_TAPPING_DOTS))
        editor.putInt(k(PrefsKeys.TAPPING_DOT_DELAY), prefs.getInt(PrefsKeys.TAPPING_DOT_DELAY, PrefsKeys.DEFAULT_TAPPING_DOT_DELAY))
        editor.putInt(k(PrefsKeys.CUSTOM_OPERANDS), prefs.getInt(PrefsKeys.CUSTOM_OPERANDS, PrefsKeys.DEFAULT_OPERANDS))
        editor.putInt(k(PrefsKeys.CUSTOM_RANGE_MIN), prefs.getInt(PrefsKeys.CUSTOM_RANGE_MIN, PrefsKeys.DEFAULT_RANGE_MIN))
        editor.putInt(k(PrefsKeys.CUSTOM_RANGE_MAX), prefs.getInt(PrefsKeys.CUSTOM_RANGE_MAX, PrefsKeys.DEFAULT_RANGE_MAX))
        editor.putStringSet(k(PrefsKeys.CUSTOM_OPERATIONS), prefs.getStringSet(PrefsKeys.CUSTOM_OPERATIONS, setOf("+")) ?: setOf("+"))
        editor.putBoolean(k(PrefsKeys.OVERLAY_TIMER), prefs.getBoolean(PrefsKeys.OVERLAY_TIMER, PrefsKeys.DEFAULT_OVERLAY_TIMER))

        editor.apply()
    }
}
