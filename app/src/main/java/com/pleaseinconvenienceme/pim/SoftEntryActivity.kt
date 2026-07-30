package com.pleaseinconvenienceme.pim

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

class SoftEntryActivity : ComponentActivity() {
    companion object {
        val isActive = AtomicBoolean(false)
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onStart() {
        super.onStart()
        isActive.set(true)
        requestAudioFocus()
    }

    override fun onStop() {
        isActive.set(false)
        abandonAudioFocus()
        super.onStop()
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
            )
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(it)
        }
        audioFocusRequest = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra("APP_NAME") ?: "Unknown App"
        val packageName = intent.getStringExtra("PACKAGE_NAME") ?: ""

        val resolver = AppSettingsResolver(this, appName)
        val reminderDelay = resolver.getInt(PrefsKeys.REVEAL_SECONDS, PrefsKeys.DEFAULT_REVEAL_SECONDS)

        val reviewHelper = ReviewHelperImpl()

        setContent {
            PimTheme {
                var showReviewDialog by remember { mutableStateOf(false) }

                val finishCancel = {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish()
                }

                if (showReviewDialog) {
                    ReviewPromptDialog(
                        onSure = {
                            showReviewDialog = false
                            markReviewNeverAsk(this@SoftEntryActivity)
                            reviewHelper.launchReview(this@SoftEntryActivity)
                            finishCancel()
                        },
                        onMaybeLater = {
                            showReviewDialog = false
                            markReviewMaybeLater(this@SoftEntryActivity)
                            finishCancel()
                        },
                        onNeverAsk = {
                            showReviewDialog = false
                            markReviewNeverAsk(this@SoftEntryActivity)
                            finishCancel()
                        },
                        onDismiss = {
                            showReviewDialog = false
                            markReviewMaybeLater(this@SoftEntryActivity)
                            finishCancel()
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SoftEntryScreen(
                        appName = appName,
                        packageName = packageName,
                        reminderDelay = reminderDelay,
                        onGotIt = {
                            recordInteraction(this@SoftEntryActivity)
                            grantAppSession(this@SoftEntryActivity, appName)
                            finish()
                        },
                        onCancel = {
                            recordInteraction(this@SoftEntryActivity)
                            if (shouldPromptReview(this@SoftEntryActivity)) {
                                showReviewDialog = true
                            } else {
                                finishCancel()
                            }
                        }
                    )
                }
            }
        }
    }

}

@Composable
fun SoftEntryScreen(
    appName: String,
    packageName: String = "",
    reminderDelay: Int = PrefsKeys.DEFAULT_REVEAL_SECONDS,
    onGotIt: () -> Unit,
    onCancel: () -> Unit
) {
    var dotsRemaining by remember { mutableIntStateOf(reminderDelay) }
    LaunchedEffect(Unit) {
        if (reminderDelay > 0) {
            repeat(reminderDelay) {
                delay(1000L)
                dotsRemaining--
            }
            onGotIt()
        }
    }

    Scaffold(topBar = { PimTopBar(appName) }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            if (reminderDelay > 0) {
                Text(
                    text = "$appName opens in…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                CountdownRing(totalSeconds = reminderDelay, secondsRemaining = dotsRemaining)
                Spacer(modifier = Modifier.height(48.dp))
            }
            if (reminderDelay <= 0) {
                Button(onClick = onGotIt, modifier = Modifier.fillMaxWidth(0.6f)) { Text("Got it") }
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(0.6f)) { Text("Cancel") }
        }
    }
}
