package com.pleaseinconvenienceme.pim

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

class TroubleshootingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val expandFaq = intent.getIntExtra("expand_faq", -1)
        setContent {
            PimTheme {
                TroubleshootingScreen(onBack = { finish() }, initialExpanded = expandFaq)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootingScreen(onBack: () -> Unit, initialExpanded: Int = -1) {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER.lowercase()
    val isAggressiveOEM = manufacturer in listOf(
        "samsung", "xiaomi", "redmi", "poco", "huawei", "honor",
        "oppo", "realme", "oneplus", "vivo"
    )

    var expandedIndex by remember { mutableStateOf(initialExpanded) }
    val anyExpanded = expandedIndex != -1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding -> 
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {

            // FAQ 1: Tips for using PIM effectively
            FaqItem(
                question = "What are the best tips for using PIM?",
                expanded = expandedIndex == 0,
                anyExpanded = anyExpanded,
                onToggle = { expandedIndex = if (expandedIndex == 0) -1 else 0 }
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("Here's some of the features that'll help you get the most out of PIM:\n\n")

                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Customize:") }
                        append(" Just tap on the restricted app in the main screen, and then tap Custom. There's options for everyone in there. Can't stand Math tasks? Switch to a Typing task.\n\n")

                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Difficulty:") }
                        append(" Play around with the difficulty, with Easy/Medium/Hard/Custom. Make it hard enough to be inconvenient, but not so hard that you uninstall PIM. Use the \"Try it\" button on the top right to test it out.\n\n")

                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Task Delay:") }
                        append(" In the Task Delay section, you can force a wait before the task even shows up. Use \"Per additional session\" to add more delay for every extra session.\n\n")

                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Lock:") }
                        append(" When PIM is locked, you can't change settings in a moment of temptation. To lock, go to the main screen and tap Options on the bottom, then tap the padlock at the top.\n\nIt sets up a password that you need to enter to change settings. Keep the password safe, but not accessible.\n\n")

                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Timer:") }
                        append(" Set up the floating timer, at the bottom of Options. It's useful for knowing when to wrap things up.")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // FAQ 2: PIM isn't blocking my apps
            FaqItem(
                question = "PIM isn't blocking my apps",
                expanded = expandedIndex == 1,
                anyExpanded = anyExpanded,
                onToggle = { expandedIndex = if (expandedIndex == 1) -1 else 1 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Some phones close background apps to save battery. This stops PIM from working.\nMake sure battery optimization is set to Unrestricted for PIM.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Open battery settings")
                    }

                    if (isAggressiveOEM) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Extra steps for your phone",
                            style = MaterialTheme.typography.titleSmall
                        )
                        val oemInstructions = when {
                            manufacturer == "samsung" -> 
                                "Samsung is known for closing apps to save battery. This is not a PIM issue — it affects many background apps.\n\n" +
                                "Go to Settings > Battery > More battery settings > Put unused apps to sleep, and make sure PIM is excluded.\n\n" +
                                "Also go to Settings > Apps > PIM > Battery > Unrestricted."
                            manufacturer in listOf("xiaomi", "redmi", "poco") -> 
                                "Xiaomi/MIUI is known for closing apps to save battery. This is not a PIM issue — it affects many background apps.\n\n" +
                                "Go to Settings > Apps > Manage apps > PIM > Battery saver > No restrictions.\n\n" +
                                "Also enable Autostart for PIM: Settings > Apps > Manage apps > PIM > Autostart."
                            manufacturer in listOf("huawei", "honor") -> 
                                "Huawei is known for closing apps to save battery. This is not a PIM issue — it affects many background apps.\n\n" +
                                "Go to Settings > Battery > App launch > PIM > Manage manually, then enable all three toggles."
                            manufacturer in listOf("oppo", "realme") -> 
                                "OPPO/Realme is known for closing apps to save battery. This is not a PIM issue — it affects many background apps.\n\n" +
                                "Go to Settings > Battery > More settings > Optimize battery usage > find PIM > Don't optimize."
                            manufacturer == "oneplus" -> 
                                "OnePlus is known for closing apps to save battery. This is not a PIM issue — it affects many background apps.\n\n" +
                                "Go to Settings > Battery > Battery optimization > All apps > PIM > Don't optimize."
                            manufacturer == "vivo" -> 
                                "Vivo is known for closing apps to save battery. This is not a PIM issue — it affects many background apps.\n\n" +
                                "Go to Settings > Battery > High background power consumption > add PIM to the list."
                            else ->  ""
                        }
                        Text(
                            text = oemInstructions,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Menu names may change depending on version.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (manufacturer in listOf("xiaomi", "redmi", "poco")) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent().setClassName(
                                            "com.miui.securitycenter",
                                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        context.startActivity(Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}")
                                        ))
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Open Autostart settings")
                            }
                        }
                        Text(
                            text = "You can also lock PIM in your Recent Apps screen — long-press PIM's card and tap the lock icon (or \"Keep open\"). This prevents your phone from closing it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Keep permissions",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Your system may remove permissions if you haven't opened PIM in a while. Because PIM runs in the background, it's rarely opened.\nDisable \"Pause app activity if unused\" to prevent this.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    context.startActivity(Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ))
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Open permission settings")
                        }
                    }

                    Text(
                        text = "Still having issues?",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Some phone makers are very aggressive about closing background apps to claim longer battery life. This can stop PIM from running.\n\nDontKillMyApp.com has step-by-step instructions for every brand.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com")))
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Visit dontkillmyapp.com")
                    }
                }
            }

            HorizontalDivider()

            // FAQ 3: Brief glimpse of restricted app
            FaqItem(
                question = "I see a few seconds of my restricted app before PIM comes up",
                expanded = expandedIndex == 2,
                anyExpanded = anyExpanded,
                onToggle = { expandedIndex = if (expandedIndex == 2) -1 else 2 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "That's a deliberate tradeoff. Most app blockers use the Accessibility permission, which lets them see every screen and every tap. It's powerful, and it's been abused by malware. PIM doesn't ask for it.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Instead, PIM regularly checks which app is in the foreground. It's quick but not instant, so you may see a little of the app before PIM pops up. We think that's a fair trade for your privacy.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // FAQ 4: What's included for free? (Google Play only)
            if (BuildConfig.ENFORCE_LIMIT) {
                FaqItem(
                    question = "What does the free trial include?",
                    expanded = expandedIndex == 3,
                    anyExpanded = anyExpanded,
                    onToggle = { expandedIndex = if (expandedIndex == 3) -1 else 3 }
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append("For 7 days, PIM is completely unlimited. Restrict all the apps you want, and customize everything.\n\nA one-time purchase allows unlimited apps, ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("forever")
                            }
                            append(".\n\nNo subscription, no ads, no tracking.")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }

            // FAQ 5: YouTube still plays
            FaqItem(
                question = "YouTube and Instagram still play audio when PIM is up",
                expanded = expandedIndex == 4,
                anyExpanded = anyExpanded,
                onToggle = { expandedIndex = if (expandedIndex == 4) -1 else 4 }
            ) {
                Text(
                    text = "YouTube, TikTok, and some other apps use Picture-in-Picture to keep playing.\n\nTo turn it off, go to Settings > Apps > YouTube (or TikTok) > Picture-in-picture, and turn off.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // FAQ 6: Does PIM work in a browser?
            FaqItem(
                question = "Can PIM restrict websites in a browser?",
                expanded = expandedIndex == 5,
                anyExpanded = anyExpanded,
                onToggle = { expandedIndex = if (expandedIndex == 5) -1 else 5 }
            ) {
                Text(
                    text = "No. PIM checks which app is open, not which website.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // FAQ 7: Locking
            FaqItem(
                question = "Can I lock my settings?",
                expanded = expandedIndex == 6,
                anyExpanded = anyExpanded,
                onToggle = { expandedIndex = if (expandedIndex == 6) -1 else 6 }
            ) {
                Text(
                    text = "Yes. Tap Options at the bottom of the main screen. Then tap the lock icon at the top. You'll be prompted to write down a password. After locking, you'll need that password to change anything.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // FAQ 8: I forgot my PIM password
            FaqItem(
                question = "I forgot my PIM password",
                expanded = expandedIndex == 7,
                anyExpanded = anyExpanded,
                onToggle = { expandedIndex = if (expandedIndex == 7) -1 else 7 }
            ) {
                Text(
                    text = "To reset your password, go to Settings > Apps > PIM > Storage > Clear storage.\nOr, you can uninstall and reinstall PIM.\n\nEither way, you'll have to redo your settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // FAQ 9: Does PIM work on iOS?
            FaqItem(
                question = "Does PIM work on iOS?",
                expanded = expandedIndex == 8,
                anyExpanded = anyExpanded,
                onToggle = { expandedIndex = if (expandedIndex == 8) -1 else 8 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Not yet. Send an email if you want to know when it's ready.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Email us",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:hello@pleaseinconvenienceme.com?subject=PIM%20iOS%20Interest")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun FaqItem(
    question: String,
    expanded: Boolean,
    anyExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron"
    )
    val itemAlpha by animateFloatAsState(
        targetValue = if (anyExpanded && !expanded) 0.35f else 1f,
        label = "faqAlpha"
    )

    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(itemAlpha)
            .border(
                width = 1.dp,
                color = if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                shape = shape
            ),
        shape = shape,
        tonalElevation = if (expanded) 3.dp else 0.dp,
        color = if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}
