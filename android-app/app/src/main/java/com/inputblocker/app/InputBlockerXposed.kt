package com.inputblocker.app

import android.view.MotionEvent
import android.util.DisplayMetrics
import android.view.WindowManager
import android.app.Application
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.io.BufferedReader
import java.io.FileReader

class InputBlockerXposed : IXposedHookZygoteInit {
    companion object {
        private const val TAG = "InputBlocker-Xposed"
        @Volatile private var cachedRegions: List<Region> = emptyList()
        @Volatile private var cachedEnabled = true
        private var lastLoadTime = 0L
        private const val CACHE_TTL = 5000L // 5 seconds
        
        private var emergencyTouchStartTime = 0L
        private var isEmergencyGestureActive = false
        private var emergencyResetTriggered = false
        
        // Cached system objects to avoid reflection in the hot path
        private var cachedWindowManager: WindowManager? = null
        private var cachedWidth = 0
        private var cachedHeight = 0
        private var lastMetricsUpdate = 0L
        private const val METRICS_TTL = 30000L // 30 seconds
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        XposedBridge.log("InputBlocker Xposed module initialized")
        
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.input.InputDispatcher",
                null,
                "dispatchMotionLocked",
                android.os.IBinder::class.java,
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        updateConfigIfNeeded()
                        
                        // Use index access for performance; dispatchMotionLocked(IBinder, MotionEvent, ...)
                        // Usually MotionEvent is at index 1 or 2 depending on Android version
                        val motionEvent = param.args.getOrNull(1) as? MotionEvent 
                            ?: param.args.find { it is MotionEvent } as? MotionEvent 
                            ?: return
                        
                        val now = System.currentTimeMillis()
                        updateMetricsIfNeeded(now)
                        
                        if (cachedWidth <= 0 || cachedHeight <= 0) return
                        
                        val nx = motionEvent.x / cachedWidth
                        val ny = motionEvent.y / cachedHeight
                        
                        // Unified Emergency Reset Gesture (Top-left corner 5%)
                        val emergencyZoneSize = 0.05f
                        
                        if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                            if (nx < emergencyZoneSize && ny < emergencyZoneSize) {
                                emergencyTouchStartTime = now
                                isEmergencyGestureActive = true
                            }
                        } else if (motionEvent.action == MotionEvent.ACTION_MOVE) {
                            if (nx >= emergencyZoneSize || ny >= emergencyZoneSize) {
                                isEmergencyGestureActive = false
                            }
                        } else if (motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_CANCEL) {
                            isEmergencyGestureActive = false
                            emergencyTouchStartTime = 0L
                            emergencyResetTriggered = false
                        }
                        
                        if (isEmergencyGestureActive && !emergencyResetTriggered && (now - emergencyTouchStartTime > 3000)) {
                            triggerEmergencyReset()
                        }

                        if (!cachedEnabled) return
                        
                        // Test Mode
                        if (File("/data/adb/modules/inputblocker/config/test_mode").exists()) {
                            param.setResult(null)
                            return
                        }

                        // Region Processing
                        val currentRegions = cachedRegions
                        
                        // 1. Exclude Zones first
                        for (region in currentRegions) {
                            if (region.isExclude && isInsideRegion(nx, ny, region)) return
                        }
                        
                        // 2. Blocking Zones
                        for (region in currentRegions) {
                            if (!region.isExclude && isInsideRegion(nx, ny, region)) {
                                if (shouldBlockSurgically(motionEvent, region)) {
                                    logBlockedTouch(nx, ny, region)
                                    param.setResult(null) 
                                    return
                                }
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Error hooking InputDispatcher: ${e.message}")
        }
    }

    private fun isInsideRegion(nx: Float, ny: Float, region: Region): Boolean {
        return when (region.type) {
            0 -> nx >= region.x1 && nx <= region.x2 && ny >= region.y1 && ny <= region.y2
            1 -> { // Circle (x1, y1) = center, x2 = radius (normalized to width)
                val dx = (nx - region.x1) * cachedWidth
                val dy = (ny - region.y1) * cachedHeight
                val r = region.x2 * cachedWidth
                (dx * dx + dy * dy) <= (r * r)
            }
            2 -> { // Ellipse (x1, y1) = center, x2 = rx, y2 = ry
                val dx = (nx - region.x1) * cachedWidth
                val dy = (ny - region.y1) * cachedHeight
                val rx = region.x2 * cachedWidth
                val ry = region.y2 * cachedHeight
                (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) <= 1.0f
            }
            else -> false
        }
    }

    private fun shouldBlockSurgically(event: MotionEvent, region: Region): Boolean {
        val pressure = event.pressure
        val duration = event.eventTime - event.downTime
        
        // Block if pressure is low OR if it's a long-held ghost tap
        // (Ghost taps often have very low pressure or extremely long durations if the digitizer is failing)
        return pressure < region.minPressure || duration > 2000 // Treat > 2s as abnormal ghost
    }

    private fun triggerEmergencyReset() {
        try {
            File("/data/adb/modules/inputblocker/config/kill_switch").writeText("1")
            cachedEnabled = false
            emergencyResetTriggered = true
            XposedBridge.log("$TAG: Emergency reset triggered!")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to write kill_switch: ${e.message}")
        }
    }

    private fun updateMetricsIfNeeded(now: Long) {
        if (now - lastMetricsUpdate < METRICS_TTL && cachedWidth > 0) return
        
        try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
            val app = XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as? Application
            val wm = app?.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
            
            val metrics = DisplayMetrics()
            wm?.defaultDisplay?.getRealMetrics(metrics)
            
            if (metrics.widthPixels > 0) {
                cachedWidth = metrics.widthPixels
                cachedHeight = metrics.heightPixels
                cachedWindowManager = wm
                lastMetricsUpdate = now
            }
        } catch (e: Exception) {
            // Silently fail to avoid spamming logs on every touch if system is busy
        }
    }

    private fun logBlockedTouch(nx: Float, ny: Float, region: Region) {
        try {
            val logFile = File("/data/adb/modules/inputblocker/config/blocklog.txt")
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val entry = "$timestamp | X: ${"%.3f".format(nx)}, Y: ${"%.3f".format(ny)} | Region: [${region.x1}, ${region.y1}, ${region.x2}, ${region.y2}]\n"
            logFile.appendText(entry)
            
            if (logFile.length() > 102400) logFile.delete() // 100KB rotate
        } catch (e: Exception) { }
    }

    private fun getCurrentPackage(): String? {
        return try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
            val app = XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as? Application
            app?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun updateConfigIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastLoadTime < CACHE_TTL) return

        try {
            if (File("/data/adb/modules/inputblocker/config/kill_switch").exists()) {
                cachedEnabled = false
                lastLoadTime = now
                return
            }

            val pkg = getCurrentPackage()
            val configPath = if (pkg != null && File("/data/adb/modules/inputblocker/config/profiles/$pkg.conf").exists()) {
                "/data/adb/modules/inputblocker/config/profiles/$pkg.conf"
            } else {
                "/data/adb/modules/inputblocker/config/profiles/default.conf"
            }
            
            val file = File(configPath)
            if (!file.exists()) return
            
            val newRegions = mutableListOf<Region>()
            var newEnabled = true

            BufferedReader(FileReader(file)).use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("enabled=")) {
                        newEnabled = trimmed.substring(8) == "1"
                    } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        Region.fromString(trimmed)?.let { newRegions.add(it) }
                    }
                }
            }
            cachedRegions = newRegions
            cachedEnabled = newEnabled
            lastLoadTime = now
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Error loading config: ${e.message}")
        }
    }
}
