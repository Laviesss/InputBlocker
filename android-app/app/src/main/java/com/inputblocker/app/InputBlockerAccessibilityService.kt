package com.inputblocker.app

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AccessibilityService that provides a trusted touch-blocking overlay
 * using [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY].
 *
 * This bypasses Android 12+ restrictions on [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * which prevent apps from intercepting touches through overlays.
 *
 * The service also handles:
 * - Emergency kill-switch via volume key sequence (Down x3 → Up x3) via [onKeyEvent]
 * - Foreground app detection via [android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]
 * - Config file monitoring with automatic region reload
 */
class InputBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "InputBlocker-Accessibility"
        private const val CHANNEL_ID = "InputBlockerAccessibility"
        private const val NOTIFICATION_ID = 1002

        /** Max time (ms) in which the 6-key emergency sequence must be completed */
        private const val SEQUENCE_TIMEOUT_MS = 5000L

        /** How often (ms) to poll config and check expiration */
        private const val POLL_INTERVAL_MS = 2000L
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
    private var currentProfile = "default"
    private var lastForegroundPkg: String? = null
    private var blockingExpirationTime = 0L
    @Volatile private var isRunning = true

    // ── Key sequence tracking ──────────────────────────────────────

    private val keyHistory = ArrayList<Int>()
    private val keyTimestamps = ArrayList<Long>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resetSequenceTask = Runnable {
        keyHistory.clear()
        keyTimestamps.clear()
    }

    // ── Service lifecycle ───────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService connected")
        isRunning = true

        // Acquire WindowManager — always via getSystemService.
        // TYPE_ACCESSIBILITY_OVERLAY is available since API 16 (our minimum).
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        createOverlay()

        // Initial config load + start polling
        loadConfig()
        updateOverlay()
        startPollLoop()
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
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
                KeyEvent.KEYCODE_VOLUME_UP   -> trackKeySequence(1)
            }
        }
        // Return false so volume keys still control media volume normally
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            overlayView?.let { wm?.removeView(it) }
        } catch (_: Exception) { }
        Log.i(TAG, "AccessibilityService destroyed")
    }

    // ── Config loading ──────────────────────────────────────────────

    private fun loadConfig() {
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

    // ── Poll loop ───────────────────────────────────────────────────

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
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Poll loop error", e)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    // ── Overlay management ──────────────────────────────────────────

    private fun createOverlay() {
        val manager = wm ?: return
        overlayView = TouchBlockOverlay(this)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // createWindowManager() auto-assigns TYPE_ACCESSIBILITY_OVERLAY
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,  // Start non-blocking
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        overlayParams = params

        try {
            manager.addView(overlayView, params)
            Log.i(TAG, "Accessibility overlay created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add accessibility overlay", e)
        }
    }

    private fun updateOverlay() {
        val active = accessibilityMode && blockingEnabled && !forceSafeMode
        overlayView?.apply {
            setRegions(regions)
            setBlockingEnabled(active)
        }

        // Toggle FLAG_NOT_TOUCHABLE to intercept touches only when active
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

    // ── Emergency sequence (Volume Down x3 → Volume Up x3) ─────────

    private fun trackKeySequence(type: Int) {
        val now = System.currentTimeMillis()
        mainHandler.removeCallbacks(resetSequenceTask)

        keyHistory.add(type)
        keyTimestamps.add(now)

        // Keep rolling window of 6
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
        Log.w(TAG, "EMERGENCY KILL-SWITCH triggered via AccessibilityService!")
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

    // ── Haptic feedback ────────────────────────────────────────────

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

    // ── Notification ───────────────────────────────────────────────

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
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            !accessibilityMode -> "Disabled (accessibility_mode=0)"
            forceSafeMode      -> "Safe Mode (Disabled)"
            !blockingEnabled   -> "Blocking disabled"
            regions.isEmpty()  -> "No zones configured"
            else               -> "Blocking ${regions.size} region(s)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("InputBlocker")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    // ── UI thread helper ────────────────────────────────────────────

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  TouchBlockOverlay — full-screen overlay that intercepts touches
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

        private val currentRegions = mutableListOf<Region>()
        private var active = false

        fun setRegions(list: List<Region>) {
            currentRegions.clear()
            currentRegions.addAll(list)
            postInvalidate()
        }

        fun setBlockingEnabled(enabled: Boolean) {
            active = enabled
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!active) return

            for (region in currentRegions) {
                when (region.type) {
                    0 -> { // Rectangle
                        val r = RectF(
                            region.x1 * width, region.y1 * height,
                            region.x2 * width, region.y2 * height
                        )
                        canvas.drawRect(r, blockPaint)
                        canvas.drawRect(r, borderPaint)
                    }
                    1 -> { // Circle
                        val cx = region.x1 * width
                        val cy = region.y1 * height
                        val r = region.x2 * width
                        canvas.drawCircle(cx, cy, r, blockPaint)
                        canvas.drawCircle(cx, cy, r, borderPaint)
                    }
                    2 -> { // Ellipse
                        val cxf = (region.x1 - region.x2) * width
                        val cyf = (region.y1 - region.y2) * height
                        val cxs = (region.x1 + region.x2) * width
                        val cys = (region.y1 + region.y2) * height
                        canvas.drawOval(RectF(cxf, cyf, cxs, cys), blockPaint)
                        canvas.drawOval(RectF(cxf, cyf, cxs, cys), borderPaint)
                    }
                }
            }

            val msg = if (currentRegions.isEmpty()) {
                "InputBlocker: No zones configured"
            } else {
                "BLOCKED: ${currentRegions.size} zone(s)"
            }
            canvas.drawText(msg, 10f, 50f, textPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!active || currentRegions.isEmpty()) return false

            val nx = event.x / width
            val ny = event.y / height

            if (event.action == MotionEvent.ACTION_DOWN ||
                event.action == MotionEvent.ACTION_MOVE
            ) {
                // Whitelist check first (exclusion regions)
                for (region in currentRegions) {
                    if (region.isExclude && isInside(nx, ny, region)) return false
                }

                // Block check
                for (region in currentRegions) {
                    if (!region.isExclude && isInside(nx, ny, region)) {
                        // Android's MotionEvent.getPressure() returns contact area
                        // (capacitive patch size), not physical force.
                        val contactArea = event.pressure
                        if (contactArea < region.minPressure) {
                            val timeStr = java.text.SimpleDateFormat(
                                "HH:mm:ss", java.util.Locale.getDefault()
                            ).format(java.util.Date())
                            OverlayService.addBlockEntry(
                                BlockLogActivity.BlockEntry(
                                    timeStr,
                                    "Blocked at (%.2f, %.2f)".format(nx, ny)
                                )
                            )
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
