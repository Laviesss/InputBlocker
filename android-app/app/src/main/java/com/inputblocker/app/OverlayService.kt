package com.inputblocker.app

import com.inputblocker.shared.Region
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
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import java.lang.ref.WeakReference
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.ArrayList
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

class OverlayService : Service() {

    companion object {
        private const val TAG = "InputBlocker-Overlay"
        private const val CHANNEL_ID = "InputBlockerOverlay"
        private const val NOTIFICATION_ID = 1001
        
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
    private var currentProfile = "default"
    private var lastForegroundApp: String? = null
    private var blockingExpirationTime = 0L

    private var configReceiver: BroadcastReceiver? = null
    private var isRunning = true

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "OverlayService starting...")
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        loadConfig()
        createOverlayView()
        registerConfigReceiver()
        startExpirationTimer()
    }

    private fun startExpirationTimer() {
        Thread {
            while (isRunning) {
                try {
                    Thread.sleep(2000)
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
                    
                    if (isEnabled && blockingExpirationTime > 0 && System.currentTimeMillis() > blockingExpirationTime) {
                        disableBlocking()
                        runOnUiThread {
                            Toast.makeText(this, "Blocking session expired", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Timer error", e)
                }
            }
        }.start()
    }

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "InputBlocker Active", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, NotificationReceiver::class.java).apply { action = NotificationReceiver.ACTION_TOGGLE_BLOCKING }
        val togglePendingIntent = PendingIntent.getBroadcast(this, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val safeIntent = Intent(this, NotificationReceiver::class.java).apply { action = NotificationReceiver.ACTION_SAFE_MODE }
        val safePendingIntent = PendingIntent.getBroadcast(this, 2, safeIntent, PendingIntent.FLAG_IMMUTABLE)

        val statusText = when {
            forceSafeMode -> "Safe Mode (Disabled)"
            isEnabled -> "Blocking ${regions.size} region(s)"
            else -> "Blocking disabled"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("InputBlocker")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Toggle", togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Safe Mode", safePendingIntent)
            .build()
    }

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
    }

    private fun emergencyReset() {
        InputBlockerServiceManager.enableSafeMode(this)
        reloadConfig()
        runOnUiThread { Toast.makeText(this, "EMERGENCY RESET triggered", Toast.LENGTH_LONG).show() }
    }

    private fun createOverlayView() {
        if (windowManager == null || isLsposedModeFromConfig()) return
        
        touchBlockView = TouchBlockView(this)
        touchBlockView?.setService(this)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        
        touchBlockView?.apply {
            setRegions(regions)
            setBlockingEnabled(isEnabled && !forceSafeMode)
        }

        try {
            windowManager?.addView(touchBlockView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
        }
    }

    private fun registerConfigReceiver() {
        configReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.inputblocker.RELOAD" -> reloadConfig()
                    "com.inputblocker.DISABLE" -> disableBlocking()
                    "com.inputblocker.ENABLE" -> enableBlocking(intent.getBooleanExtra("force_safe", true))
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.inputblocker.RELOAD")
            addAction("com.inputblocker.DISABLE")
            addAction("com.inputblocker.ENABLE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(configReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(configReceiver, filter)
        }
    }

    private fun reloadConfig() {
        loadConfig()
        runOnUiThread {
            touchBlockView?.apply {
                setRegions(regions)
                setBlockingEnabled(isEnabled && !forceSafeMode)
            }
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification())
    }

    private fun disableBlocking() {
        isEnabled = false
        runOnUiThread { touchBlockView?.setBlockingEnabled(false) }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification())
    }

    private fun enableBlocking(clearSafeMode: Boolean) {
        if (clearSafeMode) forceSafeMode = false
        isEnabled = true
        runOnUiThread { touchBlockView?.setBlockingEnabled(true) }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification())
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
        configReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) {} }
        touchBlockView?.let { windowManager?.removeView(it) }
    }

    @Nullable override fun onBind(intent: Intent?): IBinder? = null

    private fun runOnUiThread(action: Runnable) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }

    inner class TouchBlockView(context: Context) : View(context) {
        private var serviceRef = WeakReference<OverlayService>(null)
        fun setService(service: OverlayService) { serviceRef = WeakReference(service) }

        private val blockPaint = Paint().apply { color = Color.parseColor("#1A00FF00"); style = Paint.Style.FILL }
        private val borderPaint = Paint().apply { color = Color.parseColor("#00FF00"); style = Paint.Style.STROKE; strokeWidth = 4f }
        private val textPaint = Paint().apply { color = Color.parseColor("#00FF00"); textSize = 36f; isFakeBoldText = true }
        private val regionsList = mutableListOf<Region>()
        private var enabled = true

        fun setRegions(list: List<Region>) { regionsList.clear(); regionsList.addAll(list); invalidate() }
        fun setBlockingEnabled(enabled: Boolean) { this.enabled = enabled; invalidate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!enabled) return

            for (region in regionsList) {
                when (region.type) {
                    0 -> {
                        val rect = android.graphics.RectF(region.x1 * width, region.y1 * height, region.x2 * width, region.y2 * height)
                        canvas.drawRect(rect, blockPaint); canvas.drawRect(rect, borderPaint)
                    }
                    1 -> {
                        val cx = region.x1 * width; val cy = region.y1 * height; val r = region.x2 * width
                        canvas.drawCircle(cx, cy, r, blockPaint); canvas.drawCircle(cx, cy, r, borderPaint)
                    }
                    2 -> {
                        val rect = android.graphics.RectF((region.x1 - region.x2) * width, (region.y1 - region.y2) * height, (region.x1 + region.x2) * width, (region.y1 + region.y2) * height)
                        canvas.drawOval(rect, blockPaint); canvas.drawOval(rect, borderPaint)
                    }
                }
            }
            canvas.drawText("BLOCKED: ${regionsList.size}", 10f, 50f, textPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!enabled || regionsList.isEmpty()) return false
            
            val nx = event.x / width
            val ny = event.y / height
            
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                // Whitelist check first
                for (region in regionsList) {
                    if (region.isExclude && isPointInRegion(nx, ny, region)) return false
                }
                
                // Surgical blocking check
                for (region in regionsList) {
                    if (!region.isExclude && isPointInRegion(nx, ny, region)) {
                        val pressure = event.pressure
                        val duration = event.eventTime - event.downTime
                        
                        if (pressure < region.minPressure || duration > region.maxDuration) {
                            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            addBlockEntry(BlockLogActivity.BlockEntry(timeStr, "Blocked at (%.2f, %.2f)".format(nx, ny)))
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
                1 -> { val dx = (nx - region.x1) * width; val dy = (ny - region.y1) * height; val r = region.x2 * width; (dx * dx + dy * dy) <= (r * r) }
                2 -> { val dx = (nx - region.x1); val dy = (ny - region.y1); val rx = region.x2; val ry = region.y2; (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) <= 1.0f }
                else -> false
            }
        }
    }
}
