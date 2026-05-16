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
import java.util.ArrayList
import java.util.concurrent.ConcurrentLinkedQueue

class OverlayService : Service() {

class OverlayService : Service() {

    companion object {
        private const val TAG = "InputBlocker-Overlay"
        private const val CHANNEL_ID = "InputBlockerOverlay"
        private const val NOTIFICATION_ID = 1001
        
        private var globalBlockLog = ConcurrentLinkedQueue<BlockLogActivity.BlockEntry>()
        
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
    private val regions = mutableListOf<Region>()
    private var isEnabled = true
    private var forceSafeMode = false
    private var isDetectionMode = false
    private val touchHeatmap = mutableMapOf<Pair<Float, Float>, Int>()
    private var detectionStartTime = 0L
    
    // Real-time Block Log
    private val blockLog = ConcurrentLinkedQueue<BlockLogActivity.BlockEntry>()

    private var currentProfile = "default"

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
        
        Log.i(TAG, "OverlayService ready - regions: ${regions.size}, enabled: $isEnabled")
    }

    private fun startExpirationTimer() {
        Thread {
            while (isRunning) {
                try {
                    Thread.sleep(60000) // Check every minute
                    if (isEnabled && blockingExpirationTime > 0 && System.currentTimeMillis() > blockingExpirationTime) {
                        Log.i(TAG, "Blocking session expired")
                        disableBlocking()
                        runOnUiThread {
                            Toast.makeText(this, "Blocking session expired", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Expiration timer error", e)
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

        // Quick Action Intents
        val toggleIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_TOGGLE_BLOCKING
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 1, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val safeIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_SAFE_MODE
        }
        val safePendingIntent = PendingIntent.getBroadcast(
            this, 2, safeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val syncIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_SYNC
        }
        val syncPendingIntent = PendingIntent.getBroadcast(
            this, 3, syncIntent,
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
            .addAction(R.drawable.ic_power_off, "Toggle", togglePendingIntent)
            .addAction(R.drawable.ic_shield, "Safe Mode", safePendingIntent)
            .addAction(R.drawable.ic_sync, "Sync", syncPendingIntent)
            .build()
    }


    private fun loadConfig() {
        regions.clear()
        
        val configFile = File(InputBlockerServiceManager.getConfigFile(this, currentProfile))
        if (!configFile.exists()) {
            Log.w(TAG, "Config file not found for profile: $currentProfile")
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
                                         parts[0].trim().toFloat(),
                                         parts[1].trim().toFloat(),
                                         parts[2].trim().toFloat(),
                                         parts[3].trim().toFloat()
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

    private fun emergencyReset() {
        Log.i(TAG, "Emergency Reset Triggered!")
        try {
            // 1. Force disable and set safe mode in the config file
            InputBlockerServiceManager.runRootCommand("sed -i 's/^enabled=.*/enabled=0/' /data/adb/modules/inputblocker/config/blocked_regions.conf")
            InputBlockerServiceManager.runRootCommand("sed -i 's/^force_safe_mode=.*/force_safe_mode=1/' /data/adb/modules/inputblocker/config/blocked_regions.conf")
            
            // 2. Trigger immediate reload
            val intent = Intent("com.inputblocker.RELOAD")
            sendBroadcast(intent)
            
            runOnUiThread {
                Toast.makeText(this, "EMERGENCY RESET: Blocking disabled", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Emergency reset failed", e)
        }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(configReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(configReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver", e)
        }
    }

    private fun runRootCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root command failed: $command - ${e.message}")
            false
        }
    }

    private fun startDetection() {
        Thread {
            try {
                Log.i(TAG, "Initiating detection sequence...")
                
                // 1. Disable lock screen
                if (!runRootCommand("locksettings set-disabled true")) {
                    Log.w(TAG, "Failed to disable lock screen, continuing anyway...")
                }
                Thread.sleep(500)
                
                // 2. Force screen off
                if (!runRootCommand("input keyevent 26")) {
                    Log.w(TAG, "Failed to force screen off, continuing anyway...")
                }
                Thread.sleep(1000)
                
                // 3. Force screen on
                if (!runRootCommand("input keyevent KEYCODE_WAKEUP")) {
                    Log.w(TAG, "Failed to force screen on, continuing anyway...")
                }
                Thread.sleep(1000)
                
                // 4. NOW start the actual detection period
                Log.i(TAG, "Screen cycled. Starting 10s detection window...")
                isDetectionMode = true
                touchHeatmap.clear()
                detectionStartTime = System.currentTimeMillis()
                runOnUiThread {
                    touchBlockView?.setBlockingEnabled(true)
                }
                
                // 5. Wait for the detection duration
                Thread.sleep(10000) 
                
                // 6. Automatically stop and process results
                stopDetection()
                
            } catch (e: Exception) {
                Log.e(TAG, "Detection sequence failed: ${e.message}")
                isDetectionMode = false
                runOnUiThread {
                    touchBlockView?.setBlockingEnabled(isEnabled && !forceSafeMode)
                }
            }
        }.start()
    }

    private fun stopDetection() {
        isDetectionMode = false
        val detectedRegions = processHeatmap()
        
        // Re-enable the lock screen
        if (!runRootCommand("locksettings set-disabled false")) {
            Log.e(TAG, "Failed to re-enable lock screen")
        } else {
            Log.i(TAG, "Lock screen re-enabled")
        }

        val intent = Intent("com.inputblocker.DETECTION_RESULTS")
        intent.putExtra("regions", ArrayList(detectedRegions))
        sendBroadcast(intent)
        
        touchHeatmap.clear()
        Log.i(TAG, "Detection stopped. Found ${detectedRegions.size} regions")
        
        // Restore blocking state
        runOnUiThread {
            touchBlockView?.setBlockingEnabled(isEnabled && !forceSafeMode)
        }
    }

    private fun processHeatmap(): List<Region> {
        if (touchHeatmap.isEmpty()) return emptyList()
        
        // DBSCAN-inspired Clustering Algorithm
        val eps = 0.08f // 8% of screen distance
        val minPts = 3  
        
        val hotspots = touchHeatmap.keys.toList()
        if (hotspots.isEmpty()) return emptyList()
        
        val visited = mutableSetOf<Pair<Float, Float>>()
        val clusters = mutableListOf<MutableList<Pair<Float, Float>>>()
        
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
                val cluster = mutableListOf<Pair<Float, Float>>()
                val queue = mutableListOf<Pair<Float, Float>>()
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
        
        return clusters.map { cluster ->
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            
            for (p in cluster) {
                minX = minOf(minX, p.first)
                minY = minOf(minY, p.second)
                maxX = maxOf(maxX, p.first)
                maxY = maxOf(maxY, p.second)
            }
            
            // Add padding
            val padding = 0.02f
            Region(
                (minX - padding).coerceIn(0f, 1f),
                (minY - padding).coerceIn(0f, 1f),
                (maxX + padding).coerceIn(0f, 1f),
                (maxY + padding).coerceIn(0f, 1f)
            )
        }.filter { region -> 
            // Filter out regions that are too tiny (less than 1% of screen)
            (region.x2 - region.x1) > 0.01f && (region.y2 - region.y1) > 0.01f 
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
        
        Log.i(TAG, "Config reloaded")
    }

    private fun disableBlocking() {
        isEnabled = false
        
        runOnUiThread {
            touchBlockView?.setBlockingEnabled(false)
            Toast.makeText(this, "InputBlocker disabled", Toast.LENGTH_SHORT).show()
        }
        
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification())
        
        Log.i(TAG, "Blocking disabled")
    }

    private fun enableBlocking(clearSafeMode: Boolean, durationMs: Long = 0L) {
        if (clearSafeMode) {
            forceSafeMode = false
            clearSafeModeFlag()
        }
        
        isEnabled = true
        
        if (durationMs > 0) {
            blockingExpirationTime = System.currentTimeMillis() + durationMs
            Log.i(TAG, "Blocking enabled for ${durationMs/1000}s")
        } else {
            blockingExpirationTime = 0L
        }
        
        runOnUiThread {
            touchBlockView?.setBlockingEnabled(true)
            Toast.makeText(this, "InputBlocker enabled", Toast.LENGTH_SHORT).show()
        }
        
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification())
        
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
                    "CHANGE_PROFILE" -> {
                        val profile = intent.getStringExtra("profile") ?: "default"
                        currentProfile = profile
                        reloadConfig()
                        Log.i(TAG, "Profile changed to: $profile")
                    }
                    else -> Log.d(TAG, "Unhandled onStartCommand action: $action")
                }
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
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

    private fun runOnUiThread(action: Runnable) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post(action)
    }

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
        private var gestureStartTime = 0L
        private var isGestureActive = false

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
            val forceSafeModeVal = service?.forceSafeMode ?: false

            if (!enabled) {
                canvas.drawColor(Color.TRANSPARENT)
                if (forceSafeModeVal) {
                    canvas.drawText("SAFE MODE", width / 2f, 100f, safeModePaint)
                    canvas.drawText("(Blocking Disabled)", width / 2f, 150f, safeModePaint)
                }
                return
            }

                for (region in regionsList) {
                    val rect = android.graphics.RectF(
                        region.x1 * width, region.y1 * height,
                        region.x2 * width, region.y2 * height
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
            val service = serviceRef.get()
            
            // --- EMERGENCY RESET GESTURE DETECTION ---
            val pointerCount = event.pointerCount
            if (pointerCount >= 3) {
                var allInTopLeft = true
                for (i in 0 until pointerCount) {
                    val x = event.getX(i)
                    val y = event.getY(i)
                    if (x > width * 0.1f || y > height * 0.1f) {
                        allInTopLeft = false
                        break
                    }
                }
                
                if (allInTopLeft) {
                    if (!isGestureActive) {
                        gestureStartTime = System.currentTimeMillis()
                        isGestureActive = true
                    } else if (System.currentTimeMillis() - gestureStartTime >= 3000) {
                        service?.emergencyReset()
                        isGestureActive = false
                        return true
                    }
                } else {
                    isGestureActive = false
                }
            } else {
                isGestureActive = false
            }
            // -----------------------------------------

            if (service?.isDetectionMode == true) {
                val x = event.x
                val y = event.y
                
                // Use normalized coordinates for heatmap
                val nx = (x / width).coerceIn(0f, 1f)
                val ny = (y / height).coerceIn(0f, 1f)
                
                // Quantize to 0.01 grid
                val qnx = (Math.round(nx * 100) / 100f)
                val qny = (Math.round(ny * 100) / 100f)
                val point = Pair(qnx, qny)
                
                service.touchHeatmap[point] = service.touchHeatmap.getOrDefault(point, 0) + 1
                Log.d(TAG, "Detected touch at ($x,$y) -> Normalized($qnx,$qny)")
                
                return true // Block everything during detection
            }

            if (!enabled || regionsList.isEmpty()) {
                return false
            }
            val x = event.x
            val y = event.y
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                for (region in regionsList) {
                    val nx = x / width
                    val ny = y / height
                    if (nx >= region.x1 && nx <= region.x2 && ny >= region.y1 && ny <= region.y2) {
                        val timestamp = java.text.DateFormat.//S...
                        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        val logMsg = "Blocked touch at (${String.format("%.2f", nx)}, ${String.format("%.2f", ny)}) - Region #${regionsList.indexOf(region) + 1}"
                        
                        BlockLogActivity.addBlockEntry(BlockLogActivity.BlockEntry(timeStr, logMsg))
                        Log.d(TAG, "Blocked touch at ($x,$y) -> Normalized($nx,$ny)")
                        return true
                    }

                }
            }
            return false
        }
    }
}
