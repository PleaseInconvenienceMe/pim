package com.pleaseinconvenienceme.pim

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Thresholds for review prompt — lower these for testing
const val MIN_INTERACTIONS = 30
const val MIN_DAYS_SINCE_INSTALL = 3
const val MIN_DAYS_BETWEEN_PROMPTS = 30
const val MAYBE_LATER_INTERACTIONS = 30

interface ReviewHelper {
    fun launchReview(activity: Activity)
}

fun recordInteraction(context: Context) {
    val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
    // Set install date on first interaction
    if (prefs.getLong(PrefsKeys.INSTALL_DATE, 0L) == 0L) {
        prefs.edit().putLong(PrefsKeys.INSTALL_DATE, System.currentTimeMillis()).apply()
    }
    val count = prefs.getInt(PrefsKeys.INTERACTION_COUNT, 0)
    prefs.edit().putInt(PrefsKeys.INTERACTION_COUNT, count + 1).apply()
}

fun shouldPromptReview(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(PrefsKeys.REVIEW_NEVER_ASK, false)) return false
    val interactions = prefs.getInt(PrefsKeys.INTERACTION_COUNT, 0)
    val extraInteractions = prefs.getInt(PrefsKeys.REVIEW_EXTRA_INTERACTIONS, 0)
    val installDate = prefs.getLong(PrefsKeys.INSTALL_DATE, 0L)
    val lastPrompt = prefs.getLong(PrefsKeys.LAST_REVIEW_PROMPT, 0L)
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L

    if (interactions < MIN_INTERACTIONS + extraInteractions) return false
    if (installDate == 0L || now - installDate < MIN_DAYS_SINCE_INSTALL * dayMs) return false
    if (lastPrompt > 0 && now - lastPrompt < MIN_DAYS_BETWEEN_PROMPTS * dayMs) return false
    return true
}

fun markReviewPrompted(context: Context) {
    val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
    prefs.edit().putLong(PrefsKeys.LAST_REVIEW_PROMPT, System.currentTimeMillis()).apply()
}

fun markReviewMaybeLater(context: Context) {
    val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
    val current = prefs.getInt(PrefsKeys.REVIEW_EXTRA_INTERACTIONS, 0)
    prefs.edit().putInt(PrefsKeys.REVIEW_EXTRA_INTERACTIONS, current + MAYBE_LATER_INTERACTIONS).apply()
    markReviewPrompted(context)
}

fun markReviewNeverAsk(context: Context) {
    val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(PrefsKeys.REVIEW_NEVER_ASK, true).apply()
}

@Composable
fun ReviewPromptDialog(
    onSure: () -> Unit,
    onMaybeLater: () -> Unit,
    onNeverAsk: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review PIM?") },
        text = { Text("If PIM is helping you, a quick review goes a long way.") },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedButton(onClick = onSure, modifier = Modifier.fillMaxWidth()) {
                    Text("Sure")
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = onMaybeLater, modifier = Modifier.fillMaxWidth()) {
                    Text("Maybe later")
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = onNeverAsk, modifier = Modifier.fillMaxWidth()) {
                    Text("Don't ask again")
                }
            }
        },
        dismissButton = {}
    )
}
