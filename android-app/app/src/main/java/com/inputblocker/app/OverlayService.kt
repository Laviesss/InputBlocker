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
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Serializable

class OverlayService : Service() {

    companion object {
        private const val TAG = "InputBlocker-Overlay"
        private const val CHANNEL_ID = "InputBlockerOverlay"
        private const val NOTIFICATION_ID = 1001
    }

    private var windowManager: WindowManager? = null
    private var touchBlockView: TouchBlockView? = null
    private val regions = mutableListOf<Region>()
    private var isEnabled = true
    private var forceSafeMode = false

    private var configReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "OverlayService starting...")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        loadConfig()
        createOverlayView()
        registerConfigReceiver()
        
        Log.i(TAG, "OverlayService ready - regions: ${regions.size}, enabled: $isEnabled")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "InputBlocker Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when InputBlocker is blocking touches"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = when {
            forceSafeMode -> "Safe Mode (Disabled) - Reset to enable"
            isEnabled -> "Blocking ${regions.size} region(s)"
            else -> "Blocking disabled"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("InputBlocker")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun loadConfig() {
        regions.clear()
        
        val configFile = File(InputBlockerServiceManager.getConfigFile(this))
        if (!configFile.exists()) {
            Log.w(TAG, "Config file not found")
            return
        }

        try {
            BufferedReader(FileReader(configFile)).use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.isEmpty() || trimmed.startsWith("#") -> return@forEach
                        trimmed.startsWith("enabled=") -> {
                            isEnabled = trimmed.substring(8) == "1"
                            Log.i(TAG, "Config enabled: $isEnabled")
                        }
                        trimmed.startsWith("force_safe_mode=") -> {
                            forceSafeMode = trimmed.substring(15) == "1"
                            if (forceSafeMode) {
                                isEnabled = false
                                Log.i(TAG, "Safe mode is active - blocking disabled")
                            }
                        }
                        else -> {
                            val parts = trimmed.split(",")
                            if (parts.size == 4) {
                                try {
                                    regions.add(Region(
                                        parts[0].trim().toInt(),
                                        parts[1].trim().toInt(),
                                        parts[2].trim().toInt(),
                                        parts[3].trim().toInt()
                                    ))
                                } catch (e: NumberFormatException) {
                                    Log.w(TAG, "Invalid region line: $trimmed")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config", e)
        }
        
        Log.i(TAG, "Loaded ${regions.size} regions")
    }

    private fun createOverlayView() {
        if (windowManager == null) {
            Log.e(TAG, "WindowManager is null")
            return
        }

        touchBlockView = TouchBlockView(this)
        
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
        
        touchBlockView?.apply {
            setRegions(regions)
            setBlockingEnabled(isEnabled && !forceSafeMode)
        }

        try {
            windowManager?.addView(touchBlockView, params)
            Log.i(TAG, "Overlay view added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun registerConfigReceiver() {
        configReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                Log.i(TAG, "Received broadcast: $action")
                
                when (action) {
                    "com.inputblocker.RELOAD" -> reloadConfig()
                    "com.inputblocker.DISABLE" -> disableBlocking()
                    "com.inputblocker.ENABLE" -> {
                        val forceSafe = intent.getBooleanExtra("force_safe", true)
                        enableBlocking(forceSafe)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.inputblocker.RELOAD")
            addAction("com.inputblocker.DISABLE")
            addAction("com.inputblocker.ENABLE")
        }
        
        try {
            registerReceiver(configReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver", e)
        }
    }

    private fun reloadConfig() {
        loadConfig()
        
        touchBlockView?.apply {
            setRegions(regions)
            setBlockingEnabled(isEnabled && !forceSafeMode)
        }
        
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification())
        
        Log.i(TAG, "Config reloaded")
    }

    private fun disableBlocking() {
        isEnabled = false
        
        touchBlockView?.setBlockingEnabled(false)
        
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification())
        
        Toast.makeText(this, "InputBlocker disabled", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Blocking disabled")
    }

    private fun enableBlocking(clearSafeMode: Boolean) {
        if (clearSafeMode) {
            forceSafeMode = false
            clearSafeModeFlag()
        }
        
        isEnabled = true
        
        touchBlockView?.setBlockingEnabled(true)
        
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification())
        
        Toast.makeText(this, "InputBlocker enabled", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Blocking enabled")
    }

    private fun clearSafeModeFlag() {
        try {
            val configFile = File(InputBlockerServiceManager.getConfigFile(this))
            if (configFile.exists()) {
                val content = configFile.readLines()
                    .filter { !it.startsWith("force_safe_mode=") }
                    .joinToString("\n")
                configFile.writeText(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear safe mode flag", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")
        
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
        
        configReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) { }
        }
        
        touchBlockView?.let { view ->
            windowManager?.removeView(view)
        }
        
        Log.i(TAG, "OverlayService destroyed")
    }

    @Nullable
    override fun onBind(intent: Intent?): IBinder? = null

    data class Region(
        var x1: Int = 0,
        var y1: Int = 0,
        var x2: Int = 0,
        var y2: Int = 0
    ) : Serializable

    inner class TouchBlockView(context: Context) : View(context) {
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
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
        }
        
        private val safeModePaint = Paint().apply {
            color = Color.parseColor("#FFFF00")
            textSize = 48f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        
        private val regionsList = mutableListOf<Region>()
        private var enabled = true

        fun setRegions(list: List<Region>) {
            regionsList.clear()
            regionsList.addAll(list)
            invalidate()
        }

        fun setBlockingEnabled(enabled: Boolean) {
            this.enabled = enabled
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (!enabled) {
                canvas.drawColor(Color.TRANSPARENT)
                
                if (forceSafeMode) {
                    canvas.drawText("SAFE MODE", width / 2f, 100f, safeModePaint)
                    canvas.drawText("(Blocking Disabled)", width / 2f, 150f, safeModePaint)
                }
                return
            }

            for (region in regionsList) {
                val rect = android.graphics.RectF(
                    region.x1.toFloat(), region.y1.toFloat(),
                    region.x2.toFloat(), region.y2.toFloat()
                )
                
                canvas.drawRect(rect, blockPaint)
                canvas.drawRect(rect, borderPaint)
            }

            val text = if (regionsList.isNotEmpty()) {
                "BLOCKED: ${regionsList.size} region(s)"
            } else {
                "No regions configured"
            }
            canvas.drawText(text, 10f, 50f, textPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!enabled || regionsList.isEmpty()) {
                return false
            }

            val x = event.x
            val y = event.y

            if (event.action == MotionEvent.ACTION_DOWN ||
                event.action == MotionEvent.ACTION_MOVE) {
                
                for (region in regionsList) {
                    if (x >= region.x1 && x <= region.x2 &&
                        y >= region.y1 && y <= region.y2) {
                        
                        Log.d(TAG, "Blocked touch at ($x,$y)")
                        return true
                    }
                }
            }

            return false
        }
    }
}
