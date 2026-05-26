package com.inputblocker.app

import com.inputblocker.shared.Region
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class InputBlockerXposed : IXposedHookZygoteInit {
    companion object {
        private const val TAG = "InputBlocker-Xposed"
        @Volatile private var cachedRegions = ArrayList<Region>()
        @Volatile private var cachedEnabled = true
        @Volatile private var testModeActive = false
        private var lastLoadTime = 0L
        private const val CACHE_TTL = 10000L // 10 seconds for standard config
        
        private var cachedWidth = 0
        private var cachedHeight = 0
        private var lastMetricsUpdate = 0L
        private const val METRICS_TTL = 60000L // 60 seconds
        private var cachedWindowManager: WindowManager? = null

        // Crash Protection: Write a flag if we fail in the hot path
        private val CRASH_FLAG_PATH = "/data/adb/modules/inputblocker/config/crash_detected"

        // --- Async Logging System (Only in system_server) ---
        private val logQueue = LinkedBlockingQueue<String>(500)
        private var loggerStarted = false

        private fun startLogger() {
            if (loggerStarted) return
            loggerStarted = true
            Thread({
                while (true) {
                    try {
                        val entry = logQueue.take()
                        val parts = entry.split("|", limit = 2)
                        if (parts.size == 2) {
                            val file = File(parts[0])
                            file.appendText("${parts[1]}\n")
                            if (file.length() > 204800) file.delete() // 200KB rotation
                        }
                    } catch (e: Exception) {}
                }
            }, "InputBlocker-Logger").start()
        }
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
                        try {
                            val now = System.currentTimeMillis()
                            updateConfigIfNeeded(now)
                            
                            if (!cachedEnabled) return

                            // Use index access for performance; dispatchMotionLocked(IBinder, MotionEvent, ...)
                            val motionEvent = param.args.getOrNull(1) as? MotionEvent ?: return
                            
                            val startNano = System.nanoTime()
                            updateMetricsIfNeeded(now)
                            
                            if (cachedWidth <= 0 || cachedHeight <= 0) return
                            
                            val nx = motionEvent.x / cachedWidth
                            val ny = motionEvent.y / cachedHeight
                            
                            if (testModeActive) {
                                logLatency(System.nanoTime() - startNano)
                                param.setResult(null)
                                return
                            }
                            
                            val regions = cachedRegions
                            val size = regions.size
                            
                            // 1. Priority: Exclude Zones (Whitelist)
                            for (i in 0 until size) {
                                val region = regions[i]
                                if (region.isExclude && isInsideRegion(nx, ny, region)) {
                                    logLatency(System.nanoTime() - startNano)
                                    return
                                }
                            }
                            
                            // 2. Surgical Blocking Zones
                            for (i in 0 until size) {
                                val region = regions[i]
                                if (!region.isExclude && isInsideRegion(nx, ny, region)) {
                                    if (shouldBlockSurgically(motionEvent, region)) {
                                        logBlockedTouch(nx, ny, motionEvent.pressure, (motionEvent.eventTime - motionEvent.downTime), region)
                                        logLatency(System.nanoTime() - startNano)
                                        param.setResult(null) 
                                        return
                                    }
                                }
                            }
                            logLatency(System.nanoTime() - startNano)
                        } catch (t: Throwable) {
                            // Senior Engineering: Fail-safe crash detection
                            handleHookCrash(t)
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
        
        // Block if pressure is low OR if the tap duration exceeds the region's specified threshold
        // (Ghost taps often have very low pressure or abnormally long durations)
        return pressure < region.minPressure || duration > region.maxDuration
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

    private fun logLiveEvent(type: String, nx: Float, ny: Float) {
        try {
            android.util.Log.i("InputBlockerLive", "$type|$nx|$ny")
        } catch (e: Exception) { }
    }

    private fun logLatency(nanos: Long) {
        logQueue.offer("/data/adb/modules/inputblocker/config/latency.log|$nanos")
        startLogger()
    }

    private fun logBlockedTouch(nx: Float, ny: Float, pressure: Float, duration: Long, region: Region) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = "$timestamp | X: ${"%.3f".format(nx)}, Y: ${"%.3f".format(ny)} | P: ${"%.3f".format(pressure)}, D: ${duration}ms | Region: [${region.x1}, ${region.y1}, ${region.x2}, ${region.y2}]"
        logQueue.offer("/data/adb/modules/inputblocker/config/blocklog.txt|$entry")
        startLogger()
    }

    private fun handleHookCrash(t: Throwable) {
        XposedBridge.log("$TAG CRITICAL CRASH: ${t.message}")
        try {
            File(CRASH_FLAG_PATH).writeText("1")
        } catch (e: Exception) {}
    }

    private fun updateConfigIfNeeded(now: Long) {
        if (now - lastLoadTime < CACHE_TTL) return

        try {
            // Centralized throttled I/O
            val killSwitch = File("/data/adb/modules/inputblocker/config/kill_switch")
            if (killSwitch.exists()) {
                cachedEnabled = false
                lastLoadTime = now
                return
            }

            testModeActive = File("/data/adb/modules/inputblocker/config/test_mode").exists()

            val pkg = currentPackageName
            val configPath = if (pkg != null && File("/data/adb/modules/inputblocker/config/profiles/$pkg.conf").exists()) {
                "/data/adb/modules/inputblocker/config/profiles/$pkg.conf"
            } else {
                "/data/adb/modules/inputblocker/config/profiles/default.conf"
            }
            
            val file = File(configPath)
            if (!file.exists()) {
                lastLoadTime = now
                return
            }
            
            val newRegions = ArrayList<Region>()
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

    private val currentPackageName: String?
        get() = try {
            val activityThread = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null),
                "currentActivityThread"
            )
            XposedHelpers.callMethod(activityThread, "currentPackageName") as? String
        } catch (_: Exception) {
            null
        }
}
