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
import java.lang.ref.WeakReference
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
    private var isDetectionMode = false
    private val touchHeatmap = mutableMapOf<Pair<Int, Int>, Int>()
    private var detectionStartTime = 0L

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

    private fun isLsposedModeFromConfig(): Boolean {
        val configFile = File(InputBlockerServiceManager.getConfigFile(this))
        if (!configFile.exists()) return false
        return try {
            configFile.readLines().any { it.trim().startsWith("lsposed_mode=1") }
        } catch (e: Exception) {
            false
        }
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

        // Don't create overlay if LSPosed mode is enabled
        if (isLsposedModeFromConfig()) {
            Log.i(TAG, "LSPosed mode active - skipping overlay view")
            return
        }
        
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
                    "com.inputblocker.START_DETECTION" -> startDetection()
                    "com.inputblocker.STOP_DETECTION" -> stopDetection()
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction("com.inputblocker.RELOAD")
            addAction("com.inputblocker.DISABLE")
            addAction("com.inputblocker.ENABLE")
            addAction("com.inputblocker.START_DETECTION")
            addAction("com.inputblocker.STOP_DETECTION")
        }
        
        try {
            registerReceiver(configReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver", e)
        }
    }


    private fun stopDetection() {
        isDetectionMode = false
        val detectedRegions = processHeatmap()
        
        val intent = Intent("com.inputblocker.DETECTION_RESULTS")
        intent.putExtra("regions", ArrayList(detectedRegions))
        sendBroadcast(intent)
        
        touchHeatmap.clear()
        Log.i(TAG, "Detection stopped. Found ${detectedRegions.size} regions")
        
        // Restore blocking state
        touchBlockView?.setBlockingEnabled(isEnabled && !forceSafeMode)
    }

    private fun startDetection() {
        // We don't set isDetectionMode = true here yet to avoid recording "garbage" 
        // during the screen cycle transition.
        
        Thread {
            try {
                Log.i(TAG, "Initiating detection sequence...")
                
                // 1. Disable lock screen
                Runtime.getRuntime().exec(arrayOf("su", "-c", "locksettings set-disabled true"))
                Thread.sleep(500)
                
                // 2. Force screen off
                Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 26"))
                Thread.sleep(1000)
                
                // 3. Force screen on
                Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent KEYCODE_WAKEUP"))
                Thread.sleep(1000)
                
                // 4. NOW start the actual detection period
                Log.i(TAG, "Screen cycled. Starting 10s detection window...")
                isDetectionMode = true
                touchHeatmap.clear()
                detectionStartTime = System.currentTimeMillis()
                touchBlockView?.setBlockingEnabled(true)
                
                // 5. Wait for the detection duration
                Thread.sleep(10000) 
                
                // 6. Automatically stop and process results
                stopDetection()
                
            } catch (e: Exception) {
                Log.e(TAG, "Detection sequence failed: ${e.message}")
                isDetectionMode = false
                touchBlockView?.setBlockingEnabled(isEnabled && !forceSafeMode)
            }
        }.start()
    }

    private fun stopDetection() {
        isDetectionMode = false
        val detectedRegions = processHeatmap()
        
        // 2. Re-enable the lock screen
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "locksettings set-disabled false"))
            Log.i(TAG, "Lock screen re-enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-enable lock screen: ${e.message}")
        }

        val intent = Intent("com.inputblocker.DETECTION_RESULTS")
        intent.putExtra("regions", ArrayList(detectedRegions))
        sendBroadcast(intent)
        
        touchHeatmap.clear()
        Log.i(TAG, "Detection stopped. Found ${detectedRegions.size} regions")
        
        // Restore blocking state
        touchBlockView?.setBlockingEnabled(isEnabled && !forceSafeMode)
    }

    private fun processHeatmap(): List<Region> {
        if (touchHeatmap.isEmpty()) return emptyList()
        
        // DBSCAN-inspired Clustering Algorithm
        // Parameters: eps (distance threshold), minPts (min points to form a cluster)
        val eps = 80.0 // Max distance between points to be considered neighbors
        val minPts = 3  // Minimum points to form a cluster
        
        val hotspots = touchHeatmap.filter { it.value >= minPts }.keys.toList()
        if (hotspots.isEmpty()) return emptyList()
        
        val visited = mutableSetOf<Pair<Int, Int>>()
        val clusters = mutableListOf<MutableList<Pair<Int, Int>>>()
        
        for (point in hotspots) {
            if (point in visited) continue
            
            // Find neighbors
            val neighbors = hotspots.filter { other ->
                val dist = Math.sqrt(Math.pow((other.first - point.first).toDouble(), 2.0) + 
                                     Math.pow((other.second - point.second).toDouble(), 2.0))
                dist <= eps
            }
            
            if (neighbors.size >= minPts) {
                // Start a new cluster
                val cluster = mutableListOf<Pair<Int, Int>>()
                val queue = mutableListOf<Pair<Int, Int>>()
                queue.add(point)
                visited.add(point)
                
                var qIdx = 0
                while (qIdx < queue.size) {
                    val p = queue[qIdx++]
                    cluster.add(p)
                    
                    val pNeighbors = hotspots.filter { other ->
                        val dist = Math.sqrt(Math.pow((other.first - p.first).toDouble(), 2.0) + 
                                             Math.pow((other.second - p.second).toDouble(), 2.0))
                        dist <= eps
                    }
                    
                    for (pn in pNeighbors) {
                        if (pn !in visited) {
                            visited.add(pn)
                            queue.add(pn)
                        }
                    }
                }
                clusters.add(cluster)
            } else {
                visited.add(point)
            }
        }
        
        // Convert clusters to Bounding Box Regions
        return clusters.map { cluster ->
            var x1 = Int.MAX_VALUE
            var y1 = Int.MAX_VALUE
            var x2 = Int.MIN_VALUE
            var y2 = Int.MIN_VALUE
            
            for (p in cluster) {
                x1 = minOf(x1, p.first)
                y1 = minOf(y1, p.second)
                x2 = maxOf(x2, p.first)
                y2 = maxOf(y2, p.second)
            }
            
            // Add padding to the detected region for safety
            Region(x1 - 40, y1 - 40, x2 + 40, y2 + 40)
        }.filter { region -> 
            // Filter out regions that are too tiny to be useful
            (region.x2 - region.x1) > 20 && (region.y2 - region.y1) > 20 
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
                    .filter { !it.startsWith("force_safe_mode=") && it.isNotBlank() }
                    .joinToString("\n")
                configFile.writeText(content + "\n")
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

    inner class TouchBlockView(context: Context) : View(context) {
        private var serviceRef = WeakReference<OverlayService>(null)

        fun setService(service: OverlayService) {
            serviceRef = WeakReference(service)
        }

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
            
            val service = serviceRef.get()
            val forceSafeMode = service?.let { 
                InputBlockerServiceManager.getModulePath(it).let { path ->
                    val configFile = File(path, "config/blocked_regions.conf")
                    if (configFile.exists()) {
                        configFile.readLines().any { line -> line.startsWith("force_safe_mode=1") }
                    } else false
                }
            } ?: false

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
            if (isDetectionMode) {
                val x = event.x.toInt()
                val y = event.y.toInt()
                
                // Quantize coordinates to a 20x20 grid for clustering
                val gridX = (x / 20) * 20
                val gridY = (y / 20) * 20
                val point = Pair(gridX, gridY)
                
                touchHeatmap[point] = touchHeatmap.getOrDefault(point, 0) + 1
                Log.d(TAG, "Detected touch at ($x,$y) -> Grid($gridX,$gridY)")
                
                return true // Block everything during detection
            }

            if (!enabled || regionsList.isEmpty()) {
                return false
            }
            val x = event.x
            val y = event.y
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                for (region in regionsList) {
                    if (x >= region.x1 && x <= region.x2 && y >= region.y1 && y <= region.y2) {
                        Log.d(TAG, "Blocked touch at ($x,$y)")
                        return true
                    }
                }
            }
            return false
        }
    }
}
