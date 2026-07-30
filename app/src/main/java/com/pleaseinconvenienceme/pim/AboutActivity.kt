package com.pleaseinconvenienceme.pim

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        val versionCode = BuildConfig.VERSION_CODE
        setContent {
            PimTheme {
                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {},
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = if (isSystemInDarkTheme()) Color(0xFF003A57) else MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                ) { paddingValues ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                val quoteAlpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = tween(durationMillis = 1500),
                    label = "quoteAlpha"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSystemInDarkTheme()) Color(0xFF003A57) else MaterialTheme.colorScheme.primary)
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "You don\u2019t need\nmore willpower.",
                        fontSize = 34.sp,
                        fontFamily = PlayfairDisplay,
                        textAlign = TextAlign.Start,
                        lineHeight = 44.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth().alpha(quoteAlpha)
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        text = "You need more\ninconvenience.",
                        fontSize = 48.sp,
                        fontFamily = PlayfairDisplay,
                        textAlign = TextAlign.End,
                        lineHeight = 58.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxWidth().alpha(quoteAlpha)
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.4f),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        text = "PIM",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BrandFont,
                        letterSpacing = 4.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please Inconvenience Me",
                        fontSize = 28.sp,
                        fontFamily = BrandFont,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pleaseinconvenienceme.com")))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("PleaseInconvenienceMe.com")
                        }

                        OutlinedButton(
                            onClick = {
                                val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                                val subject = Uri.encode("PIM Feedback (v$version)")
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:hello@pleaseinconvenienceme.com?subject=$subject")
                                }
                                startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Contact")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                                } catch (e: Exception) {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Rate this app")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var versionTapCount by remember { mutableIntStateOf(0) }
                    var showDevMenu by remember { mutableStateOf(intent.getBooleanExtra("open_dev_tools", false)) }

                    Text(
                        text = "Version $versionName ($versionCode) · ${BuildConfig.DISTRIBUTION}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            versionTapCount++
                            if (versionTapCount >= 7) {
                                versionTapCount = 0
                                showDevMenu = true
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (showDevMenu) {
                        val devPrefs = getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
                        var devKey by remember { mutableIntStateOf(0) }
                        AlertDialog(
                            onDismissRequest = { showDevMenu = false },
                            title = { Text("Dev Tools") },
                            text = {
                                val prefs = remember(devKey) { getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE) }
                                val lockNudge = remember(devKey) { prefs.getInt(PrefsKeys.LOCK_NUDGE_COUNT, 0) }
                                val trialDay = remember(devKey) { TrialHelper.getTrialDay(this@AboutActivity) }
                                val trialState = remember(devKey) { TrialHelper.getTrialState(this@AboutActivity) }
                                val isPurchased = remember(devKey) { prefs.getBoolean(PrefsKeys.IS_PURCHASED, false) }
                                val isLegacy = remember(devKey) { TrialHelper.isLegacyUser(this@AboutActivity) }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    TextButton(onClick = {
                                        showDevMenu = false
                                        devPrefs.edit()
                                            .putBoolean(PrefsKeys.SETUP_COMPLETE, false)
                                            .putBoolean(PrefsKeys.FIRST_LAUNCH_HINT_SHOWN, false)
                                            .putBoolean(PrefsKeys.SETTINGS_NUDGE_SHOWN, false)
                                            .putBoolean(PrefsKeys.TASK_NUDGE_SHOWN, false)
                                            .putInt(PrefsKeys.RESTRICTION_COUNT, 0)
                                            .commit()
                                        startActivity(Intent(this@AboutActivity, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        })
                                    }) { Text("Onboarding") }
                                    TextButton(onClick = {
                                        devPrefs.edit().putInt(PrefsKeys.LOCK_NUDGE_COUNT, 0).apply()
                                        devKey++
                                    }) { Text("Reset lock tip on remove ($lockNudge of 5)") }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Text("Trial", style = MaterialTheme.typography.titleSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                        (1..4).forEach { day ->
                                            val selected = trialDay == day
                                            TextButton(
                                                onClick = { TrialHelper.setTrialDay(this@AboutActivity, day); devKey++ },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier
                                                    .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                                                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)) else Modifier)
                                            ) { Text("$day", fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                        (5..7).forEach { day ->
                                            val selected = trialDay == day
                                            TextButton(
                                                onClick = { TrialHelper.setTrialDay(this@AboutActivity, day); devKey++ },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier
                                                    .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                                                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)) else Modifier)
                                            ) { Text("$day", fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                                        }
                                        val xSelected = trialDay == 8
                                        TextButton(
                                            onClick = { TrialHelper.setTrialDay(this@AboutActivity, 8); devKey++ },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                                                .then(if (xSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)) else Modifier)
                                        ) { Text("X", fontWeight = if (xSelected) FontWeight.Bold else FontWeight.Normal) }
                                    }
                                    Text("Day $trialDay — ${trialState.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (isPurchased) "Purchased: YES" else "Purchased: NO", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                        OutlinedButton(onClick = {
                                            val newValue = !isPurchased
                                            devPrefs.edit().putBoolean(PrefsKeys.IS_PURCHASED, newValue).apply()
                                            devKey++
                                        }) { Text("Switch") }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (isLegacy) "Legacy: YES (grandfathered)" else "Legacy: NO (new user)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                        OutlinedButton(onClick = {
                                            TrialHelper.setDevLegacy(this@AboutActivity, !isLegacy)
                                            devKey++
                                        }) { Text("Switch") }
                                    }
                                    TextButton(onClick = {
                                        showDevMenu = false
                                        startActivity(Intent(this@AboutActivity, MainActivity::class.java).apply {
                                            putExtra("preview_thank_you", true)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        })
                                    }) { Text("Preview post-purchase thank you") }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    val monitorLogEnabled = remember(devKey) { MonitorEventLog.isEnabled(this@AboutActivity) }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (monitorLogEnabled) "Monitor log: ON" else "Monitor log: OFF", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                        OutlinedButton(onClick = {
                                            MonitorEventLog.setEnabled(this@AboutActivity, !monitorLogEnabled)
                                            devKey++
                                        }) { Text("Switch") }
                                        OutlinedButton(onClick = {
                                            MonitorEventLog.clear()
                                            devKey++
                                        }) { Text("Clear") }
                                    }
                                    if (monitorLogEnabled) {
                                        Button(
                                            onClick = {
                                                MonitorEventLog.snapshot(this@AboutActivity)
                                                devKey++
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Bug! Snapshot log") }
                                    }
                                    if (monitorLogEnabled) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                if (MonitorEventLog.events.isEmpty()) {
                                                    Text("(no events yet)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                                } else {
                                                    MonitorEventLog.events.asReversed().forEach { (ts, msg) ->
                                                        Text(
                                                            "${MonitorEventLog.formatTimestamp(ts)}  $msg",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showDevMenu = false }) { Text("OK") }
                            }
                        )
                    }


                }
                }
            }
        }
    }
}
