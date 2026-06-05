package com.inputblocker.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import com.inputblocker.shared.Region
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class OverlayService : Service() {

    companion object {
        private const val TAG = "InputBlocker-Overlay"
        private const val CHANNEL_ID = "InputBlockerOverlay"
        private const val NOTIFICATION_ID = 1001

        /** Pause durations (ms) for notification action buttons */
        private const val PAUSE_5MIN_MS = 5 * 60 * 1000L
        private const val PAUSE_30MIN_MS = 30 * 60 * 1000L

        /** Minimum gap (ms) between block log entries */
        private const val RATE_LIMIT_MS = 300L

        private const val ACTION_PAUSE_5MIN = "com.inputblocker.overlay.PAUSE_5MIN"
        private const val ACTION_PAUSE_30MIN = "com.inputblocker.overlay.PAUSE_30MIN"
        private const val ACTION_RESUME = "com.inputblocker.overlay.RESUME"

        private val globalBlockLog = ConcurrentLinkedQueue<BlockLogActivity.BlockEntry>()

        fun getRecentBlocks(): List<BlockLogActivity.BlockEntry> {
            return globalBlockLog.toList().reversed()
        }

        fun addBlockEntry(entry: BlockLogActivity.BlockEntry) {
            globalBlockLog.add(entry)
            if (globalBlockLog.size > 50) {
                globalBlockLog.poll()
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var touchBlockView: TouchBlockView? = null
    private val regions = CopyOnWriteArrayList<Region>()
    @Volatile private var isEnabled = true
    @Volatile private var forceSafeMode = false
    @Volatile private var paused = false
    private var pauseExpirationTime = 0L
    private var currentProfile = "default"
    private var lastForegroundApp: String? = null
    private var blockingExpirationTime = 0L

    private var configReceiver: BroadcastReceiver? = null
    private var configObserver: ConfigFileObserver? = null
    private var isRunning = true
    private var overlayParams: WindowManager.LayoutParams? = null

    // ── Rate limiting & block counter ───────────────────────────────
    private var lastBlockLogTime = 0L
    private val blockCount = AtomicInteger(0)

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

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "OverlayService starting...")
        isRunning = true
        createNotificationChannel()

        // Register notification action receiver
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

        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        loadConfig()
        createOverlayView()
        registerConfigReceiver()
        startConfigWatching()
        startPollLoop()
    }

    // ═══════════════════════════════════════════════════════════════
    //  Config Monitoring (FileObserver + polling fallback)
    // ═══════════════════════════════════════════════════════════════

    private fun startConfigWatching() {
        configObserver?.stop()
        val configDir = File(InputBlockerServiceManager.getConfigDir(this))
        if (!configDir.exists()) configDir.mkdirs()

        val observer = ConfigFileObserver(
            configDir.absolutePath,
            { reloadConfig() }
        )
        observer.start()
        configObserver = observer
    }

    private fun startPollLoop() {
        Thread {
            while (isRunning) {
                try {
                    Thread.sleep(2000)
                    if (!isRunning) break

                    // Foreground app detection
                    val fgApp = getForegroundPackage()
                    if (fgApp != null && fgApp != lastForegroundApp) {
                        lastForegroundApp = fgApp
                        val profileFile = File(InputBlockerServiceManager.getConfigFile(this, fgApp))
                        if (profileFile.exists()) {
                            currentProfile = fgApp
                            reloadConfig()
                        } else if (currentProfile != "default") {
                            currentProfile = "default"
                            reloadConfig()
                        }
                    }

                    // Auto-expire blocking session
                    if (isEnabled && blockingExpirationTime > 0 &&
                        System.currentTimeMillis() > blockingExpirationTime
                    ) {
                        disableBlocking()
                        runOnUiThread {
                            Toast.makeText(this@OverlayService, "Blocking session expired", Toast.LENGTH_LONG).show()
                        }
                    }

                    // Auto-resume from pause
                    if (paused && System.currentTimeMillis() > pauseExpirationTime) {
                        paused = false
                        runOnUiThread {
                            updateOverlayState()
                            Toast.makeText(this@OverlayService, "Blocking resumed", Toast.LENGTH_SHORT).show()
                        }
                    }
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
            updateOverlayState()
            val mins = durationMs / 60000
            Toast.makeText(this, "Blocking paused for $mins min", Toast.LENGTH_SHORT).show()
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun resumeBlocking() {
        paused = false
        pauseExpirationTime = 0L
        runOnUiThread {
            updateOverlayState()
            Toast.makeText(this, "Blocking resumed", Toast.LENGTH_SHORT).show()
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, createNotification())
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Legacy helper
    // ═══════════════════════════════════════════════════════════════

    private fun isLsposedModeFromConfig(): Boolean {
        val configFile = File(InputBlockerServiceManager.getConfigFile(this, "default"))
        if (!configFile.exists()) return false
        return try {
            configFile.readLines().any { it.trim().startsWith("lsposed_mode=1") }
        } catch (e: Exception) {
            false
        }
    }

    private fun getForegroundPackage(): String? {
        val output = InputBlockerServiceManager.runRootCommand("dumpsys activity activities | grep mResumedActivity")
        if (output.isBlank()) return null
        val match = Regex(" ([a-zA-Z0-9._]+)/").find(output)
        return match?.groupValues?.get(1)
    }

    // ═══════════════════════════════════════════════════════════════
    //  Notification
    // ═══════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "InputBlocker Active", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_TOGGLE_BLOCKING
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val safeIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_SAFE_MODE
        }
        val safePendingIntent = PendingIntent.getBroadcast(
            this, 2, safeIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = buildString {
            when {
                forceSafeMode -> append("Safe Mode")
                paused -> append("Paused")
                isEnabled -> append("Active — ${regions.size} zone(s)")
                else -> append("Disabled")
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
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Toggle", togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Safe Mode", safePendingIntent
            )

        // Add pause/resume actions when blocking is active
        if (!paused && isEnabled && !forceSafeMode) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause 5m",
                PendingIntent.getBroadcast(
                    this, 3,
                    Intent(ACTION_PAUSE_5MIN).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause 30m",
                PendingIntent.getBroadcast(
                    this, 4,
                    Intent(ACTION_PAUSE_30MIN).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else if (paused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                PendingIntent.getBroadcast(
                    this, 5,
                    Intent(ACTION_RESUME).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        return builder.build()
    }

    // ═══════════════════════════════════════════════════════════════
    //  Config Loading
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
                        trimmed.startsWith("enabled=") -> isEnabled = trimmed.substring(8) == "1"
                        trimmed.startsWith("force_safe_mode=") -> {
                            forceSafeMode = trimmed.substring(15) == "1"
                            if (forceSafeMode) isEnabled = false
                        }
                        trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                            Region.fromString(trimmed)?.let { regions.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config", e)
        }

        // Reset block count when regions actually change
        if (regions.toList() != oldRegions) {
            blockCount.set(0)
        }
    }

    private fun reloadConfig() {
        loadConfig()
        runOnUiThread { updateOverlayState() }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification())
    }

    private fun emergencyReset() {
        InputBlockerServiceManager.enableSafeMode(this)
        reloadConfig()
        runOnUiThread {
            Toast.makeText(this, "EMERGENCY RESET triggered", Toast.LENGTH_LONG).show()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Overlay Management
    // ═══════════════════════════════════════════════════════════════

    private fun createOverlayView() {
        if (windowManager == null || isLsposedModeFromConfig()) return

        touchBlockView = TouchBlockView(this)
        touchBlockView?.setService(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        overlayParams = params

        touchBlockView?.apply {
            setRegions(regions)
            setBlockingEnabled(isEnabled && !forceSafeMode)
            setBlockCount(blockCount.get())
        }

        try {
            windowManager?.addView(touchBlockView, params)
            updateOverlayTouchThroughput()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
        }
    }

    private fun updateOverlayTouchThroughput() {
        val view = touchBlockView ?: return
        val params = overlayParams ?: return
        val wm = windowManager ?: return

        val shouldBlockTouches = isEnabled && !forceSafeMode && regions.isNotEmpty() && !paused

        if (shouldBlockTouches) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        try {
            wm.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overlay touch behavior", e)
        }
    }

    private fun updateOverlayState() {
        touchBlockView?.apply {
            setRegions(regions)
            setBlockingEnabled(isEnabled && !forceSafeMode && !paused)
            setBlockCount(blockCount.get())
        }
        updateOverlayTouchThroughput()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification())
    }

    // ═══════════════════════════════════════════════════════════════
    //  Broadcast Receiver Registration
    // ═══════════════════════════════════════════════════════════════

    private fun registerConfigReceiver() {
        configReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.inputblocker.RELOAD" -> reloadConfig()
                    "com.inputblocker.DISABLE" -> disableBlocking()
                    "com.inputblocker.ENABLE" -> enableBlocking(intent.getBooleanExtra("force_safe", true))
                    "com.inputblocker.PAUSE" -> pauseBlocking(60000)
                    "com.inputblocker.RESUME" -> resumeBlocking()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.inputblocker.RELOAD")
            addAction("com.inputblocker.DISABLE")
            addAction("com.inputblocker.ENABLE")
            addAction("com.inputblocker.PAUSE")
            addAction("com.inputblocker.RESUME")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(configReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(configReceiver, filter)
        }
    }

    private fun disableBlocking() {
        isEnabled = false
        runOnUiThread { updateOverlayState() }
    }

    private fun enableBlocking(clearSafeMode: Boolean) {
        if (clearSafeMode) forceSafeMode = false
        isEnabled = true
        runOnUiThread { updateOverlayState() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                "RELOAD" -> reloadConfig()
                "DISABLE" -> disableBlocking()
                "ENABLE" -> enableBlocking(true)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        configObserver?.stop()
        configReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {
                Log.e(TAG, "Error unregistering config receiver", e)
            }
        }
        try { unregisterReceiver(notificationReceiver) } catch (_: Exception) { }
        touchBlockView?.let { windowManager?.removeView(it) }
    }

    @Nullable
    override fun onBind(intent: Intent?): IBinder? = null

    private fun runOnUiThread(action: Runnable) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }

    // ═══════════════════════════════════════════════════════════════
    //  TouchBlockView
    // ═══════════════════════════════════════════════════════════════

    inner class TouchBlockView(context: Context) : View(context) {
        private var serviceRef = WeakReference<OverlayService>(null)
        fun setService(service: OverlayService) { serviceRef = WeakReference(service) }

        private val blockPaint = Paint().apply {
            color = Color.parseColor("#1A00FF00"); style = Paint.Style.FILL
        }
        private val borderPaint = Paint().apply {
            color = Color.parseColor("#00FF00"); style = Paint.Style.STROKE; strokeWidth = 4f
        }
        private val textPaint = Paint().apply {
            color = Color.parseColor("#00FF00"); textSize = 36f; isFakeBoldText = true
        }
        private val countPaint = Paint().apply {
            color = Color.argb(180, 0, 255, 0); textSize = 24f
        }

        private val regionsList = mutableListOf<Region>()
        private var enabled = true
        private var count = 0

        fun setRegions(list: List<Region>) {
            regionsList.clear(); regionsList.addAll(list); invalidate()
        }
        fun setBlockingEnabled(e: Boolean) { enabled = e; invalidate() }
        fun setBlockCount(c: Int) { count = c; invalidate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!enabled) return

            for (region in regionsList) {
                when (region.type) {
                    0 -> {
                        val rect = android.graphics.RectF(
                            region.x1 * width, region.y1 * height,
                            region.x2 * width, region.y2 * height
                        )
                        canvas.drawRect(rect, blockPaint)
                        canvas.drawRect(rect, borderPaint)
                    }
                    1 -> {
                        val cx = region.x1 * width
                        val cy = region.y1 * height
                        val r = region.x2 * width
                        canvas.drawCircle(cx, cy, r, blockPaint)
                        canvas.drawCircle(cx, cy, r, borderPaint)
                    }
                    2 -> {
                        val rect = android.graphics.RectF(
                            (region.x1 - region.x2) * width,
                            (region.y1 - region.y2) * height,
                            (region.x1 + region.x2) * width,
                            (region.y1 + region.y2) * height
                        )
                        canvas.drawOval(rect, blockPaint)
                        canvas.drawOval(rect, borderPaint)
                    }
                }
            }

            val statusMsg = if (paused) {
                val remaining = ((pauseExpirationTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                "PAUSED — ${remaining}s remaining"
            } else if (regionsList.isEmpty()) {
                "InputBlocker: No zones configured"
            } else {
                "BLOCKING ${regionsList.size} zone(s)"
            }
            canvas.drawText(statusMsg, 10f, 50f, textPaint)
            if (count > 0) {
                canvas.drawText("Total: $count blocked", 10f, 90f, countPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!enabled || regionsList.isEmpty() || paused) return false

            val nx = event.x / width
            val ny = event.y / height

            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                for (region in regionsList) {
                    if (region.isExclude && isPointInRegion(nx, ny, region)) return false
                }

                for (region in regionsList) {
                    if (!region.isExclude && isPointInRegion(nx, ny, region)) {
                        val contactArea = event.pressure
                        val duration = event.eventTime - event.downTime

                        if (contactArea < region.minPressure || duration > region.maxDuration) {
                            blockCount.incrementAndGet()

                            val now = System.currentTimeMillis()
                            if (now - lastBlockLogTime > RATE_LIMIT_MS) {
                                lastBlockLogTime = now
                                val timeStr = SimpleDateFormat(
                                    "HH:mm:ss", Locale.getDefault()
                                ).format(Date())
                                addBlockEntry(
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

        private fun isPointInRegion(nx: Float, ny: Float, region: Region): Boolean {
            return when (region.type) {
                0 -> nx >= region.x1 && nx <= region.x2 && ny >= region.y1 && ny <= region.y2
                1 -> {
                    val dx = (nx - region.x1) * width
                    val dy = (ny - region.y1) * height
                    val r = region.x2 * width
                    (dx * dx + dy * dy) <= (r * r)
                }
                2 -> {
                    val dx = (nx - region.x1)
                    val dy = (ny - region.y1)
                    val rx = region.x2
                    val ry = region.y2
                    (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) <= 1.0f
                }
                else -> false
            }
        }
    }
}
