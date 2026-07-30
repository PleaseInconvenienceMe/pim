package com.pleaseinconvenienceme.pim

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AppMonitorService : Service() {
    companion object {
        // How often we sample the foreground app. Tradeoff: lower = snappier detection and a
        // wider natural grace window for redirect relays (see MIN_FOREGROUND_TIME_MS), but more
        // frequent wakeups = worse battery. Was 150 originally; raised to 300 to save battery,
        // which is what re-introduced the Maps-link challenge flash — so if you change this,
        // keep MIN_FOREGROUND_TIME_MS comfortably above it.
        private const val POLL_INTERVAL_MS = 300L
        private const val WATCHDOG_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes

        fun scheduleWatchdog(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BootReceiver::class.java).apply {
                action = "com.pleaseinconvenienceme.pim.WATCHDOG"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                pendingIntent
            )
        }
        private const val FIRST_CHECK_LOOKBACK_MS = 60_000L
        private const val NORMAL_LOOKBACK_MS = 10_000L
        // The "settle window": how long a restricted app reached BY JUMPING FROM ANOTHER APP
        // must stay in the foreground before we challenge it. A redirect relay (e.g. a restricted
        // browser briefly opening to resolve a Google Maps link before handing off to the Maps
        // app) departs within this time, so the challenge never flashes on it. This is applied
        // ONLY to app-to-app jumps — deliberate opens from the launcher/recents/notification
        // challenge instantly (settle window of 0). See currentForegroundFromLauncher and the
        // check in checkForegroundApp.
        //
        // Must comfortably exceed one POLL_INTERVAL_MS, otherwise the first poll can already see
        // a relay past the threshold and challenge it (this is exactly what regressed when
        // POLL_INTERVAL_MS was raised 150 -> 300, back when this delay applied to every open).
        //
        // Tradeoffs on the value:
        //  - Higher: slower link handoffs are absorbed more reliably, BUT a link you actually
        //    wanted that lands on a restricted app (indistinguishable from a relay at first
        //    instant) waits longer before its challenge. Too high starts to feel like a loophole.
        //  - Lower: snappier on those ambiguous link-opens, but the Maps-link flash creeps back
        //    once the delay drops near/below POLL_INTERVAL_MS.
        // Because deliberate opens no longer pay this cost, we can keep it comfortably high.
        // Measured a real case: a browser (Vanadium) resolving an Apple Maps link held the
        // foreground ~983ms before handing off to the Maps app, so 500ms was too short and the
        // challenge flashed. 1500ms clears a ~1s handoff with margin; the only cost is that a
        // link you actually wanted that lands on a restricted app waits this long to challenge.
        // Pass-through tracking below is a backstop for a relay that flickers back AFTER leaving;
        // it cannot stop the initial flash, because the mark is only set on departure.
        private const val MIN_FOREGROUND_TIME_MS = 1500L
        // If a restricted app leaves the foreground in under this time, it was likely a
        // redirect relay, not a deliberate open. Mark it as a pass-through. Kept in step with
        // the settle window so a ~1s browser->Maps relay is also recognized by the backstop.
        private const val PASS_THROUGH_THRESHOLD_MS = 1500L
        // How long a pass-through mark stays valid. After this, treat the app as fresh.
        private const val PASS_THROUGH_TTL_MS = 3_000L
        private const val DEDUP_INTERVAL_MS = 5_000L
        private const val COLD_OPEN_THRESHOLD_MS = 4 * 60 * 60 * 1000L // 4 hours
    }

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var isMonitoring = false
    // Set when startForeground was refused in onCreate (background-start restriction);
    // the instance is shutting down and must not begin monitoring or sticky-restart.
    private var foregroundStartFailed = false

    // Resurrects the service when the screen is unlocked, in case it was killed
    private val screenUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isMonitoring) {
                isMonitoring = true
                startMonitoring()
            }
            scheduleWatchdog(context)
        }
    }
    private var lastCheckedTime = System.currentTimeMillis()
    // Track the latest UsageStats event timestamp we've processed, so we don't
    // re-process the same events on every poll (avoids fg-change log spam and
    // wasteful state churn).
    private var lastProcessedEventTime: Long = 0L
    private lateinit var restrictedApps: Set<String>

    // Cache package name to app name mapping
    private val packageNameCache = mutableMapOf<String, String>()
    // Cache whether a package has a launcher icon (user-facing app vs background process)
    private val launcherIntentCache = mutableMapOf<String, Boolean>()

    // Simple dedup: don't show challenge/soft entry more than once per 5 seconds
    private var lastChallengeTime: Long = 0
    private var lastSoftEntryTime: Long = 0

    // Track what the user is currently using
    private var currentForegroundAppName: String? = null
    private var currentForegroundPackage: String? = null
    private var currentForegroundClassName: String? = null
    private var currentForegroundSince: Long = 0
    // Origin of the current foreground app: was it opened directly from the home launcher /
    // system UI (a deliberate icon tap, recents, notification, or return), or jumped to from
    // another app (the link-relay pattern)? Deliberate origins challenge instantly; app-to-app
    // jumps wait out the settle window so redirect relays (e.g. a Maps link) can pass through.
    // Defaults true so the first observation after boot errs toward an instant challenge.
    private var currentForegroundFromLauncher: Boolean = true
    // Cached set of home-launcher package names (resolved once from the HOME intent).
    private var homePackages: Set<String>? = null

    // Pass-through tracking: if a restricted app appeared briefly then left (e.g. a browser
    // opening a Maps link), we record it here so we don't incorrectly trigger on it.
    // Maps appName → timestamp when it was marked as a pass-through.
    private val recentPassThroughs = mutableMapOf<String, Long>()

    // Usage tracking: prevent double-logging a single visit
    private var usageLogged = false

    // Overlay timer — all overlay state is managed on the main thread via mainHandler
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    // These are only read/written inside mainHandler posts:
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayAppName: String? = null
    // Delayed hide: prevents flicker when system events briefly interrupt foreground tracking
    private val hideOverlayRunnable = Runnable {
        if (overlayView != null) MonitorEventLog.log(this, "ovl-hide-delayed $overlayAppName")
        overlayView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlayView = null; overlayParams = null; overlayAppName = null
        sessionExpiryRunnable?.let { mainHandler.removeCallbacks(it) }
        sessionExpiryRunnable = null; sessionExpiryAppName = null
    }
    // Session expiry safety net — guarantees session expires even if polling loop dies
    private var sessionExpiryRunnable: Runnable? = null
    private var sessionExpiryAppName: String? = null
    private var lastScheduledExpiryMs: Long = 0

    // Heartbeat: updated at the start of each checkForegroundApp() call
    @Volatile private var lastPollTimestamp: Long = 0
    private fun getOverlayPosition(appName: String): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        val overridePrefs = getSharedPreferences(PrefsKeys.APP_OVERRIDES, Context.MODE_PRIVATE)
        val globalPrefs = getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        val xKey = "$appName::${PrefsKeys.OVERLAY_X}"
        val yKey = "$appName::${PrefsKeys.OVERLAY_Y}"
        val x = if (overridePrefs.contains(xKey)) overridePrefs.getInt(xKey, -1)
                 else globalPrefs.getInt(PrefsKeys.OVERLAY_X, -1)
        val y = if (overridePrefs.contains(yKey)) overridePrefs.getInt(yKey, -1)
                 else globalPrefs.getInt(PrefsKeys.OVERLAY_Y, -1)
        return if (x >= 0 && y >= 0) Pair(x, y) else {
            Pair((metrics.widthPixels * 0.65f).toInt(), (metrics.heightPixels * 0.06f).toInt())
        }
    }

    private fun saveOverlayPosition(appName: String, x: Int, y: Int) {
        getSharedPreferences(PrefsKeys.APP_OVERRIDES, Context.MODE_PRIVATE)
            .edit().putInt("$appName::${PrefsKeys.OVERLAY_X}", x)
                   .putInt("$appName::${PrefsKeys.OVERLAY_Y}", y).apply()
    }

    private fun showOverlay(appName: String, packageName: String, sessionEndMs: Long) {
        if (!Settings.canDrawOverlays(this)) return
        val overlaySize = AppSettingsResolver(this, appName).getInt(PrefsKeys.OVERLAY_TIMER_SIZE, PrefsKeys.DEFAULT_OVERLAY_SIZE)
        if (overlaySize == PrefsKeys.OVERLAY_SIZE_OFF) return
        val isLarge = overlaySize == PrefsKeys.OVERLAY_SIZE_LARGE

        // Calculate display text before the foreground check so we can update existing overlay
        // even when the check would otherwise suppress a new overlay creation.
        val now = System.currentTimeMillis()
        val totalSecondsLeft = ((sessionEndMs - now) / 1000L).toInt().coerceAtLeast(0)
        val displayText = String.format("%d:%02d", totalSecondsLeft / 60, totalSecondsLeft % 60)

        // Fix 4: Verify the restricted app is still foreground via a direct queryEvents check.
        // This prevents the overlay lingering after the user leaves. If a different app is
        // foreground but our overlay is already showing, still update the text (don't freeze it).
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val recentEvents = usm.queryEvents(now - 2000L, now)
            val ev = UsageEvents.Event()
            var lastForegroundPackage: String? = null
            while (recentEvents.hasNextEvent()) {
                recentEvents.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForegroundPackage = ev.packageName
                }
            }
            if (lastForegroundPackage != null && lastForegroundPackage != packageName) {
                // Different app is foreground — update existing overlay text but don't create new
                mainHandler.post {
                    if (overlayAppName == appName && overlayView != null) {
                        (overlayView as? LinearLayout)?.findViewWithTag<TextView>("timer_text")?.text = displayText
                    }
                }
                return
            }
        } catch (_: Exception) { /* proceed if query fails */ }

        // Cancel any pending delayed hide — overlay should stay visible
        mainHandler.removeCallbacks(hideOverlayRunnable)

        // All overlay state is managed inside this main-thread post
        mainHandler.post {
            try {
            // Also cancel here in case a hide was posted between our cancel above and this post
            mainHandler.removeCallbacks(hideOverlayRunnable)
            if (overlayAppName == appName && overlayView != null) {
                // Overlay already showing — just update text
                (overlayView as? LinearLayout)?.findViewWithTag<TextView>("timer_text")?.text = displayText
            } else {
                MonitorEventLog.log(this, "ovl-show $appName secLeft=$totalSecondsLeft")
                // Create new overlay (or replace stale one)
                overlayView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
                overlayView = null
                if (windowManager == null) windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val (posX, posY) = getOverlayPosition(appName)
                val density = resources.displayMetrics.density

                val scale = if (isLarge) 2f else 1f
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((12 * scale * density).toInt(), (8 * scale * density).toInt(), (16 * scale * density).toInt(), (8 * scale * density).toInt())
                    background = GradientDrawable().apply {
                        setColor(0x99006492.toInt())
                        cornerRadius = 24 * scale * density
                    }
                }

                val icon = ImageView(this).apply {
                    setImageDrawable(ContextCompat.getDrawable(this@AppMonitorService, R.mipmap.ic_launcher_round))
                    layoutParams = LinearLayout.LayoutParams(
                        (22 * scale * density).toInt(),
                        (22 * scale * density).toInt()
                    ).apply { marginEnd = (8 * scale * density).toInt() }
                }

                val tv = TextView(this).apply {
                    tag = "timer_text"
                    text = displayText
                    setTextColor(0xFFFFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * scale)
                    typeface = Typeface.DEFAULT_BOLD
                }

                container.addView(icon)
                container.addView(tv)

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = posX
                    y = posY
                }
                var initTouchX = 0f; var initTouchY = 0f
                var initParamX = 0; var initParamY = 0
                container.setOnTouchListener { _, event ->
                    try {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> { initTouchX = event.rawX; initTouchY = event.rawY; initParamX = params.x; initParamY = params.y; true }
                            MotionEvent.ACTION_MOVE -> { params.x = initParamX + (event.rawX - initTouchX).toInt(); params.y = initParamY + (event.rawY - initTouchY).toInt(); windowManager?.updateViewLayout(container, params); true }
                            MotionEvent.ACTION_UP -> { saveOverlayPosition(appName, params.x, params.y); true }
                            else -> false
                        }
                    } catch (_: Exception) { false }
                }
                overlayView = container
                overlayParams = params
                overlayAppName = appName
                try { windowManager?.addView(container, params) } catch (_: Exception) { overlayView = null; overlayParams = null; overlayAppName = null }
            }

            // Fix 3: Session expiry safety net — schedule a runnable that fires at session end.
            // This guarantees the session expires even if the polling loop has died.
            // Only reschedule if the app changed or expiry time shifted by more than 1 second.
            val msUntilExpiry = sessionEndMs - System.currentTimeMillis()
            if (msUntilExpiry > 0 &&
                (sessionExpiryAppName != appName || Math.abs(sessionEndMs - lastScheduledExpiryMs) > 1000L)) {
                sessionExpiryRunnable?.let { mainHandler.removeCallbacks(it) }
                val expiryRunnable = Runnable {
                    MonitorEventLog.log(this, "sess-end-safetynet $appName")
                    hideOverlayNow()
                    handler?.post {
                        try { checkForegroundApp() } catch (e: Exception) {
                            android.util.Log.e("PIM", "checkForegroundApp error (expiry)", e)
                        }
                    }
                }
                sessionExpiryRunnable = expiryRunnable
                sessionExpiryAppName = appName
                lastScheduledExpiryMs = sessionEndMs
                mainHandler.postDelayed(expiryRunnable, msUntilExpiry + 500)
            }

            } catch (_: Exception) { overlayView = null; overlayParams = null; overlayAppName = null }
        }
    }

    private fun hideOverlay() {
        // Use a short delay so rapid hide+show cycles (from system events) don't cause flicker.
        mainHandler.removeCallbacks(hideOverlayRunnable)
        mainHandler.postDelayed(hideOverlayRunnable, 500)
    }

    /** Immediately remove the overlay with no delay (used in onDestroy and session expiry). */
    private fun hideOverlayNow() {
        mainHandler.removeCallbacks(hideOverlayRunnable)
        mainHandler.post {
            if (overlayView != null) MonitorEventLog.log(this, "ovl-hide-now $overlayAppName")
            overlayView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
            overlayView = null; overlayParams = null; overlayAppName = null
            sessionExpiryRunnable?.let { mainHandler.removeCallbacks(it) }
            sessionExpiryRunnable = null; sessionExpiryAppName = null; lastScheduledExpiryMs = 0
        }
    }

    /** Fix 2: Restart a dead HandlerThread and polling loop. Called on the main thread. */
    private fun restartPollingLoop() {
        MonitorEventLog.log(this, "pol-restart")
        handlerThread?.quitSafely()
        handlerThread = HandlerThread("AppMonitorThread").also { it.start() }
        handler = Handler(handlerThread!!.looper)
        isMonitoring = true
        startMonitoring()
    }

    /** Fix 5: Watchdog that checks every second whether the polling loop has stalled,
     *  and restarts it if so. Runs on the main thread, independent of the polling thread,
     *  so it can detect and recover from a fully dead HandlerThread. */
    private var watchdogTickCount = 0
    private val pollingWatchdog = object : Runnable {
        override fun run() {
            watchdogTickCount++
            val pollAge = System.currentTimeMillis() - lastPollTimestamp
            if (lastPollTimestamp > 0 && pollAge > 2000L) {
                android.util.Log.w("PIM", "Watchdog: polling stalled ${pollAge}ms, restarting")
                MonitorEventLog.log(this@AppMonitorService, "wd-stall pollAge=${pollAge}ms restarting")
                restartPollingLoop()
            } else if (watchdogTickCount % 30 == 0) {
                // Heartbeat every ~30s with current state for diagnostics
                val fg = currentForegroundAppName ?: "null"
                val ovl = overlayAppName ?: "null"
                MonitorEventLog.log(this@AppMonitorService, "wd-tick pollAge=${pollAge}ms fg=$fg ovl=$ovl")
            }
            if (isMonitoring) {
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Foreground promotion is refused when the service is being resurrected from the
        // background (watchdog alarm, WorkManager watchdog, sticky restart) and the app
        // lacks a background-start exemption (e.g. battery optimization not disabled).
        // Without this guard that refusal is a crash ("PIM keeps stopping") which sticky-
        // restart turns into a crash loop. Instead: bow out quietly; the service comes
        // back on the next legitimate start (opening PIM, or a watchdog firing while
        // the app is exempt/temporarily allowlisted).
        try {
            startForeground(1, createNotification())
        } catch (e: Exception) {
            android.util.Log.e("PIM", "startForeground refused (background start?)", e)
            MonitorEventLog.log(this, "svc-fg-refused ${e.javaClass.simpleName}")
            foregroundStartFailed = true
            // Stopping inside the grace window also avoids the follow-up
            // ForegroundServiceDidNotStartInTimeException crash.
            stopSelf()
            return
        }
        loadSettings()

        handlerThread = HandlerThread("AppMonitorThread").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        ContextCompat.registerReceiver(this, screenUnlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED)
        MonitorEventLog.log(this, "svc-create")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // onCreate failed to reach the foreground and already called stopSelf — don't
        // start monitoring on a doomed instance, and don't ask for a sticky restart
        // (it would come back in the same disallowed background context and fail again).
        if (foregroundStartFailed) return START_NOT_STICKY
        // Always reload settings in case they changed
        loadSettings()
        scheduleWatchdog(this)
        ServiceWatchdogWorker.schedule(this)
        if (!isMonitoring) {
            // Fresh start (or restart from dead state): reset foreground tracking and
            // event-dedup so first poll re-evaluates from scratch via FIRST_CHECK_LOOKBACK_MS.
            // Critical: do NOT reset on every onStartCommand — routine watchdog re-arms call
            // here and resetting mid-session breaks foreground tracking for steadily-used apps.
            handler?.post {
                currentForegroundAppName = null
                currentForegroundPackage = null
                currentForegroundClassName = null
                currentForegroundSince = 0
                lastProcessedEventTime = 0L
            }
            isMonitoring = true
            startMonitoring()
        }
        mainHandler.removeCallbacks(pollingWatchdog)
        mainHandler.postDelayed(pollingWatchdog, 1000L)
        MonitorEventLog.log(this, "svc-start")
        return START_STICKY
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        restrictedApps = prefs.getStringSet(PrefsKeys.RESTRICTED_APPS, emptySet()) ?: emptySet()
    }

    private fun startMonitoring() {
        handler?.postDelayed(object : Runnable {
            override fun run() {
                try {
                    checkForegroundApp()
                } catch (e: Exception) {
                    // Fix 1: Log exceptions so they appear in logcat for diagnosis
                    android.util.Log.e("PIM", "checkForegroundApp error", e)
                }
                if (isMonitoring) {
                    handler?.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }, POLL_INTERVAL_MS)
    }

    private fun checkForegroundApp() {
        // Fix 2: Update heartbeat so showOverlay can detect if polling has stalled
        lastPollTimestamp = System.currentTimeMillis()
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()

        // Reload settings every time to stay in sync
        loadSettings()

        // On first check, look back 60 seconds to catch an app that's already open.
        // Otherwise look back 10 seconds to account for UsageStats reporting delays
        // (events can be delayed several seconds before appearing in the query).
        val queryFrom = if (currentForegroundAppName == null) {
            currentTime - FIRST_CHECK_LOOKBACK_MS
        } else {
            currentTime - NORMAL_LOOKBACK_MS
        }

        // Process events to track what app is in the foreground
        val events = usageStatsManager.queryEvents(queryFrom, currentTime)
        val event = UsageEvents.Event()
        var maxEventTime = lastProcessedEventTime

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            // Skip events we've already processed in a previous poll cycle.
            // queryEvents returns the same events on consecutive polls within
            // its lookback window, which causes log spam and state churn.
            if (event.timeStamp <= lastProcessedEventTime) continue
            if (event.timeStamp > maxEventTime) maxEventTime = event.timeStamp

            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val packageName = event.packageName

                // For our own app, home launchers, and system UI, trigger an app-switch so the
                // overlay is hidden, but don't proceed to restriction checks. Recording these as
                // the current foreground is also what lets the next app-open see it was reached
                // from a deliberate origin (see currentForegroundFromLauncher).
                if (isDeliberateOrigin(packageName)) {
                    if (packageName != currentForegroundPackage) {
                        currentForegroundAppName?.let { oldApp ->
                            if (restrictedApps.contains(oldApp)) hideOverlay()
                        }
                        currentForegroundAppName = packageName
                        currentForegroundPackage = packageName
                        currentForegroundClassName = event.className ?: ""
                        currentForegroundSince = event.timeStamp
                    }
                    continue
                }

                // Ignore background system processes (no launcher icon) — these briefly
                // appear in UsageStats but are not user-navigated apps (e.g. Play Services)
                if (!hasLauncherIntent(packageName)) continue

                val appName = getAppNameFromPackage(packageName)
                if (appName != currentForegroundAppName) {
                    MonitorEventLog.log(this, "fg-change ${currentForegroundAppName ?: "(none)"} -> $appName")
                    currentForegroundAppName?.let { oldApp ->
                        if (restrictedApps.contains(oldApp)) hideOverlay()
                        // Log usage if leaving a restricted app mid-session or mid-pause
                        if (!usageLogged && restrictedApps.contains(oldApp) && currentForegroundSince > 0) {
                            val sessionPrefs = getSharedPreferences(PrefsKeys.SESSIONS, Context.MODE_PRIVATE)
                            val rawSessionEnd = sessionPrefs.getLong(oldApp, 0L)
                            val tempPrefs = getSharedPreferences(PrefsKeys.TEMP_UNRESTRICT, Context.MODE_PRIVATE)
                            val tempExpiry = tempPrefs.getLong(oldApp, 0L)
                            val effectiveEnd = maxOf(rawSessionEnd, tempExpiry)
                            if (effectiveEnd > 0) {
                                val duration = minOf(event.timeStamp, effectiveEnd) - currentForegroundSince
                                if (duration > 0) logUsage(this, oldApp, duration)
                            }
                        }
                        usageLogged = false
                        // If the old app was restricted and only appeared briefly, it was
                        // likely a redirect relay (e.g. browser handing off to Maps).
                        // Mark it so we don't trigger if it flickers back into foreground.
                        if (restrictedApps.contains(oldApp) &&
                            event.timeStamp - currentForegroundSince < PASS_THROUGH_THRESHOLD_MS) {
                            recentPassThroughs[oldApp] = event.timeStamp
                        }
                    }
                    // Classify how this app was reached before we overwrite the tracked package:
                    // the package we're leaving IS the origin. From a launcher/system UI =
                    // deliberate (instant challenge); from another app = a possible link relay
                    // (gets the settle window). Null origin (first observation) errs to instant.
                    val fromPackage = currentForegroundPackage
                    currentForegroundFromLauncher = fromPackage == null || isDeliberateOrigin(fromPackage)
                    // App coming to foreground fresh — clear any prior pass-through mark.
                    // The user deliberately opened it this time.
                    recentPassThroughs.remove(appName)
                    currentForegroundSince = event.timeStamp
                } else if (usageLogged) {
                    // Same app re-entered foreground after task — reset tracking
                    usageLogged = false
                    currentForegroundSince = event.timeStamp
                }
                currentForegroundAppName = appName
                currentForegroundPackage = packageName
                currentForegroundClassName = event.className ?: ""
            }
        }

        lastCheckedTime = currentTime
        lastProcessedEventTime = maxEventTime

        // Now check: is the current foreground app restricted and without a valid session?
        val appName = currentForegroundAppName ?: return
        val packageName = currentForegroundPackage ?: return

        if (!restrictedApps.contains(appName)) return

        // Skip if trial expired and not purchased (let user access apps freely)
        if (!TrialHelper.hasFullAccess(this)) return

        // Skip if temporarily unrestricted
        val tempPrefs = getSharedPreferences(PrefsKeys.TEMP_UNRESTRICT, Context.MODE_PRIVATE)
        val tempExpiry = tempPrefs.getLong(appName, 0L)
        if (tempExpiry > currentTime) return

        // Skip PWAs running inside a restricted browser
        val className = currentForegroundClassName ?: ""
        if (isPWAClassName(className)) return

        // Check session status
        val sessionPrefs = getSharedPreferences(PrefsKeys.SESSIONS, Context.MODE_PRIVATE)
        val rawSessionEnd = sessionPrefs.getLong(appName, 0L)

        // Active session — let them use the app
        if (rawSessionEnd > currentTime) {
            showOverlay(appName, packageName, rawSessionEnd)
            return
        }

        // Session expired — always hide overlay immediately, regardless of what happens next
        hideOverlay()

        // Deliberate opens (tapped from home, recents, a notification, or returned from a
        // challenge) get an instant challenge. App-to-app jumps wait out the settle window so a
        // redirect relay (e.g. a restricted browser resolving a Maps link before handing off to
        // the Maps app) can pass through without the challenge ever flashing. A jump that
        // actually stays put still gets challenged once the window elapses — this only delays
        // the ambiguous case, it never lets an app through un-challenged.
        val settleWindow = if (currentForegroundFromLauncher) 0L else MIN_FOREGROUND_TIME_MS
        val dwell = currentTime - currentForegroundSince
        if (dwell < settleWindow) {
            MonitorEventLog.log(this, "eval-wait $appName fromLauncher=$currentForegroundFromLauncher dwell=${dwell}ms settle=${settleWindow}ms")
            return
        }

        // Skip if this app recently passed through as a redirect relay (e.g. browser opening
        // a Maps link). The pass-through mark is cleared if the user deliberately re-opens it.
        val passedThroughAt = recentPassThroughs[appName]
        if (passedThroughAt != null) {
            if (currentTime - passedThroughAt < PASS_THROUGH_TTL_MS) {
                MonitorEventLog.log(this, "eval-passthru $appName age=${currentTime - passedThroughAt}ms")
                return
            }
            else recentPassThroughs.remove(appName) // TTL expired — treat as a fresh open
        }

        // Decide: soft entry (cold open) vs task (session just expired)
        // If no session ever, or expired longer ago than the session duration → soft entry
        // If expired recently (user was actively using it) → task
        val durationPrefs = getSharedPreferences(PrefsKeys.DURATIONS, Context.MODE_PRIVATE)
        val sessionDurationMs = durationPrefs.getInt(appName, 5) * 60 * 1000L
        val isColdOpen = rawSessionEnd == 0L || (currentTime - rawSessionEnd) > COLD_OPEN_THRESHOLD_MS

        if (isColdOpen) {
            if (TaskActivity.isActive.get()) return
            if (currentTime - lastChallengeTime < DEDUP_INTERVAL_MS) return

            lastChallengeTime = currentTime
            MonitorEventLog.log(this, "challenge-cold $appName fromLauncher=$currentForegroundFromLauncher dwell=${dwell}ms")
            val intent = Intent(this, TaskActivity::class.java).apply {
                putExtra("APP_NAME", appName)
                putExtra("PACKAGE_NAME", packageName)
                // Cold open (never used, or >4h ago) is never a back-to-back continuation.
                putExtra("IS_CONTINUATION", false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val noAnim = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
            startActivity(intent, noAnim)
        } else {
            // Session just expired — log usage then show the task
            if (SoftEntryActivity.isActive.get()) return
            if (currentTime - lastSoftEntryTime < DEDUP_INTERVAL_MS) return
            if (TaskActivity.isActive.get()) return
            if (currentTime - lastChallengeTime < DEDUP_INTERVAL_MS) return

            if (!usageLogged && currentForegroundSince > 0 && rawSessionEnd > currentForegroundSince) {
                logUsage(this, appName, rawSessionEnd - currentForegroundSince)
                usageLogged = true
            }

            lastChallengeTime = currentTime
            MonitorEventLog.log(this, "challenge-task $appName fromLauncher=$currentForegroundFromLauncher dwell=${dwell}ms")
            val intent = Intent(this, TaskActivity::class.java).apply {
                putExtra("APP_NAME", appName)
                putExtra("PACKAGE_NAME", packageName)
                // Back-to-back: the session expired while the user was still in the app (they
                // entered before it ended). A return after leaving has currentForegroundSince
                // after the session end, so it stays false.
                putExtra("IS_CONTINUATION", currentForegroundSince > 0 && rawSessionEnd > currentForegroundSince)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val noAnim = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
            startActivity(intent, noAnim)
        }
    }

    // Packages that count as a "deliberate origin": the home launcher(s), the recents/overview
    // and system UI, the legacy launcher, and PIM itself. An app reached directly from one of
    // these was opened on purpose (icon tap, recents, notification, or return from a challenge),
    // NOT jumped to from another app as a redirect relay. Drives instant-challenge vs. settle-
    // window in the foreground check.
    private fun isDeliberateOrigin(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (packageName == "com.android.launcher") return true
        if (packageName.startsWith("com.android.systemui")) return true
        return getHomePackages().contains(packageName)
    }

    // The device's home launcher package(s). Resolved from the HOME intent, not hardcoded, so
    // this works across stock and third-party launchers (and their recents/overview provider).
    private fun getHomePackages(): Set<String> {
        homePackages?.let { return it }
        val pkgs = try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(homeIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
        homePackages = pkgs
        return pkgs
    }

    private fun hasLauncherIntent(packageName: String): Boolean {
        return launcherIntentCache.getOrPut(packageName) {
            // Has a launcher icon (shows in app drawer)
            if (packageManager.getLaunchIntentForPackage(packageName) != null) return@getOrPut true
            // Is a home screen launcher (handles HOME intent but has no app drawer icon)
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(homeIntent, 0)
                .any { it.activityInfo.packageName == packageName }
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return packageNameCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pim_monitor",
                "App Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors restricted apps"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "pim_monitor")
            .setContentTitle("PIM Active")
            .setContentText("Monitoring restricted apps")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleWatchdog(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        MonitorEventLog.log(this, "svc-destroy")
        isMonitoring = false
        mainHandler.removeCallbacks(pollingWatchdog)
        hideOverlayNow()
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        try { unregisterReceiver(screenUnlockReceiver) } catch (_: Exception) {}
    }
}
