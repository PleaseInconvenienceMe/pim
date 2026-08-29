package com.pleaseinconvenienceme.pim

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import java.io.File
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PimTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs = getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
                    var setupComplete by remember {
                        mutableStateOf(allPermissionsGranted() && prefs.getBoolean(PrefsKeys.SETUP_COMPLETE, false))
                    }

                    if (setupComplete) {
                        AppListScreen()
                    } else {
                        SetupScreen(onSetupComplete = {
                            prefs.edit().putBoolean(PrefsKeys.SETUP_COMPLETE, true).apply()
                            setupComplete = true
                        })
                    }
                }
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        val hasUsage = hasUsageStatsPermission(this)
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            android.provider.Settings.canDrawOverlays(this) else true
        return hasUsage && hasOverlay
    }
}

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Bitmap
)

/**
 * Loads an app icon from disk cache if available, otherwise fetches from PackageManager and caches it.
 * Cache key includes lastUpdateTime so icons are automatically refreshed after app updates.
 * Cache files live in cacheDir and can be cleared by the system if storage is low.
 */
fun loadCachedIcon(context: Context, pm: android.content.pm.PackageManager, pkgName: String): Bitmap? {
    return try {
        val lastUpdate = pm.getPackageInfo(pkgName, 0).lastUpdateTime
        val cacheDir = File(context.cacheDir, "app_icons")
        val cacheFile = File(cacheDir, "${pkgName.hashCode()}_$lastUpdate.png")

        if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)
        } else {
            cacheDir.mkdirs()
            // Delete stale cache files for this package (from previous app versions)
            val pkgHash = pkgName.hashCode().toString()
            cacheDir.listFiles { f -> f.name.startsWith("${pkgHash}_") && f != cacheFile }
                ?.forEach { it.delete() }
            val appInfo = pm.getApplicationInfo(pkgName, 0)
            val bitmap = pm.getApplicationIcon(appInfo).toBitmap(72, 72)
            try {
                cacheFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (_: Exception) {} // cache write failure is non-fatal
            bitmap
        }
    } catch (_: Exception) { null }
}

// Colors passed from Compose's MaterialTheme into the View-based RecyclerView adapter
// so the list respects dark mode without hardcoded color values.
data class AdapterColors(
    val hintText: Int = 0xFF888888.toInt(),
    val headerRestricted: Int = 0xFF8B5E52.toInt(),
    val headerNormal: Int = 0xFF444444.toInt(),
    val cardRestrictedBg: Int = 0xFFF5E8E6.toInt(),
    val cardRestrictedStroke: Int = 0x308B5E52.toInt(),
    val cardPausedBg: Int = 0xFFEEEEEE.toInt(),
    val cardPausedStroke: Int = 0x40000000.toInt(),
    val cardNormalBg: Int = 0xFFF5F5F5.toInt(),
    val cardNormalStroke: Int = 0x20000000.toInt(),
    val pulseDot: Int = 0xFF888888.toInt()
)

// RecyclerView adapter for smooth scrolling
sealed class ListItem {
    data class Header(val title: String, val isRestricted: Boolean) : ListItem()
    data class AppRow(
        val app: AppInfo,
        val isChecked: Boolean,
        val isRestricted: Boolean,
        val durationMinutes: Int = 0,
        val isTempUnrestricted: Boolean = false,
        val tempMinutesLeft: Int = 0,
        val hasCustomOverrides: Boolean = false,
        val isLocked: Boolean = false
    ) : ListItem()
    data class EmptyHint(val message: String) : ListItem()
}

class AppListAdapter(
    private var items: List<ListItem> = emptyList(),
    private val onCheckedChange: (String, Boolean) -> Unit,
    private val onDurationClick: (String) -> Unit = {},
    private val onPausedAppClick: (String) -> Unit = {},
    private var colors: AdapterColors = AdapterColors()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun updateColors(newColors: AdapterColors) {
        if (colors == newColors) return
        colors = newColors
        notifyDataSetChanged()
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_APP = 1
        const val TYPE_HINT = 2
    }

    class HeaderViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    class HintViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewWithTag("checkbox")
        val icon: ImageView = view.findViewWithTag("icon")
        val name: TextView = view.findViewWithTag("name")
        val pulseDot: View = view.findViewWithTag("pulse_dot")
        val container: View = view
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ListItem.Header -> TYPE_HEADER
        is ListItem.AppRow -> TYPE_APP
        is ListItem.EmptyHint -> TYPE_HINT
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density

        return when (viewType) {
            TYPE_HINT -> {
                val tv = TextView(ctx).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(dp(16, density), dp(8, density), dp(16, density), dp(16, density))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(colors.hintText)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                HintViewHolder(tv)
            }
            TYPE_HEADER -> {
                val tv = TextView(ctx).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(dp(16, density), dp(20, density), dp(16, density), dp(8, density))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                HeaderViewHolder(tv)
            }
            else -> {
                // Outer wrapper just adds vertical spacing between items
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        dp(68, density)
                    )
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(6, density), dp(3, density), dp(6, density), dp(3, density))
                }

                // Inner card-like container with visible background
                val inner = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12, density), 0, dp(4, density), 0)
                    elevation = 2 * density
                    tag = "inner"
                }

                val iv = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(42, density), dp(42, density))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    tag = "icon"
                }
                inner.addView(iv)

                val spacer2 = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(14, density), 0)
                }
                inner.addView(spacer2)

                val tv = TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    tag = "name"
                }
                inner.addView(tv)

                val currentColors = colors // capture before apply — GradientDrawable has its own .colors property
                val dot = View(ctx).apply {
                    val size = dp(8, density)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginEnd = dp(8, density)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(currentColors.pulseDot)
                    }
                    tag = "pulse_dot"
                    visibility = View.GONE
                }
                inner.addView(dot)

                val cb = CheckBox(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48, density), dp(48, density))
                    tag = "checkbox"
                }
                inner.addView(cb)

                row.addView(inner)
                AppViewHolder(row)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.EmptyHint -> {
                (holder as HintViewHolder).textView.text = item.message
            }
            is ListItem.Header -> {
                val h = holder as HeaderViewHolder
                h.textView.text = item.title
                h.textView.setTextColor(
                    if (item.isRestricted) colors.headerRestricted else colors.headerNormal
                )
            }
            is ListItem.AppRow -> {
                val h = holder as AppViewHolder
                when {
                    item.isTempUnrestricted ->
                        h.name.text = "${item.app.name} (paused • ${item.tempMinutesLeft} min)"
                    item.isRestricted && item.durationMinutes > 0 && item.isLocked -> {
                        val text = if (item.hasCustomOverrides)
                            "${item.app.name} (${item.durationMinutes} min • custom •  )"
                        else
                            "${item.app.name} (${item.durationMinutes} min •  )"
                        val spannable = android.text.SpannableString(text)
                        val lockDrawable = androidx.core.content.ContextCompat.getDrawable(h.container.context, R.drawable.ic_lock_small)
                        if (lockDrawable != null) {
                            val size = (h.name.textSize * 0.85f).toInt()
                            lockDrawable.setBounds(0, 0, size, size)
                            val imageSpan = android.text.style.ImageSpan(lockDrawable, android.text.style.ImageSpan.ALIGN_BASELINE)
                            val insertPos = text.indexOf("  ") + 1
                            spannable.setSpan(imageSpan, insertPos, insertPos + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        h.name.text = spannable
                    }
                    item.isRestricted && item.durationMinutes > 0 && item.hasCustomOverrides ->
                        h.name.text = "${item.app.name} (${item.durationMinutes} min • custom)"
                    item.isRestricted && item.durationMinutes > 0 ->
                        h.name.text = "${item.app.name} (${item.durationMinutes} min)"
                    else ->
                        h.name.text = item.app.name
                }
                h.icon.setImageBitmap(item.app.icon)

                // Set card-like background on the inner layout
                val inner = (h.container as ViewGroup).findViewWithTag<View>("inner")
                val density = h.container.context.resources.displayMetrics.density
                val c = colors // capture before apply — GradientDrawable has its own .colors property
                val bg = GradientDrawable().apply {
                    cornerRadius = 14 * density
                    when {
                        item.isTempUnrestricted -> {
                            setColor(c.cardPausedBg)
                            setStroke((1 * density).toInt(), c.cardPausedStroke)
                        }
                        item.isRestricted -> {
                            setColor(c.cardRestrictedBg)
                            setStroke((4 * density).toInt(), c.cardRestrictedStroke)
                        }
                        else -> {
                            setColor(c.cardNormalBg)
                            setStroke((1 * density).toInt(), c.cardNormalStroke)
                        }
                    }
                }
                inner.background = bg
                inner.clipToOutline = true
                inner.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

                // Pulsing dot for paused apps
                (h.pulseDot.tag as? ObjectAnimator)?.cancel()
                if (item.isTempUnrestricted) {
                    h.pulseDot.visibility = View.VISIBLE
                    val anim = ObjectAnimator.ofFloat(h.pulseDot, "alpha", 1f, 0.15f).apply {
                        duration = 1500
                        repeatMode = ValueAnimator.REVERSE
                        repeatCount = ValueAnimator.INFINITE
                        start()
                    }
                    h.pulseDot.tag = anim
                } else {
                    h.pulseDot.visibility = View.GONE
                    h.pulseDot.alpha = 1f
                    h.pulseDot.tag = null
                }

                // Prevent listener from firing during bind
                h.checkbox.setOnCheckedChangeListener(null)
                h.checkbox.isChecked = item.isChecked
                val guardedListener = object : android.widget.CompoundButton.OnCheckedChangeListener {
                    override fun onCheckedChanged(button: android.widget.CompoundButton, isChecked: Boolean) {
                        if (!isChecked && item.isRestricted && !item.isTempUnrestricted) {
                            // Re-check immediately so the checkbox never visually unchecks,
                            // reinstalling this same guarded listener afterwards.
                            h.checkbox.setOnCheckedChangeListener(null)
                            h.checkbox.isChecked = true
                            h.checkbox.setOnCheckedChangeListener(this)
                            onCheckedChange(item.app.name, false)
                            return
                        }
                        onCheckedChange(item.app.name, isChecked)
                    }
                }
                h.checkbox.setOnCheckedChangeListener(guardedListener)

                // Tap anywhere on the row to toggle/edit
                inner.setOnClickListener {
                    when {
                        item.isTempUnrestricted -> onPausedAppClick(item.app.name)
                        item.isRestricted -> onDurationClick(item.app.name)
                        else -> h.checkbox.isChecked = true
                    }
                }

                // Override checkbox uncheck for paused apps
                if (item.isTempUnrestricted) {
                    h.checkbox.setOnCheckedChangeListener { _, isChecked ->
                        if (!isChecked) {
                            h.checkbox.isChecked = true // keep it checked visually
                            onPausedAppClick(item.app.name)
                        }
                    }
                }
            }
        }
    }

    fun updateItems(newItems: List<ListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}

@Composable
fun AppListScreen() {
    val context = LocalContext.current
    val packageManager = context.packageManager

    // Get all installed apps asynchronously to avoid blocking UI
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Track which apps are selected
    var selectedApps by remember { mutableStateOf(setOf<String>()) }

    // Per-app durations
    var appDurations by remember { mutableStateOf(mapOf<String, Int>()) }

    // Temporarily unrestricted apps: app name -> expiry timestamp
    var tempUnrestrict by remember { mutableStateOf(mapOf<String, Long>()) }

    // Duration picker dialog state
    var showDurationPicker by remember { mutableStateOf(false) }
    var pickerAppName by remember { mutableStateOf("") }
    var pickerPackageName by remember { mutableStateOf("") }
    var pickerInitialMinutes by remember { mutableStateOf(5) }
    var pickerIsNewRestriction by remember { mutableStateOf(false) }
    var pickerHasCustomOverrides by remember { mutableStateOf(false) }
    var pickerShowRevertMessage by remember { mutableStateOf(false) }
    var showLockedPickerPasswordPrompt by remember { mutableStateOf(false) }

    // Tracks when custom overrides change, to force list re-render
    var customOverridesVersion by remember { mutableStateOf(0) }

    // Remove confirmation dialog state
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showRemoveOptions by remember { mutableStateOf(false) }
    var removeAppName by remember { mutableStateOf("") }

    // Paused app options dialog state
    var showPausedOptions by remember { mutableStateOf(false) }
    var pausedAppName by remember { mutableStateOf("") }

    // Lock password gate state (for remove/pause of locked apps)
    var showLockedPasswordPrompt by remember { mutableStateOf(false) }
    var lockedPasswordAppName by remember { mutableStateOf("") }

    // Global-lock toggle state (Default options padlock)
    val settingsPrefsForLock = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
    var globalLockEnabled by remember {
        mutableStateOf(settingsPrefsForLock.getBoolean(PrefsKeys.LOCK_ALL_RESTRICTED, PrefsKeys.DEFAULT_LOCK_ALL_RESTRICTED))
    }
    var showGlobalLockUnlockPrompt by remember { mutableStateOf(false) }
    var showGlobalLockLockPrompt by remember { mutableStateOf(false) }
    var showGlobalLockCreatePassword by remember { mutableStateOf(false) }
    var showNewRestrictionConfirm by remember { mutableStateOf(false) }
    var pendingNewRestrictionApp by remember { mutableStateOf("") }
    var pendingNewRestrictionPackage by remember { mutableStateOf("") }

    // Bottom-nav selected tab. Declared up here so appSettingsLauncher can switch tabs.
    var selectedTab by remember { mutableStateOf(0) }
    var lockedPasswordAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Session-based unlock tracking (in-memory, resets when app is killed)
    val sessionUnlockedApps = remember { mutableStateOf(setOf<String>()) }

    // Cancel confirmation dialog state
    var showCancelConfirm by remember { mutableStateOf(false) }

    // Donate nudge state
    var showDonateNudge by remember { mutableStateOf(false) }

    // One-time hint dialogs — initialized synchronously so it shows on the first frame
    var showFirstLaunchHint by remember {
        val settingsPrefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        mutableStateOf(!settingsPrefs.getBoolean(PrefsKeys.FIRST_LAUNCH_HINT_SHOWN, false))
    }

    // Try it preview
    var showTryItPreview by remember { mutableStateOf(false) }

    // Upgrade screen (shown from menu)
    var showUpgradeScreen by remember { mutableStateOf(false) }

    // Billing
    val billingHelper = remember { BillingHelperImpl() }
    val activity = context as ComponentActivity
    LaunchedEffect(Unit) { billingHelper.initialize(activity) }

    // Review prompt
    val reviewHelper = remember { ReviewHelperImpl() }
    var showReviewDialog by remember { mutableStateOf(false) }
    var addInfoLevel by remember { mutableIntStateOf(0) }
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            recordInteraction(context)
            if (shouldPromptReview(context)) showReviewDialog = true
        }
    }
    val isPurchased by billingHelper.isPurchased.collectAsState()
    val price by billingHelper.price.collectAsState()

    // Handle "open_upgrade" intent from task screen
    LaunchedEffect(Unit) {
        val openUpgrade = (context as? ComponentActivity)?.intent?.getBooleanExtra("open_upgrade", false) ?: false
        if (openUpgrade) {
            (context as? ComponentActivity)?.intent?.removeExtra("open_upgrade")
            kotlinx.coroutines.delay(500) // wait for billing client to connect
            billingHelper.launchBillingFlow(activity)
        }
    }

    // Snackbar for app restriction changes
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val centerSnackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when message changes
    val lockTipSuffix = "\n\nTip: Are you tempted to remove restrictions? Learn about locking settings."
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            val hasLockTip = message.endsWith(lockTipSuffix)
            if (hasLockTip) {
                centerSnackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
            } else {
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
            snackbarMessage = null
        }
    }




    if (showReviewDialog) {
        ReviewPromptDialog(
            onSure = {
                showReviewDialog = false
                markReviewNeverAsk(context)
                reviewHelper.launchReview(activity)
            },
            onMaybeLater = {
                showReviewDialog = false
                markReviewMaybeLater(context)
            },
            onNeverAsk = {
                showReviewDialog = false
                markReviewNeverAsk(context)
            },
            onDismiss = {
                showReviewDialog = false
                markReviewMaybeLater(context)
            }
        )
    }

    if (addInfoLevel > 0) {
        val infoTitle = when (addInfoLevel) {
            1 -> "First app restricted."
            2 -> "Second app restricted."
            else -> "Another app on the restricted list."
        }
        val infoBody = when (addInfoLevel) {
            1 -> "Congrats! When you open the app, PIM will give you a task."
            2 -> "Nice. When you open the app, PIM will give you a task."
            else -> "Tap any app to customize."
        }
        val infoTip = if (addInfoLevel == 1) "Tap an app to customize the task." else null
        AlertDialog(
            onDismissRequest = { addInfoLevel = 0 },
            title = { Text(infoTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(infoBody)
                    if (infoTip != null) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Tip: ") }
                                append(infoTip)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { addInfoLevel = 0 },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Got it")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        // Load settings first — must complete before app list loads so restricted apps
        // are known upfront and can be shown immediately (avoiding a race condition).
        val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        val savedApps = (prefs.getStringSet(PrefsKeys.RESTRICTED_APPS, emptySet()) ?: emptySet()).toSet()
        selectedApps = savedApps

        val durationPrefs = context.getSharedPreferences(PrefsKeys.DURATIONS, Context.MODE_PRIVATE)
        appDurations = durationPrefs.all.mapValues { (it.value as? Int) ?: 5 }

        val tempPrefs = context.getSharedPreferences(PrefsKeys.TEMP_UNRESTRICT, Context.MODE_PRIVATE)
        tempUnrestrict = tempPrefs.all.mapValues { (it.value as? Long) ?: 0L }

        if (savedApps.isNotEmpty()) {
            val serviceIntent = Intent(context, AppMonitorService::class.java)
            context.startForegroundService(serviceIntent)
        }



        // Now load app list — settings are guaranteed loaded so restricted apps show first
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null)
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfoList = packageManager.queryIntentActivities(launcherIntent, 0)

            // Collect app metadata serially (fast), then load icons in parallel
            data class AppMeta(val name: String, val pkgName: String)
            val seen = mutableSetOf<String>()
            val appMetas = mutableListOf<AppMeta>()
            for (resolveInfo in resolveInfoList) {
                try {
                    val pkgName = resolveInfo.activityInfo.packageName
                    val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    if (!seen.add(appName)) continue
                    if (isPWA(resolveInfo)) continue
                    appMetas.add(AppMeta(appName, pkgName))
                } catch (_: Exception) {}
            }

            val allApps = kotlinx.coroutines.coroutineScope {
                appMetas.map { meta ->
                    async {
                        val icon = loadCachedIcon(context, packageManager, meta.pkgName) ?: return@async null
                        AppInfo(meta.name, meta.pkgName, icon)
                    }
                }.awaitAll().filterNotNull()
            }

            val restrictedFirst = allApps.filter { savedApps.contains(it.name) }.sortedBy { it.name }
            val remaining = allApps.filter { !savedApps.contains(it.name) }.sortedBy { it.name }

            // Show restricted apps immediately
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                apps = restrictedFirst
                isLoading = false
            }

            // Then add the rest
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                apps = restrictedFirst + remaining
            }
        }
    }

    // Tick every minute to update countdown displays
    var tempTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            tempTick++
        }
    }

    // Build flat list for RecyclerView
    val listItems by remember {
        derivedStateOf {
            val result = mutableListOf<ListItem>()
            val now = System.currentTimeMillis()
            @Suppress("UNUSED_EXPRESSION") tempTick // force recompute every minute
            @Suppress("UNUSED_EXPRESSION") customOverridesVersion // force recompute on override changes
            val restricted = apps.filter { selectedApps.contains(it.name) }.sortedBy { it.name }
            val unrestricted = apps.filter { !selectedApps.contains(it.name) }.sortedBy { it.name }

            result.add(ListItem.Header("Apps With Restrictions (${restricted.size})", true))
            if (restricted.isEmpty()) {
                result.add(ListItem.EmptyHint("Tap an app below to add restriction"))
            } else {
                restricted.forEach { app ->
                    val expiry = tempUnrestrict[app.name] ?: 0L
                    val isTemp = expiry > now
                    val minutesLeft = if (isTemp) ((expiry - now) / 60_000).toInt().coerceAtLeast(1) else 0
                    val resolver = AppSettingsResolver(context, app.name)
                    val hasCustom = resolver.hasAnyOverrides()
                    val locked = resolver.isLocked()
                    result.add(ListItem.AppRow(app, true, true, appDurations[app.name] ?: 5, isTemp, minutesLeft, hasCustom, locked))
                }
            }

            result.add(ListItem.Header("Other Apps (${unrestricted.size})", false))
            unrestricted.forEach { app ->
                result.add(ListItem.AppRow(app, false, false))
            }

            result.toList()
        }
    }

    // Keep a reference to the adapter
    val adapter = remember {
        AppListAdapter(
            onCheckedChange = { appName, isChecked ->
                if (isChecked) {
                    val pkg = apps.find { it.name == appName }?.packageName ?: ""
                    if (globalLockEnabled) {
                        // Warn before adding a new restriction under the global lock
                        pendingNewRestrictionApp = appName
                        pendingNewRestrictionPackage = pkg
                        showNewRestrictionConfirm = true
                    } else {
                        // Show duration picker for new restriction
                        pickerAppName = appName
                        pickerPackageName = pkg
                        pickerInitialMinutes = PrefsKeys.DEFAULT_SESSION_MINUTES
                        pickerIsNewRestriction = true
                        pickerHasCustomOverrides = false // new app never has overrides
                        pickerShowRevertMessage = false
                        showDurationPicker = true
                    }
                } else {
                    // Gate locked apps with password prompt
                    val resolver = AppSettingsResolver(context, appName)
                    if (resolver.isLocked() && appName !in sessionUnlockedApps.value) {
                        lockedPasswordAppName = appName
                        lockedPasswordAction = {
                            removeAppName = appName
                            showRemoveConfirm = true
                        }
                        showLockedPasswordPrompt = true
                    } else {
                        removeAppName = appName
                        showRemoveConfirm = true
                    }
                }
            },
            onDurationClick = { appName ->
                // Show picker to change existing duration
                pickerAppName = appName
                pickerPackageName = apps.find { it.name == appName }?.packageName ?: ""
                pickerInitialMinutes = appDurations[appName] ?: 5
                pickerIsNewRestriction = false
                val resolver = AppSettingsResolver(context, appName)
                pickerHasCustomOverrides = resolver.hasAnyOverrides()
                pickerShowRevertMessage = false
                if (resolver.isLocked()) {
                    showLockedPickerPasswordPrompt = true
                } else {
                    showDurationPicker = true
                }
            },
            onPausedAppClick = { appName ->
                val resolver = AppSettingsResolver(context, appName)
                if (resolver.isLocked() && appName !in sessionUnlockedApps.value) {
                    lockedPasswordAppName = appName
                    lockedPasswordAction = {
                        pausedAppName = appName
                        showPausedOptions = true
                    }
                    showLockedPasswordPrompt = true
                } else {
                    pausedAppName = appName
                    showPausedOptions = true
                }
            }
        )
    }

    // Pass theme colors into the View-based adapter so it respects dark mode
    val colorScheme = MaterialTheme.colorScheme
    val adapterColors = AdapterColors(
        hintText = colorScheme.onSurfaceVariant.toArgb(),
        headerRestricted = if (isSystemInDarkTheme()) 0xFFC27B6E.toInt() else colorScheme.error.toArgb(),
        headerNormal = colorScheme.onSurfaceVariant.toArgb(),
        cardRestrictedBg = if (isSystemInDarkTheme()) 0xFF5A3333.toInt() else 0xFFF5E8E6.toInt(),
        cardRestrictedStroke = if (isSystemInDarkTheme()) colorScheme.error.copy(alpha = 0.3f).toArgb() else 0x308B5E52.toInt(),
        cardPausedBg = if (isSystemInDarkTheme()) colorScheme.surfaceVariant.toArgb() else 0xFFEEEEEE.toInt(),
        cardPausedStroke = if (isSystemInDarkTheme()) colorScheme.outline.copy(alpha = 0.4f).toArgb() else 0x40000000.toInt(),
        cardNormalBg = if (isSystemInDarkTheme()) colorScheme.surfaceVariant.toArgb() else 0xFFF5F5F5.toInt(),
        cardNormalStroke = if (isSystemInDarkTheme()) colorScheme.outline.copy(alpha = 0.4f).toArgb() else 0x20000000.toInt(),
        pulseDot = colorScheme.primary.toArgb()
    )
    LaunchedEffect(adapterColors) {
        adapter.updateColors(adapterColors)
    }

    // Update adapter when list changes
    LaunchedEffect(listItems) {
        adapter.updateItems(listItems)
    }

    // Key to force DurationPickerDialog to recreate with fresh state
    var pickerKey by remember { mutableStateOf(0) }

    // Snapshot of per-app overrides taken when the picker opens (for cancel/restore)
    var pickerSnapshot by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }

    // Launches AppSettingsActivity; on return, re-show picker so user can OK or Cancel
    val appSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val openOptions = result.data?.getBooleanExtra("OPEN_OPTIONS_TAB", false) ?: false
        if (openOptions) {
            // User chose to open Default options from per-app screen. Close the picker
            // and switch to the Options tab instead of re-showing it.
            showDurationPicker = false
            selectedTab = 1
            return@rememberLauncherForActivityResult
        }
        customOverridesVersion++
        val resolver = AppSettingsResolver(context, pickerAppName)
        if (!resolver.isLocked() && resolver.hasAnyOverrides() && resolver.matchesGlobals()) {
            resolver.clearAllOverrides()
            pickerHasCustomOverrides = false
        } else {
            pickerHasCustomOverrides = resolver.hasAnyOverrides()
        }
        pickerShowRevertMessage = false
        pickerKey++
    }

    // Standalone password prompt for locked apps (before showing duration picker)
    if (showLockedPickerPasswordPrompt) {
        PasswordPromptDialog(
            onUnlock = { password ->
                val resolver = AppSettingsResolver(context, pickerAppName)
                if (resolver.checkPassword(password)) {
                    sessionUnlockedApps.value = sessionUnlockedApps.value + pickerAppName
                    showLockedPickerPasswordPrompt = false
                    pickerShowRevertMessage = false
                    showDurationPicker = true
                    true
                } else {
                    false
                }
            },
            onDismiss = { showLockedPickerPasswordPrompt = false }
        )
    }

    // Global lock: turn-off password prompt
    if (showGlobalLockUnlockPrompt) {
        PasswordPromptDialog(
            title = "Remove lock?",
            body = "Enter PIM password to confirm.",
            onUnlock = { password ->
                val resolver = AppSettingsResolver(context, "")
                if (resolver.checkPassword(password)) {
                    settingsPrefsForLock.edit()
                        .putBoolean(PrefsKeys.LOCK_ALL_RESTRICTED, false)
                        .remove(PrefsKeys.GLOBAL_LOCK_HASH)
                        .apply()
                    globalLockEnabled = false
                    showGlobalLockUnlockPrompt = false
                    customOverridesVersion++
                    true
                } else {
                    false
                }
            },
            onDismiss = { showGlobalLockUnlockPrompt = false }
        )
    }

    // Global lock: turn-on password prompt (when password already exists)
    if (showGlobalLockLockPrompt) {
        PasswordPromptDialog(
            title = "Enter PIM password to lock",
            onUnlock = { password ->
                val resolver = AppSettingsResolver(context, "")
                if (resolver.checkPassword(password)) {
                    settingsPrefsForLock.edit().putBoolean(PrefsKeys.LOCK_ALL_RESTRICTED, true).apply()
                    globalLockEnabled = true
                    showGlobalLockLockPrompt = false
                    customOverridesVersion++
                    true
                } else {
                    false
                }
            },
            onDismiss = { showGlobalLockLockPrompt = false }
        )
    }

    // Global lock: create-password dialog (first time enabling)
    if (showGlobalLockCreatePassword) {
        CreatePasswordDialog(
            appName = "PIM",
            onConfirm = { password ->
                val resolver = AppSettingsResolver(context, "")
                resolver.setGlobalPassword(password)
                settingsPrefsForLock.edit().putBoolean(PrefsKeys.LOCK_ALL_RESTRICTED, true).apply()
                globalLockEnabled = true
                showGlobalLockCreatePassword = false
                customOverridesVersion++
            },
            onDismiss = { showGlobalLockCreatePassword = false }
        )
    }

    // Warn before adding a new restriction while the global lock is on
    if (showNewRestrictionConfirm) {
        AlertDialog(
            onDismissRequest = { showNewRestrictionConfirm = false },
            title = { Text("Add restriction?") },
            text = { Text("After setup, you'll need the PIM password to change these settings.") },
            confirmButton = {
                Button(onClick = {
                    pickerAppName = pendingNewRestrictionApp
                    pickerPackageName = pendingNewRestrictionPackage
                    pickerInitialMinutes = PrefsKeys.DEFAULT_SESSION_MINUTES
                    pickerIsNewRestriction = true
                    pickerHasCustomOverrides = false
                    pickerShowRevertMessage = false
                    showNewRestrictionConfirm = false
                    showDurationPicker = true
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showNewRestrictionConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Duration picker dialog (key forces fresh state after returning from AppSettings)
    if (showDurationPicker) {
        // Take snapshot when picker first opens
        LaunchedEffect(pickerAppName) {
            val resolver = AppSettingsResolver(context, pickerAppName)
            pickerSnapshot = resolver.saveSnapshot()
        }
        key(pickerKey) {
        DurationPickerDialog(
            appName = pickerAppName,
            initialMinutes = pickerInitialMinutes,
            hasCustomOverrides = pickerHasCustomOverrides,
            showRevertMessage = pickerShowRevertMessage,
            onOverridesCleared = {
                customOverridesVersion++
            },
            onOpenCustomSettings = { currentMinutes ->
                pickerInitialMinutes = currentMinutes
                val resolver = AppSettingsResolver(context, pickerAppName)
                if (!resolver.hasAnyOverrides()) resolver.snapshotGlobals()
                // For a newly-added restriction under global lock, auto-unlock the session.
                // The user is actively setting up a restriction; they shouldn't need the
                // password to immediately customize the app they just added.
                if (pickerIsNewRestriction && pickerAppName !in sessionUnlockedApps.value) {
                    sessionUnlockedApps.value = sessionUnlockedApps.value + pickerAppName
                }
                appSettingsLauncher.launch(
                    Intent(context, AppSettingsActivity::class.java)
                        .putExtra("APP_NAME", pickerAppName)
                        .putExtra("PACKAGE_NAME", pickerPackageName)
                        .putExtra("SESSION_UNLOCKED", pickerAppName in sessionUnlockedApps.value)
                )
            },
            onConfirm = { minutes, isCustomMode ->
                // Save duration
                val durationPrefs = context.getSharedPreferences(PrefsKeys.DURATIONS, Context.MODE_PRIVATE)
                durationPrefs.edit().putInt(pickerAppName, minutes).apply()
                appDurations = appDurations + (pickerAppName to minutes)

                // Leave a running session alone: it keeps the length it was granted with, and the
                // new duration applies from the next session on. Clearing it here meant that
                // stepping into PIM mid-session and confirming the picker sent the user back to
                // a fresh task even though their session still had time on it.
                // With nothing running, we still clear, so a just-expired session resets to a
                // cold open rather than counting as a back-to-back continuation.
                val sessionPrefs = context.getSharedPreferences(PrefsKeys.SESSIONS, Context.MODE_PRIVATE)
                if (sessionPrefs.getLong(pickerAppName, 0L) <= System.currentTimeMillis()) {
                    sessionPrefs.edit().remove(pickerAppName).apply()
                }

                if (pickerIsNewRestriction) {
                    if (BuildConfig.SHOW_DONATE_PROMPT && selectedApps.size + 1 == 4) {
                        showDonateNudge = true
                    }
                    val newSelectedApps = selectedApps + pickerAppName
                    selectedApps = newSelectedApps
                    val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
                    prefs.edit().putStringSet(PrefsKeys.RESTRICTED_APPS, newSelectedApps).apply()
                    context.startForegroundService(Intent(context, AppMonitorService::class.java))
                }

                // Auto-revert to default if custom overrides match globals and not locked
                if (isCustomMode) {
                    val resolver = AppSettingsResolver(context, pickerAppName)
                    if (!resolver.isLocked() && resolver.hasAnyOverrides() && resolver.matchesGlobals()) {
                        resolver.clearAllOverrides()
                    }
                }

                showDurationPicker = false
                sessionUnlockedApps.value = sessionUnlockedApps.value - pickerAppName
                customOverridesVersion++
                if (pickerIsNewRestriction) {
                    val countPrefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
                    val count = countPrefs.getInt(PrefsKeys.RESTRICTION_COUNT, 0)
                    countPrefs.edit().putInt(PrefsKeys.RESTRICTION_COUNT, count + 1).apply()
                    if (count < 3) {
                        addInfoLevel = count + 1
                    } else {
                        snackbarMessage = "$pickerAppName restricted ($minutes min)"
                    }
                    recordInteraction(context)
                    if (shouldPromptReview(context)) showReviewDialog = true
                } else {
                    val resolver = AppSettingsResolver(context, pickerAppName)
                    if (resolver.saveSnapshot() != pickerSnapshot) {
                        snackbarMessage = "$pickerAppName settings updated"
                    }
                }
            },
            onCancel = {
                val resolver = AppSettingsResolver(context, pickerAppName)
                val currentState = resolver.saveSnapshot()
                val hasChanges = currentState != pickerSnapshot
                if (hasChanges) {
                    showCancelConfirm = true
                } else {
                    sessionUnlockedApps.value = sessionUnlockedApps.value - pickerAppName
                    showDurationPicker = false
                    if (pickerIsNewRestriction) {
                        adapter.updateItems(listItems)
                    }
                }
            },
            onDismiss = {
                showDurationPicker = false
                sessionUnlockedApps.value = sessionUnlockedApps.value - pickerAppName
                if (pickerIsNewRestriction) {
                    adapter.updateItems(listItems)
                }
            }
        )
        }
    }

    // Cancel confirmation dialog
    @OptIn(ExperimentalMaterial3Api::class)
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false }
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Discard changes?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Your changes to $pickerAppName settings will be lost.")
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showCancelConfirm = false
                            val resolver = AppSettingsResolver(context, pickerAppName)
                            resolver.restoreSnapshot(pickerSnapshot)
                            customOverridesVersion++
                            showDurationPicker = false
                            sessionUnlockedApps.value = sessionUnlockedApps.value - pickerAppName
                            if (pickerIsNewRestriction) {
                                adapter.updateItems(listItems)
                            }
                        }) { Text("Discard") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { showCancelConfirm = false }) { Text("Keep editing") }
                    }
                }
            }
        }
    }

    // Donate nudge dialog (open-source build: shown when adding the 4th+ app)
    if (showDonateNudge) {
        DonateNudgeDialog(
            onDismiss = { showDonateNudge = false }
        )
    }

    // Remove confirmation dialog with countdown
    if (showRemoveConfirm) {
        var countdown by remember { mutableStateOf(5) }
        LaunchedEffect(Unit) {
            while (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            }
        }

        val filled = 5 - countdown
        val enabled = countdown == 0

        @OptIn(ExperimentalMaterial3Api::class)
        AlertDialog(
            onDismissRequest = {
                showRemoveConfirm = false
                adapter.updateItems(listItems)
            }
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
                    Text(
                        "Remove restriction",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Wait 5 seconds to click Remove")

                    Spacer(modifier = Modifier.height(20.dp))

                    // Countdown dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (i in 1..5) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (i <= filled)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showRemoveConfirm = false
                                adapter.updateItems(listItems)
                            }
                        ) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showRemoveConfirm = false
                                showRemoveOptions = true
                            },
                            enabled = enabled
                        ) { Text("Remove") }
                    }
                }
            }
        }
    }

    // Paused app options dialog
    if (showPausedOptions) {
        @OptIn(ExperimentalMaterial3Api::class)
        AlertDialog(
            onDismissRequest = { showPausedOptions = false }
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
                    Text(pausedAppName, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            showPausedOptions = false
                            val tempPrefs = context.getSharedPreferences(PrefsKeys.TEMP_UNRESTRICT, Context.MODE_PRIVATE)
                            tempPrefs.edit().remove(pausedAppName).apply()
                            tempUnrestrict = tempUnrestrict - pausedAppName
                            val minutes = appDurations[pausedAppName] ?: 5
                            snackbarMessage = "$pausedAppName restricted ($minutes min)"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove pause")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showPausedOptions = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    // Remove options dialog (1 hour vs permanently)
    if (showRemoveOptions) {
        @OptIn(ExperimentalMaterial3Api::class)
        AlertDialog(
            onDismissRequest = { showRemoveOptions = false }
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
                    Text("Remove restriction", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("How long?")
                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = {
                            showRemoveOptions = false
                            val expiry = System.currentTimeMillis() + 60 * 60 * 1000L
                            val tempPrefs = context.getSharedPreferences(PrefsKeys.TEMP_UNRESTRICT, Context.MODE_PRIVATE)
                            tempPrefs.edit().putLong(removeAppName, expiry).apply()
                            tempUnrestrict = tempUnrestrict + (removeAppName to expiry)
                            val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
                            val hasLock = prefs.contains(PrefsKeys.GLOBAL_LOCK_HASH)
                            val nudgeCount = prefs.getInt(PrefsKeys.LOCK_NUDGE_COUNT, 0)
                            val tip = if (!hasLock && nudgeCount < 5) {
                                prefs.edit().putInt(PrefsKeys.LOCK_NUDGE_COUNT, nudgeCount + 1).apply()
                                "\n\nTip: Are you tempted to remove restrictions? Learn about locking settings."
                            } else ""
                            snackbarMessage = "$removeAppName restriction paused for 1 hour$tip"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1 hour")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            showRemoveOptions = false
                            // Read the lock state BEFORE removing: removing the last app now
                            // clears the global lock, and the tip below is meant for people who
                            // never set one — not for someone whose lock we just tidied away.
                            val prefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
                            val hasLock = prefs.contains(PrefsKeys.GLOBAL_LOCK_HASH)
                            removeAppPermanently(
                                context, removeAppName, selectedApps,
                                onUpdate = { newApps, newDurations, newTemp ->
                                    selectedApps = newApps
                                    appDurations = newDurations
                                    tempUnrestrict = newTemp
                                },
                                appDurations = appDurations,
                                tempUnrestrict = tempUnrestrict,
                                onGlobalLockCleared = {
                                    // Keep the padlock UI in step with the pref. Without this the
                                    // toggle would still read locked, and tapping it would open a
                                    // password prompt that can never be satisfied — checkPassword
                                    // fails closed once the hash is gone.
                                    globalLockEnabled = false
                                    customOverridesVersion++
                                }
                            )
                            val nudgeCount = prefs.getInt(PrefsKeys.LOCK_NUDGE_COUNT, 0)
                            val tip = if (!hasLock && nudgeCount < 5) {
                                prefs.edit().putInt(PrefsKeys.LOCK_NUDGE_COUNT, nudgeCount + 1).apply()
                                "\n\nTip: Are you tempted to remove restrictions? Learn about locking settings."
                            } else ""
                            snackbarMessage = "$removeAppName restriction has been removed$tip"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Permanently")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showRemoveOptions = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    // Locked app password gate dialog
    if (showLockedPasswordPrompt) {
        PasswordPromptDialog(
            onUnlock = { password ->
                val resolver = AppSettingsResolver(context, lockedPasswordAppName)
                if (resolver.checkPassword(password)) {
                    sessionUnlockedApps.value = sessionUnlockedApps.value + lockedPasswordAppName
                    showLockedPasswordPrompt = false
                    lockedPasswordAction?.invoke()
                    lockedPasswordAction = null
                    true
                } else {
                    false
                }
            },
            onDismiss = {
                showLockedPasswordPrompt = false
                lockedPasswordAction = null
                adapter.updateItems(listItems)
            }
        )
    }

    // Auto-dismiss first launch hint 4 seconds after apps finish loading
    LaunchedEffect(isLoading) {
        if (!isLoading && showFirstLaunchHint) {
            kotlinx.coroutines.delay(4000)
            context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
                .edit().putBoolean(PrefsKeys.FIRST_LAUNCH_HINT_SHOWN, true).apply()
            showFirstLaunchHint = false
        }
    }

    // "Try it" task preview dialog
    if (showTryItPreview) {
        val resolver = remember(pickerAppName) { AppSettingsResolver(context, pickerAppName) }
        val tryTaskType = resolver.getInt(PrefsKeys.TASK_TYPE, PrefsKeys.DEFAULT_TASK_TYPE)
        val tryDifficulty = resolver.getInt(PrefsKeys.DIFFICULTY_LEVEL, PrefsKeys.DEFAULT_DIFFICULTY)
        val tryRevealSeconds = resolver.getInt(PrefsKeys.REVEAL_SECONDS, PrefsKeys.DEFAULT_REVEAL_SECONDS)
        val tryTypingCharSet = resolver.getInt(PrefsKeys.TYPING_CHAR_SET, PrefsKeys.DEFAULT_TYPING_CHAR_SET)
        val tryTypingLength = resolver.getInt(PrefsKeys.TYPING_LENGTH, PrefsKeys.DEFAULT_TYPING_LENGTH)
        val tryDotCount = resolver.getInt(PrefsKeys.TAPPING_DOT_COUNT, PrefsKeys.DEFAULT_TAPPING_DOTS)
        val tryDotDelay = resolver.getInt(PrefsKeys.TAPPING_DOT_DELAY, PrefsKeys.DEFAULT_TAPPING_DOT_DELAY)
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showTryItPreview = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                when (tryTaskType) {
                    0 -> MathChallengeScreen(
                        appName = pickerAppName, packageName = pickerPackageName,
                        difficulty = tryDifficulty,
                        revealSeconds = tryRevealSeconds,
                        onSuccess = { showTryItPreview = false },
                        onCancel = { showTryItPreview = false }
                    )
                    3 -> TappingTaskScreen(
                        appName = pickerAppName, packageName = pickerPackageName,
                        dotCount = tryDotCount, dotDelaySeconds = tryDotDelay,
                        revealSeconds = tryRevealSeconds,
                        onSuccess = { showTryItPreview = false },
                        onCancel = { showTryItPreview = false }
                    )
                    else -> TypingTaskScreen(
                        appName = pickerAppName, packageName = pickerPackageName,
                        charSet = tryTypingCharSet, length = tryTypingLength,
                        revealSeconds = tryRevealSeconds,
                        onSuccess = { showTryItPreview = false },
                        onCancel = { showTryItPreview = false }
                    )
                }
            }
        }
    }

    val settingsPreviewState = remember { mutableStateOf(false) }
    val settingsPrefs = context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
    var showSettingsDot by remember { mutableStateOf(!settingsPrefs.getBoolean(PrefsKeys.SETTINGS_NUDGE_SHOWN, false)) }

    // Trial lockout check — re-evaluated on every resume so dev tools and purchases take effect
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val trialExpired = remember(lifecycleState, isPurchased) {
        BuildConfig.ENFORCE_LIMIT && !TrialHelper.hasFullAccess(context)
    }
    // Re-query Play Billing on every resume so purchases completed while PIM was
    // backgrounded (e.g. during the Play purchase sheet) are picked up reliably,
    // even when the BillingClient listener didn't fire.
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED) {
            billingHelper.refreshPurchases()
        }
    }

    // Post-purchase thank-you: shown when isPurchased flips true while a buy/lockout overlay was up
    var showPurchaseThankYou by remember { mutableStateOf(false) }
    // Sticky flag — set true any time a buy flow is active. Survives the post-purchase
    // recomputation of trialExpired (which flips back to false the instant isPurchased turns true).
    var wasInBuyFlow by remember { mutableStateOf(false) }
    LaunchedEffect(showUpgradeScreen, trialExpired) {
        if (showUpgradeScreen || trialExpired) wasInBuyFlow = true
    }
    LaunchedEffect(isPurchased) {
        if (isPurchased && wasInBuyFlow) {
            showPurchaseThankYou = true
            showUpgradeScreen = false
            wasInBuyFlow = false
        }
    }
    // Dev preview of the thank-you screen
    LaunchedEffect(Unit) {
        val preview = (context as? ComponentActivity)?.intent?.getBooleanExtra("preview_thank_you", false) ?: false
        if (preview) {
            (context as? ComponentActivity)?.intent?.removeExtra("preview_thank_you")
            showPurchaseThankYou = true
        }
    }

    val showOverlay = trialExpired || showUpgradeScreen || showPurchaseThankYou
    @OptIn(ExperimentalMaterial3Api::class)
    Box(modifier = Modifier.fillMaxSize()) {
    val blurModifier = if (showOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(16.dp)
    } else {
        Modifier
    }
    Scaffold(
        modifier = blurModifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectedTab == 0) {
                CenterAlignedTopAppBar(
                    expandedHeight = 96.dp,
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "PIM",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontFamily = BrandFont,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                "Please Inconvenience Me",
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = BrandFont,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    },
                    actions = {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (BuildConfig.ENFORCE_LIMIT &&
                                    !TrialHelper.isPurchased(context) &&
                                    !TrialHelper.isLegacyUser(context)) {
                                    DropdownMenuItem(
                                        text = { Text("Buy PIM") },
                                        onClick = {
                                            showMenu = false
                                            showUpgradeScreen = true
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Help") },
                                    onClick = {
                                        showMenu = false
                                        context.startActivity(Intent(context, TroubleshootingActivity::class.java))
                                    }
                                )
                                if (BuildConfig.SHOW_DONATE_PROMPT) {
                                    DropdownMenuItem(
                                        text = { Text("Donate") },
                                        onClick = {
                                            showMenu = false
                                            context.startActivity(Intent(context, DonateActivity::class.java))
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("About") },
                                    onClick = {
                                        showMenu = false
                                        context.startActivity(Intent(context, AboutActivity::class.java))
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                TopAppBar(
                    expandedHeight = 96.dp,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Default",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            IconButton(onClick = {
                                if (globalLockEnabled) {
                                    showGlobalLockUnlockPrompt = true
                                } else {
                                    val hasPassword = settingsPrefsForLock.contains(PrefsKeys.GLOBAL_LOCK_HASH)
                                    if (hasPassword) {
                                        showGlobalLockLockPrompt = true
                                    } else {
                                        showGlobalLockCreatePassword = true
                                    }
                                }
                            }) {
                                Icon(
                                    painter = painterResource(
                                        if (globalLockEnabled) R.drawable.ic_lock_closed
                                        else R.drawable.ic_lock_open
                                    ),
                                    contentDescription = if (globalLockEnabled) "Locked" else "Unlocked",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    },
                    actions = {
                        OutlinedButton(
                            onClick = { settingsPreviewState.value = true },
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                            modifier = Modifier.height(38.dp).padding(end = 8.dp)
                        ) {
                            Text("Try it", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        bottomBar = {
            Surface(
                color = NavigationBarDefaults.containerColor,
                tonalElevation = NavigationBarDefaults.Elevation
            ) {
                Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val items = listOf(
                        Triple(0, Icons.Default.Home, "Home"),
                        Triple(1, Icons.Default.Settings, "Options")
                    )
                    items.forEach { (index, icon, label) ->
                        val selected = selectedTab == index
                        val color = if (selected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    selectedTab = index
                                    if (index == 1 && showSettingsDot) {
                                        showSettingsDot = false
                                        settingsPrefs.edit().putBoolean(PrefsKeys.SETTINGS_NUDGE_SHOWN, true).apply()
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (index == 1 && showSettingsDot) {
                                BadgedBox(badge = { Badge() }) {
                                    Icon(icon, contentDescription = label, tint = color)
                                }
                            } else {
                                Icon(icon, contentDescription = label, tint = color)
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = color
                            )
                        }
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Trial countdown banner (last 3 days)
                        val trialDaysLeft = remember(lifecycleState) { TrialHelper.getTrialDaysRemaining(context) }
                        val showTrialBanner = remember(lifecycleState) {
                            BuildConfig.ENFORCE_LIMIT &&
                            !TrialHelper.isPurchased(context) &&
                            !TrialHelper.isLegacyUser(context) &&
                            TrialHelper.getTrialState(context) == TrialState.IN_TRIAL &&
                            trialDaysLeft <= 3
                        }
                        if (showTrialBanner) {
                            Surface(
                                color = Color(0xFFE8A030),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showUpgradeScreen = true }
                            ) {
                                Text(
                                    "$trialDaysLeft day${if (trialDaysLeft != 1) "s" else ""} left in trial \u2014 Tap to buy",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                                )
                            }
                        }

                        // Battery-exemption warning: without Unrestricted, Android refuses
                        // background service starts and enforcement silently lapses. Persistent
                        // while true (re-checked on resume); doubles as the migration prompt for
                        // users who onboarded before the battery step became required.
                        val batteryExempt = remember(lifecycleState) {
                            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                            pm.isIgnoringBatteryOptimizations(context.packageName)
                        }
                        if (!batteryExempt && selectedApps.isNotEmpty()) {
                            val darkWarn = isSystemInDarkTheme()
                            Surface(
                                color = if (darkWarn) Color(0xFF3A3323) else Color(0xFFFAF3DC),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB8983D)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "PIM battery set to “Optimized”",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = if (darkWarn) Color(0xFFE8D9A0) else Color(0xFF6A5619)
                                    )
                                    Text(
                                        "Restrictions may fail if PIM can't run in the background.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (darkWarn) Color(0xFFBFB183) else Color(0xFF857337)
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                context.startActivity(Intent(
                                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                    android.net.Uri.parse("package:${context.packageName}")
                                                ))
                                            },
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB8983D)),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = if (darkWarn) Color(0xFFE8D9A0) else Color(0xFF6A5619)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Text("Allow", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }

                        // Native RecyclerView for smooth scrolling
                        AndroidView(
                            factory = { ctx ->
                                RecyclerView(ctx).apply {
                                    layoutManager = LinearLayoutManager(ctx)
                                    this.adapter = adapter
                                    setHasFixedSize(false)
                                    itemAnimator = null // Disable animations for snappier updates
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()
                        )
                    }
                }
            } else {
                SettingsContent(modifier = Modifier.padding(paddingValues), showPreviewState = settingsPreviewState)
            }

            if (showFirstLaunchHint && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x55000000))
                        .clickable {
                            context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
                                .edit().putBoolean(PrefsKeys.FIRST_LAUNCH_HINT_SHOWN, true).apply()
                            showFirstLaunchHint = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color(0xCC000000),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            "Scroll and tap an app\nyou want to restrict",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = BrandFont,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
    SnackbarHost(
        hostState = centerSnackbarHostState,
        modifier = Modifier.align(Alignment.Center)
    ) { data ->
        val msg = data.visuals.message
        val linkText = "locking settings."
        val linkIndex = msg.indexOf(linkText)
        Snackbar(modifier = Modifier.padding(24.dp).clickable { data.dismiss() }) {
            if (linkIndex >= 0) {
                val annotated = buildAnnotatedString {
                    append(msg.substring(0, linkIndex))
                    pushStringAnnotation("lock", "")
                    withStyle(SpanStyle(
                        color = MaterialTheme.colorScheme.inversePrimary,
                        textDecoration = TextDecoration.Underline
                    )) {
                        append(linkText)
                    }
                    pop()
                }
                ClickableText(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        textAlign = TextAlign.Center
                    ),
                    onClick = { offset ->
                        val isLink = annotated.getStringAnnotations("lock", offset, offset).firstOrNull()
                        if (isLink != null) {
                            data.dismiss()
                            context.startActivity(Intent(context, TroubleshootingActivity::class.java).apply {
                                putExtra("expand_faq", 6)
                            })
                        } else {
                            data.dismiss()
                        }
                    }
                )
            } else {
                Text(msg)
            }
        }
    }
    if (trialExpired && !showPurchaseThankYou) {
        TrialLockoutScreen(
            price = price,
            onUpgrade = { billingHelper.launchBillingFlow(activity) },
            onDevTools = {
                context.startActivity(Intent(context, AboutActivity::class.java).apply {
                    putExtra("open_dev_tools", true)
                })
            }
        )
    }
    if (showUpgradeScreen && !trialExpired && !showPurchaseThankYou) {
        TrialLockoutScreen(
            price = price,
            onUpgrade = { billingHelper.launchBillingFlow(activity) },
            onBack = { showUpgradeScreen = false }
        )
    }
    if (showPurchaseThankYou) {
        TrialLockoutScreen(
            price = null,
            onUpgrade = {},
            isPurchased = true,
            onDone = { showPurchaseThankYou = false },
            onLeaveRating = {
                reviewHelper.launchReview(activity)
                showPurchaseThankYou = false
            }
        )
    }
    }
}

private fun removeAppPermanently(
    context: Context,
    appName: String,
    selectedApps: Set<String>,
    onUpdate: (newApps: Set<String>, newDurations: Map<String, Int>, newTemp: Map<String, Long>) -> Unit,
    appDurations: Map<String, Int>,
    tempUnrestrict: Map<String, Long>,
    onGlobalLockCleared: () -> Unit
) {
    val newSelectedApps = selectedApps - appName

    context.getSharedPreferences(PrefsKeys.DURATIONS, Context.MODE_PRIVATE)
        .edit().remove(appName).apply()
    context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        .edit().putStringSet(PrefsKeys.RESTRICTED_APPS, newSelectedApps).apply()
    context.getSharedPreferences(PrefsKeys.TEMP_UNRESTRICT, Context.MODE_PRIVATE)
        .edit().remove(appName).apply()
    context.getSharedPreferences(PrefsKeys.SESSIONS, Context.MODE_PRIVATE)
        .edit()
        .remove(appName)
        .remove(PrefsKeys.LAST_SESSION_PREFIX + appName)
        .remove(PrefsKeys.SESSION_COUNT_PREFIX + appName)
        .apply()
    clearUsageLog(context, appName)

    // Clean up per-app overrides
    AppSettingsResolver(context, appName).clearAllOverrides()

    val serviceIntent = Intent(context, AppMonitorService::class.java)
    if (newSelectedApps.isNotEmpty()) {
        context.startForegroundService(serviceIntent)
    } else {
        context.stopService(serviceIntent)
        // Nothing is restricted any more, so the global lock has nothing left to guard. Leaving
        // it on strands the user: the padlock still reads locked, the next restriction is locked
        // from the moment it's added, and turning the lock off needs a password they may no
        // longer have. Removing a locked app already required that password (see the isLocked()
        // gate on the remove path), so clearing it here is not a way around the lock.
        // The hash goes with the flag, so a forgotten password can't silently come back the next
        // time the lock is switched on.
        context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PrefsKeys.LOCK_ALL_RESTRICTED, false)
            .remove(PrefsKeys.GLOBAL_LOCK_HASH)
            .apply()
        onGlobalLockCleared()
    }

    onUpdate(newSelectedApps, appDurations - appName, tempUnrestrict - appName)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPickerDialog(
    appName: String,
    initialMinutes: Int,
    hasCustomOverrides: Boolean = false,
    showRevertMessage: Boolean = false,
    onOverridesCleared: () -> Unit = {},
    onOpenCustomSettings: (currentMinutes: Int) -> Unit = {},
    onConfirm: (minutes: Int, isCustomMode: Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val steps = listOf(1, 3) + (1..12).map { it * 5 } // 1, 3, 5, 10, 15, ... 60
    val snapped = steps.minByOrNull { kotlin.math.abs(it - initialMinutes) } ?: 5
    var selectedMinutes by remember { mutableStateOf(snapped) }
    var settingsMode by remember { mutableStateOf(if (hasCustomOverrides) "custom" else "default") }
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false }
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Switch to default?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("All custom settings for $appName will be removed.")
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showResetConfirm = false }
                        ) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                AppSettingsResolver(context, appName).clearAllOverrides()
                                onOverridesCleared()
                                settingsMode = "default"
                                showResetConfirm = false
                            }
                        ) { Text("OK") }
                    }
                }
            }
        }
    }

    val selectedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    )

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // ── APP NAME ──
                Text(appName, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // ── OPTIONS ──
                Text("Options", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(20.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.wrapContentWidth().align(Alignment.CenterHorizontally)) {
                    SegmentedButton(
                        selected = settingsMode == "default",
                        onClick = {
                            if (settingsMode == "custom") showResetConfirm = true
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        colors = selectedColors
                    ) { Text("Default") }
                    SegmentedButton(
                        selected = settingsMode == "custom",
                        onClick = { onOpenCustomSettings(selectedMinutes) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        colors = selectedColors
                    ) { Text("Custom") }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // ── SESSION LENGTH ──
                Text("Session length", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                AndroidView(
                    factory = { ctx ->
                        android.widget.NumberPicker(ctx).apply {
                            minValue = 0
                            maxValue = steps.size - 1
                            displayedValues = steps.map { "$it min" }.toTypedArray()
                            value = steps.indexOf(snapped).coerceAtLeast(0)
                            wrapSelectorWheel = false
                            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            setOnValueChangedListener { _, _, newVal ->
                                selectedMinutes = steps[newVal]
                            }
                        }
                    },
                    update = { _ -> },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // ── OK / CANCEL ──
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(selectedMinutes, settingsMode == "custom") }) { Text("OK") }
                }

                // ── REVERT MESSAGE ──
                var messageVisible by remember { mutableStateOf(showRevertMessage) }
                LaunchedEffect(showRevertMessage) {
                    if (showRevertMessage) {
                        kotlinx.coroutines.delay(2500)
                        messageVisible = false
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = messageVisible) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No changes — kept Default",
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

            }
        }
    }

}

private data class ConfettiPiece(
    val xFraction: Float,
    val color: Color,
    val delayMs: Int,
    val fallDurationMs: Int,
    val rotationStart: Float,
    val rotationSpeed: Float,
    val width: Float,
    val height: Float,
    val drift: Float
)

@Composable
fun ConfettiOverlay() {
    val palette = listOf(
        Color(0xFFFF6B6B),
        Color(0xFFFFD166),
        Color(0xFF06D6A0),
        Color(0xFF4DA3FF),
        Color(0xFFEF476F),
        Color(0xFFB388FF)
    )
    val pieces = remember {
        List(60) {
            ConfettiPiece(
                xFraction = Random.nextFloat(),
                color = palette.random(),
                delayMs = Random.nextInt(0, 1500),
                fallDurationMs = Random.nextInt(3000, 4800),
                rotationStart = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() * 720f) - 360f,
                width = Random.nextFloat() * 6f + 8f,
                height = Random.nextFloat() * 4f + 4f,
                drift = (Random.nextFloat() * 160f) - 80f
            )
        }
    }
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        time.animateTo(
            targetValue = 7000f,
            animationSpec = tween(durationMillis = 7000, easing = LinearEasing)
        )
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (p in pieces) {
            val elapsed = (time.value - p.delayMs).coerceAtLeast(0f)
            if (elapsed <= 0f) continue
            val progress = (elapsed / p.fallDurationMs.toFloat()).coerceAtMost(1.2f)
            if (progress > 1.1f) continue
            val y = -30f + (h + 60f) * progress
            val x = w * p.xFraction + p.drift * progress
            val rot = p.rotationStart + p.rotationSpeed * progress
            val alpha = if (progress > 0.85f) ((1.1f - progress) / 0.25f).coerceIn(0f, 1f) else 1f
            rotate(rot, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.width / 2f, y - p.height / 2f),
                    size = GeomSize(p.width, p.height)
                )
            }
        }
    }
}

@Composable
fun TrialLockoutScreen(
    price: String?,
    onUpgrade: () -> Unit,
    onDevTools: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    isPurchased: Boolean = false,
    onDone: () -> Unit = {},
    onLeaveRating: () -> Unit = {}
) {
    var devTapCount by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                if (!isPurchased) onBack?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isSystemInDarkTheme()) Color(0xFF0A4A6A) else MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // PIM branded header
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 20.dp)
                    ) {
                        Text(
                            "PIM",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontFamily = BrandFont,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.clickable {
                                devTapCount++
                                if (devTapCount >= 7) {
                                    devTapCount = 0
                                    onDevTools()
                                }
                            }
                        )
                        Text(
                            "Please Inconvenience Me",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = BrandFont,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isPurchased) {
                        val checkColor = if (isSystemInDarkTheme()) Color(0xFFE8B840) else Color(0xFFC9A227)
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = checkColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Thank you!",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = PlayfairDisplay,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "You own PIM forever — no subscriptions, no monthly fees.",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                        Text(
                            "A rating helps small projects like PIM more than you'd think.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = onLeaveRating,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape
                        ) {
                            Text("Leave a rating")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape
                        ) {
                            Text("Not now", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            if (onBack != null) "Buy PIM" else "Your free trial has ended",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (onBack != null) "Keep blocking distractions and stay focused."
                            else "Your 7-day trial is over. Buy now to keep blocking distractions and stay focused.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSystemInDarkTheme()) Color(0xFF1A3A4A) else Color(0xFFE8F1F8),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                val bullets = listOf(
                                    "One-time purchase, not a subscription",
                                    "No ads, no tracking",
                                    "Restrict as many apps as you want",
                                    "Support continued improvement"
                                )
                                bullets.forEach { bullet ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text(
                                            "\u2713",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(bullet, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onUpgrade,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape
                        ) {
                            Text(if (price != null) "Buy \u2014 $price" else "Buy")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (onBack != null) {
                            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                                Text("Not now", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                        } else {
                            val closeContext = LocalContext.current
                            OutlinedButton(onClick = { (closeContext as? ComponentActivity)?.finish() }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                                Text("Not now", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        if (isPurchased) {
            ConfettiOverlay()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateNudgeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("You're on a roll", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(12.dp))
                Text("PIM is free on F-Droid. If it's been useful, consider supporting development.")
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Maybe later") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onDismiss()
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://pleaseinconvenienceme.com/support")))
                    }) { Text("Support development") }
                }
            }
        }
    }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = appOps.checkOpNoThrow(
        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}
