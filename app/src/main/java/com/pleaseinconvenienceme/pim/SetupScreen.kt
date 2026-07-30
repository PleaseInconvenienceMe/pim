package com.pleaseinconvenienceme.pim

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner


@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    var step by remember { mutableStateOf(1) } // 1 = splash, 2 = permissions, 3 = all set

    when (step) {
        1 -> SplashStep(onContinue = { step = 2 })
        2 -> PermissionsStep(onContinue = { step = 3 })
        3 -> AllSetStep(onContinue = onSetupComplete)
    }
}

@Composable
fun AllSetStep(onContinue: () -> Unit) {
    val context = LocalContext.current
    if (BuildConfig.ENFORCE_LIMIT) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isSystemInDarkTheme()) Color(0xFF003A57) else MaterialTheme.colorScheme.primary)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

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

            Text(
                text = "Your 7-day\nfree trial\nstarts now.",
                fontSize = 44.sp,
                fontFamily = PlayfairDisplay,
                lineHeight = 54.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.3f).align(Alignment.Start),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Pick an app you want to use less.\nPIM will make it inconvenient.",
                fontSize = 24.sp,
                fontFamily = BrandFont,
                lineHeight = 32.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "After your trial, a one-time purchase\nkeeps PIM working forever.",
                fontSize = 20.sp,
                fontFamily = BrandFont,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    TrialHelper.startTrial(context)
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth(0.6f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Let's go")
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    "You're all set.",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BrandFont,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "On the next screen, pick an app you want to use less.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = BrandFont,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "PIM will make that app less convenient.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = BrandFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(48.dp))
                Button(onClick = { onContinue() }) {
                    Text("Let's go", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

val PlayfairDisplay = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display_italic, FontWeight.Normal, FontStyle.Italic)
)

// The PIM wordmark and onboarding face. Jost (SIL Open Font License) — a Futura
// revival chosen to replace the commercial Tw Cen MT, which couldn't ship in the
// open-source repo. Deliberately named for its role, not the typeface, so a
// future swap doesn't leave a misleading name behind.
val BrandFont = FontFamily(
    Font(R.font.jost, FontWeight.Normal),
    Font(R.font.jost_bold, FontWeight.Bold),
    Font(R.font.jost_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jost_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

@Composable
fun SplashStep(onContinue: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val quoteAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 2000)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color(0xFF003A57) else MaterialTheme.colorScheme.primary)
            .padding(24.dp),
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

        Spacer(modifier = Modifier.height(64.dp))

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

        OutlinedButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary)
        ) {
            Text("Get Started")
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun PermissionsStep(onContinue: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasUsageAccess by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var hasOverlay by remember { mutableStateOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
    ) }
    var hasBatteryUnrestricted by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    // Re-check permissions when the user comes back from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = hasUsageStatsPermission(context)
                hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    Settings.canDrawOverlays(context) else true
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                hasBatteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // All three are required — battery included: without the exemption Android
    // refuses background service starts and enforcement silently lapses.
    val allGranted = hasUsageAccess && hasOverlay && hasBatteryUnrestricted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color(0xFF003A57) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(start = 40.dp, end = 40.dp, top = 160.dp, bottom = 40.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Some permissions, please...",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = BrandFont,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Permission 1: Usage Access
        PermissionCard(
            title = "App Usage Access",
            description = "Lets PIM know when you open a restricted app",
            granted = hasUsageAccess,
            onGrant = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Permission 2: Overlay
        PermissionCard(
            title = "Display Over Other Apps",
            description = "Lets PIM display on top of other apps",
            granted = hasOverlay,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Permission 3: Battery exemption (required, like the other two)
        PermissionCard(
            title = "Battery — Unrestricted",
            description = "Lets PIM keep running in the background so restrictions stay active.",
            granted = hasBatteryUnrestricted,
            onGrant = {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            enabled = allGranted,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (allGranted) "Continue" else "Grant permissions to continue")
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) Color(0xFF0A4A6A) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (granted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Button(onClick = onGrant) {
                    Text("Grant")
                }
            }
        }
    }
}
