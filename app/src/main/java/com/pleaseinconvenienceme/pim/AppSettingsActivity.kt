package com.pleaseinconvenienceme.pim

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

class AppSettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra("APP_NAME") ?: run { finish(); return }
        val packageName = intent.getStringExtra("PACKAGE_NAME") ?: ""
        val sessionUnlocked = intent.getBooleanExtra("SESSION_UNLOCKED", false)

        setContent {
            PimTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                val previewState = remember { mutableStateOf(false) }
                val resolver = remember { AppSettingsResolver(context, appName) }
                val isLocked = remember { resolver.isLocked() }
                var isSessionUnlocked by remember { mutableStateOf(sessionUnlocked) }
                var showPasswordPrompt by remember { mutableStateOf(false) }
                var showLockInfoDialog by remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }

                val settingsEnabled = !isLocked || isSessionUnlocked

                // Password prompt dialog (to unlock this session for editing)
                if (showPasswordPrompt) {
                    PasswordPromptDialog(
                        title = "Enter PIM password",
                        onUnlock = { password ->
                            if (resolver.checkPassword(password)) {
                                isSessionUnlocked = true
                                showPasswordPrompt = false
                                true
                            } else {
                                false
                            }
                        },
                        onDismiss = { showPasswordPrompt = false }
                    )
                }

                // Lock info dialog (redirect to Default options)
                if (showLockInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showLockInfoDialog = false }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    "Can't change the lock here — it's global. Tap the padlock in Options (Default).",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { showLockInfoDialog = false }) { Text("Cancel") }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = {
                                        showLockInfoDialog = false
                                        val resultData = android.content.Intent().putExtra("OPEN_OPTIONS_TAB", true)
                                        setResult(Activity.RESULT_OK, resultData)
                                        finish()
                                    }) { Text("Options") }
                                }
                            }
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            expandedHeight = 96.dp,
                            title = {
                                Column(verticalArrangement = Arrangement.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            appName,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        // Padlock icon next to app name (reflects global lock state)
                                        IconButton(onClick = { showLockInfoDialog = true }) {
                                            Icon(
                                                painter = painterResource(
                                                    if (isLocked) R.drawable.ic_lock_closed
                                                    else R.drawable.ic_lock_open
                                                ),
                                                contentDescription = if (isLocked) "Locked" else "Unlocked",
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    if (isLocked && isSessionUnlocked) {
                                        Text(
                                            "Locked · editing settings",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                Box(modifier = Modifier.padding(end = 16.dp)) {
                                    OutlinedButton(
                                        onClick = { previewState.value = true },
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Text("Try it", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                ) { paddingValues ->
                    SettingsContent(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        appName = appName,
                        packageName = packageName,
                        showPreviewState = previewState,
                        enabled = settingsEnabled
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordPromptDialog(
    title: String = "Enter PIM password",
    body: String = "",
    onUnlock: (password: String) -> Boolean,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (body.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Plain text intentional — this is a low-stakes settings lock, not a credential.
                // KeyboardType.Password disables autocorrect/suggestions/capitalize without masking text.
                OutlinedTextField(
                    value = password,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 20.sp, letterSpacing = 2.sp),
                    onValueChange = {
                        password = it
                        showError = false
                    },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {{ Text("Wrong password") }} else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrect = false,
                        capitalization = KeyboardCapitalization.None
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (!onUnlock(password)) {
                                showError = true
                            }
                        }
                    ) { Text("OK") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePasswordDialog(
    appName: String,
    onConfirm: (password: String) -> Unit,
    onDismiss: () -> Unit
) {
    val chars = "abcdefghjkmnpqrstuvwxyz23456789"
    val generated = remember {
        (1..6).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("")
    }
    var step by remember { mutableStateOf(1) }
    var confirmInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (step == 1) {
                    Text(
                        "Lock settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Write this down. You'll need it to change any PIM settings.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            generated,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { step = 2 }) { Text("OK") }
                    }
                } else if (step == 2) {
                    Text("Confirm PIM password", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmInput,
                        onValueChange = { confirmInput = it; showError = false },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 20.sp, letterSpacing = 2.sp),
                        singleLine = true,
                        isError = showError,
                        supportingText = if (showError) {{ Text("Doesn't match") }} else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (confirmInput == generated) {
                                    step = 3
                                } else {
                                    showError = true
                                }
                            }
                        ) { Text("Confirm") }
                    }
                } else if (step == 3) {
                    Text(
                        "Settings locked",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Save your password — you need it to change settings.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = { onConfirm(generated) }) { Text("OK") }
                    }
                }
            }
        }
    }
}
