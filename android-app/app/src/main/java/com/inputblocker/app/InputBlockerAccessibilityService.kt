package com.inputblocker.app

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.inputblocker.shared.Region
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * AccessibilityService providing a trusted touch-blocking overlay
 * using [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY].
 *
 * Bypasses Android 12+ restrictions on [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * which prevent apps from intercepting touches through overlays.
 *
 * Key features:
 * - Config file monitoring via [ConfigFileObserver] with polling fallback
 * - Notification with pause/resume action buttons
 * - Block counter displayed in notification and on overlay
 * - Rate-limited block logging to prevent spam
 * - Emergency kill-switch via volume key sequence
 * - Foreground app detection for per-profile switching
 */
class InputBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "InputBlocker-Accessibility"
        private const val CHANNEL_ID = "InputBlockerAccessibility"
        private const val NOTIFICATION_ID = 1002

        /** Pause durations (ms) for notification action buttons */
        private const val PAUSE_5MIN_MS = 5 * 60 * 1000L
        private const val PAUSE_30MIN_MS = 30 * 60 * 1000L

        /** Max time (ms) for emergency sequence completion */
        private const val SEQUENCE_TIMEOUT_MS = 5000L

        /** Poll interval (ms) as fallback when FileObserver is unavailable */
        private const val POLL_INTERVAL_MS = 2000L

        /** Minimum gap (ms) between block log entries to prevent spam */
        private const val RATE_LIMIT_MS = 300L

        /** Notification action strings */
        private const val ACTION_PAUSE_5MIN = "com.inputblocker.accessibility.PAUSE_5MIN"
        private const val ACTION_PAUSE_30MIN = "com.inputblocker.accessibility.PAUSE_30MIN"
        private const val ACTION_RESUME = "com.inputblocker.accessibility.RESUME"
    }

    // ── Window management ──────────────────────────────────────────

    private var wm: WindowManager? = null
    private var overlayView: TouchBlockOverlay? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    // ── State ───────────────────────────────────────────────────────

    private val regions = CopyOnWriteArrayList<Region>()
    @Volatile private var blockingEnabled = true
    @Volatile private var forceSafeMode = false
    @Volatile private var accessibilityMode = true
    @Volatile private var paused = false
    private var pauseExpirationTime = 0L
    private var currentProfile = "default"
    private var lastForegroundPkg: String? = null
    private var blockingExpirationTime = 0L
    @Volatile private var isRunning = true

    // ── Rate limiting ───────────────────────────────────────────────

    private var lastBlockLogTime = 0L
    private val blockCount = AtomicInteger(0)

    // ── FileObserver ────────────────────────────────────────────────

    private var configObserver: ConfigFileObserver? = null

    // ── Key sequence tracking ──────────────────────────────────────

    private val keyHistory = ArrayList<Int>()
    private val keyTimestamps = ArrayList<Long>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resetSequenceTask = Runnable {
        keyHistory.clear()
        keyTimestamps.clear()
    }

    // ── Notification action receiver ────────────────────────────────

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PAUSE_5MIN -> pauseBlocking(PAUSE_5MIN_MS)
                ACTION_PAUSE_30MIN -> pauseBlocking(PAUSE_30MIN_MS)
                ACTION_RESUME -> resumeBlocking()
            }
        }
    }

    // ── Global action receiver (from MainActivity broadcasts) ────

    private val globalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.inputblocker.PAUSE" -> pauseBlocking(60000)
                "com.inputblocker.RESUME" -> resumeBlocking()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Service Lifecycle
    // ═══════════════════════════════════════════════════════════════

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService connected")
        isRunning = true

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(ACTION_PAUSE_5MIN)
            addAction(ACTION_PAUSE_30MIN)
            addAction(ACTION_RESUME)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(notificationReceiver, filter)
        }

        // Register MainActivity PAUSE/RESUME broadcasts
        val globalFilter = IntentFilter().apply {
            addAction("com.inputblocker.PAUSE")
            addAction("com.inputblocker.RESUME")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(globalReceiver, globalFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(globalReceiver, globalFilter)
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        createOverlay()

        // Load config and start observers
        loadConfig()
        updateOverlay()
        startConfigWatching()
        startPollLoop()
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted — removing overlay")
        removeOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != lastForegroundPkg) {
                lastForegroundPkg = pkg
                handleForegroundChange(pkg)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> trackKeySequence(0)
                KeyEvent.KEYCODE_VOLUME_UP -> trackKeySequence(1)
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(notificationReceiver) } catch (_: Exception) { }
        try { unregisterReceiver(globalReceiver) } catch (_: Exception) { }
        removeOverlay()
        configObserver?.stop()
        Log.i(TAG, "AccessibilityService destroyed")
    }

    // ═══════════════════════════════════════════════════════════════
    //  Config Loading & Watching
    // ═══════════════════════════════════════════════════════════════

    private fun loadConfig() {
        val oldRegions = regions.toList()
        regions.clear()
        val configFile = File(InputBlockerServiceManager.getConfigFile(this, currentProfile))
        if (!configFile.exists()) {
            if (currentProfile != "default") {
                currentProfile = "default"
                loadConfig()
            }
            return
        }

        try {
            BufferedReader(FileReader(configFile)).use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("enabled=") ->
                            blockingEnabled = trimmed.substring(8) == "1"
                        trimmed.startsWith("force_safe_mode=") -> {
                            forceSafeMode = trimmed.substring(15) == "1"
                            if (forceSafeMode) blockingEnabled = false
                        }
                        trimmed.startsWith("accessibility_mode=") ->
                            accessibilityMode = trimmed.substring(19) == "1"
                        trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                            Region.fromString(trimmed)?.let { regions.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config for profile $currentProfile", e)
        }

        // Only reset block count when regions actually changed
        if (regions.toList() != oldRegions) {
            blockCount.set(0)
        }
    }

    private fun startConfigWatching() {
        configObserver?.stop()
        val configDir = File(InputBlockerServiceManager.getConfigDir(this))
        if (!configDir.exists()) configDir.mkdirs()

        val observer = ConfigFileObserver(
            configDir.absolutePath,
            { onChange() }
        )
        observer.start()
        configObserver = observer
    }

    private fun onChange() {
        loadConfig()
        runOnUiThread { updateOverlay() }
    }

    private fun handleForegroundChange(pkg: String) {
        val profileFile = File(InputBlockerServiceManager.getConfigFile(this, pkg))
        if (profileFile.exists()) {
            currentProfile = pkg
            loadConfig()
            runOnUiThread { updateOverlay() }
        } else if (currentProfile != "default") {
            currentProfile = "default"
            loadConfig()
            runOnUiThread { updateOverlay() }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Poll Loop (fallback for non-inotify filesystems)
    // ═══════════════════════════════════════════════════════════════

    private fun startPollLoop() {
        Thread {
            while (isRunning) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                    if (!isRunning) break
                    loadConfig()
                    runOnUiThread { updateOverlay() }

                    // Auto-expire timed blocking sessions
                    if (blockingEnabled && blockingExpirationTime > 0 &&
                        System.currentTimeMillis() > blockingExpirationTime
                    ) {
                        blockingEnabled = false
                        runOnUiThread {
                            updateOverlay()
                            Toast.makeText(
                                this@InputBlockerAccessibilityService,
                                "Blocking session expired",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    // Auto-resume from pause
                    if (paused && System.currentTimeMillis() > pauseExpirationTime) {
                        paused = false
                        runOnUiThread { updateOverlay() }
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Poll loop error", e)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    // ═══════════════════════════════════════════════════════════════
    //  Pause / Resume
    // ═══════════════════════════════════════════════════════════════

    private fun pauseBlocking(durationMs: Long) {
        paused = true
        pauseExpirationTime = System.currentTimeMillis() + durationMs
        runOnUiThread {
            updateOverlay()
            val mins = durationMs / 60000
            Toast.makeText(
                this@InputBlockerAccessibilityService,
                "Blocking paused for $mins min",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun resumeBlocking() {
        paused = false
        pauseExpirationTime = 0L
        runOnUiThread {
            updateOverlay()
            Toast.makeText(
                this@InputBlockerAccessibilityService,
                "Blocking resumed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Overlay Management
    // ═══════════════════════════════════════════════════════════════

    private fun createOverlay() {
        val manager = wm ?: return
        overlayView = TouchBlockOverlay(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        overlayParams = params

        try {
            manager.addView(overlayView, params)
            Log.i(TAG, "Accessibility overlay created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create accessibility overlay", e)
        }
    }

    private fun removeOverlay() {
        try {
            overlayView?.let {
                wm?.removeView(it)
                overlayView = null
            }
        } catch (_: Exception) { }
    }

    private fun updateOverlay() {
        val active = accessibilityMode && blockingEnabled && !forceSafeMode && !paused
        overlayView?.apply {
            setRegions(regions)
            setBlockingEnabled(active)
            setBlockCount(blockCount.get())
        }

        val view = overlayView ?: return
        val params = overlayParams ?: return
        val manager = wm ?: return

        if (active && regions.isNotEmpty()) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try {
            manager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overlay touch state", e)
        }

        updateNotification()
    }

    // ═══════════════════════════════════════════════════════════════
    //  Emergency Key Sequence (Volume Down x3 → Volume Up x3)
    // ═══════════════════════════════════════════════════════════════

    private fun trackKeySequence(type: Int) {
        val now = System.currentTimeMillis()
        mainHandler.removeCallbacks(resetSequenceTask)

        keyHistory.add(type)
        keyTimestamps.add(now)

        while (keyHistory.size > 6) {
            keyHistory.removeAt(0)
            keyTimestamps.removeAt(0)
        }

        if (keyHistory.size == 6 && checkSequence()) {
            triggerEmergencyKillSwitch()
        } else {
            mainHandler.postDelayed(resetSequenceTask, SEQUENCE_TIMEOUT_MS)
        }
    }

    private fun checkSequence(): Boolean {
        if (keyTimestamps.last() - keyTimestamps.first() > SEQUENCE_TIMEOUT_MS) return false
        return keyHistory == listOf(0, 0, 0, 1, 1, 1)
    }

    private fun triggerEmergencyKillSwitch() {
        Log.w(TAG, "EMERGENCY KILL-SWITCH triggered!")
        keyHistory.clear()
        keyTimestamps.clear()

        InputBlockerServiceManager.enableSafeMode(this)
        vibratePattern()
        loadConfig()
        runOnUiThread {
            Toast.makeText(
                this@InputBlockerAccessibilityService,
                "BLOCKING DISABLED: Emergency sequence detected",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Haptic Feedback
    // ═══════════════════════════════════════════════════════════════

    private fun vibratePattern() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 200, 100, 200, 100, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Notification
    // ═══════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "InputBlocker (Accessibility)",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = buildString {
            append("InputBlocker")
            when {
                !accessibilityMode -> append(" — Disabled (config)")
                forceSafeMode -> append(" — Safe Mode")
                paused -> append(" — Paused")
                !blockingEnabled -> append(" — Disabled")
                regions.isEmpty() -> append(" — No zones")
                else -> append(" — Active")
            }
            if (blockCount.get() > 0) {
                append(" [${blockCount.get()} blocked]")
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("InputBlocker")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)

        if (!paused && blockingEnabled) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause 5m",
                PendingIntent.getBroadcast(
                    this, 1,
                    Intent(ACTION_PAUSE_5MIN).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause 30m",
                PendingIntent.getBroadcast(
                    this, 2,
                    Intent(ACTION_PAUSE_30MIN).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else if (paused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                PendingIntent.getBroadcast(
                    this, 3,
                    Intent(ACTION_RESUME).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        return builder.build()
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  UI Thread Helper
    // ═══════════════════════════════════════════════════════════════

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  TouchBlockOverlay
    // ═══════════════════════════════════════════════════════════════

    inner class TouchBlockOverlay(context: Context) : View(context) {

        private val blockPaint = Paint().apply {
            color = Color.parseColor("#1A00FF00")
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint().apply {
            color = Color.parseColor("#00FF00")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val textPaint = Paint().apply {
            color = Color.parseColor("#00FF00")
            textSize = 36f
            isFakeBoldText = true
        }
        private val countPaint = Paint().apply {
            color = Color.argb(180, 0, 255, 0)
            textSize = 24f
        }

        private val currentRegions = mutableListOf<Region>()
        private var active = false
        private var count = 0

        fun setRegions(list: List<Region>) {
            currentRegions.clear()
            currentRegions.addAll(list)
            postInvalidate()
        }

        fun setBlockingEnabled(enabled: Boolean) {
            active = enabled
            postInvalidate()
        }

        fun setBlockCount(c: Int) {
            count = c
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            for (region in currentRegions) {
                when (region.type) {
                    0 -> {
                        val r = RectF(
                            region.x1 * width, region.y1 * height,
                            region.x2 * width, region.y2 * height
                        )
                        canvas.drawRect(r, blockPaint)
                        canvas.drawRect(r, borderPaint)
                    }
                    1 -> {
                        val cx = region.x1 * width
                        val cy = region.y1 * height
                        val r = region.x2 * width
                        canvas.drawCircle(cx, cy, r, blockPaint)
                        canvas.drawCircle(cx, cy, r, borderPaint)
                    }
                    2 -> {
                        val cxf = (region.x1 - region.x2) * width
                        val cyf = (region.y1 - region.y2) * height
                        val cxs = (region.x1 + region.x2) * width
                        val cys = (region.y1 + region.y2) * height
                        canvas.drawOval(RectF(cxf, cyf, cxs, cys), blockPaint)
                        canvas.drawOval(RectF(cxf, cyf, cxs, cys), borderPaint)
                    }
                }
            }

            if (!active) return

            val statusMsg = if (paused) {
                val remaining = ((pauseExpirationTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                "PAUSED — ${remaining}s remaining"
            } else if (currentRegions.isEmpty()) {
                "InputBlocker: No zones configured"
            } else {
                "BLOCKING ${currentRegions.size} zone(s)"
            }
            canvas.drawText(statusMsg, 10f, 50f, textPaint)
            if (count > 0) {
                canvas.drawText("Total: $count blocked", 10f, 90f, countPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!active || currentRegions.isEmpty() || paused) return false

            val nx = event.x / width
            val ny = event.y / height

            if (event.action == MotionEvent.ACTION_DOWN ||
                event.action == MotionEvent.ACTION_MOVE
            ) {
                for (region in currentRegions) {
                    if (region.isExclude && isInside(nx, ny, region)) return false
                }

                for (region in currentRegions) {
                    if (!region.isExclude && isInside(nx, ny, region)) {
                        val contactArea = event.pressure
                        if (contactArea < region.minPressure) {
                            blockCount.incrementAndGet()

                            // Rate-limit block logging
                            val now = System.currentTimeMillis()
                            if (now - lastBlockLogTime > RATE_LIMIT_MS) {
                                lastBlockLogTime = now
                                val timeStr = java.text.SimpleDateFormat(
                                    "HH:mm:ss", java.util.Locale.getDefault()
                                ).format(java.util.Date())
                                OverlayService.addBlockEntry(
                                    BlockLogActivity.BlockEntry(
                                        timeStr,
                                        "Blocked at (%.2f, %.2f)  count=%d".format(
                                            nx, ny, blockCount.get()
                                        )
                                    )
                                )
                            }
                            postInvalidate()
                            return true
                        }
                    }
                }
            }
            return false
        }

        private fun isInside(nx: Float, ny: Float, r: Region): Boolean {
            return when (r.type) {
                0 -> nx >= r.x1 && nx <= r.x2 && ny >= r.y1 && ny <= r.y2
                1 -> {
                    val dx = (nx - r.x1) * width
                    val dy = (ny - r.y1) * height
                    val radius = r.x2 * width
                    (dx * dx + dy * dy) <= (radius * radius)
                }
                2 -> {
                    val dx = (nx - r.x1)
                    val dy = (ny - r.y1)
                    (dx * dx) / (r.x2 * r.x2) + (dy * dy) / (r.y2 * r.y2) <= 1f
                }
                else -> false
            }
        }
    }
}
