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
import java.util.concurrent.LinkedBlockingQueue

class InputBlockerXposed : IXposedHookZygoteInit {
    companion object {
        private const val TAG = "InputBlocker-Hook"
        @Volatile private var cachedRegions: List<Region> = emptyList()
        @Volatile private var cachedEnabled = true
        @Volatile private var cachedPaused = false
        @Volatile private var cachedLsposedMode = true
        @Volatile private var testModeActive = false
        private var lastLoadTime = 0L
        private var lastConfigFileModified = -1L
        private const val CACHE_TTL = 10000L // 10 seconds fallback poll
        
        private var cachedWidth = 0
        private var cachedHeight = 0
        private var lastMetricsUpdate = 0L
        private const val METRICS_TTL = 60000L // 60 seconds

        // --- Async Logging System (Only in system_server) ---
        private val logQueue = LinkedBlockingQueue<String>(500)
        @Volatile private var loggerStarted = false

        @Synchronized
        private fun startLogger() {
            if (loggerStarted) return
            loggerStarted = true
            val loggerThread = Thread({
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        val entry = logQueue.take()
                        val parts = entry.split("|", limit = 2)
                        if (parts.size == 2) {
                            val file = File(parts[0])
                            file.parentFile?.mkdirs()
                            file.appendText("${parts[1]}\n")
                            if (file.length() > 204800) file.delete() // 200KB rotation
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Logger thread error: ${e.message}")
                    }
                }
            }, "InputBlocker-Logger")
            loggerThread.isDaemon = true
            loggerThread.start()
        }
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        XposedBridge.log("$TAG: InputBlocker hook module initialized (Vector/Zygisk/LSPosed compatible)")
        
        val hookHandler = object : de.robv.android.xposed.XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val now = System.currentTimeMillis()
                    updateConfigIfNeeded(now)

                    if (!cachedEnabled || cachedPaused) return
                    if (!cachedLsposedMode) return // LSPosed mode disabled — OverlayService handles blocking

                    // Extract MotionEvent from method arguments (supports dispatchMotionLocked & injectInputEvent overloads)
                    var motionEvent: MotionEvent? = null
                    for (arg in param.args) {
                        if (arg is MotionEvent) {
                            motionEvent = arg
                            break
                        }
                    }
                    if (motionEvent == null) return

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

                    // 1. Priority: Exclude Zones (Whitelist)
                    for (region in regions) {
                        if (region.isExclude && region.contains(nx, ny)) {
                            logLatency(System.nanoTime() - startNano)
                            return
                        }
                    }

                    // 2. Surgical Blocking Zones
                    for (region in regions) {
                        if (!region.isExclude && region.contains(nx, ny)) {
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
                    handleHookCrash(t)
                }
            }
        }

        var hooked = false
        // Strategy 1: Standard InputDispatcher.dispatchMotionLocked
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.input.InputDispatcher",
                null,
                "dispatchMotionLocked",
                android.os.IBinder::class.java,
                hookHandler
            )
            hooked = true
            XposedBridge.log("$TAG: Hooked InputDispatcher.dispatchMotionLocked successfully")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Primary hook failed (${e.message}), trying alternative signatures...")
        }

        // Strategy 2: Narrow fallback matching specific InputDispatcher methods containing MotionEvent
        if (!hooked) {
            try {
                val inputDispatcherClass = XposedHelpers.findClass("com.android.server.input.InputDispatcher", null)
                for (method in inputDispatcherClass.declaredMethods) {
                    val name = method.name
                    if ((name == "dispatchMotionLocked" || name == "dispatchMotion" || name == "injectInputEvent") &&
                        method.parameterTypes.size in 1..4 &&
                        method.parameterTypes.contains(MotionEvent::class.java)) {
                        XposedBridge.hookMethod(method, hookHandler)
                        hooked = true
                        val paramSig = method.parameterTypes.joinToString { it.simpleName }
                        XposedBridge.log("$TAG: Hooked strategy 2: ${method.declaringClass.name}.$name($paramSig)")
                        break
                    }
                }
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: InputDispatcher fallback failed: ${e.message}")
            }
        }

        // Strategy 3: Narrow fallback to InputManagerService.injectInputEvent overloads
        if (!hooked) {
            try {
                val imsClass = XposedHelpers.findClass("com.android.server.input.InputManagerService", null)
                for (method in imsClass.declaredMethods) {
                    val name = method.name
                    if ((name == "injectInputEvent" || name == "injectInputEventInternal") &&
                        method.parameterTypes.contains(MotionEvent::class.java)) {
                        XposedBridge.hookMethod(method, hookHandler)
                        hooked = true
                        val paramSig = method.parameterTypes.joinToString { it.simpleName }
                        XposedBridge.log("$TAG: Hooked strategy 3: ${method.declaringClass.name}.$name($paramSig)")
                        break
                    }
                }
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: InputManagerService fallback failed: ${e.message}")
            }
        }

        if (!hooked) {
            XposedBridge.log("$TAG: WARNING - Could not hook any InputDispatcher/InputManager method.")
        }
    }

    private fun shouldBlockSurgically(event: MotionEvent, region: Region): Boolean {
        val pressure = event.pressure
        val duration = event.eventTime - event.downTime
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
                lastMetricsUpdate = now
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to update display metrics: ${e.message}")
        }
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
            val crashFile = File("/data/adb/modules/inputblocker/config/crash_detected")
            crashFile.parentFile?.mkdirs()
            crashFile.writeText("1")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to write crash flag: ${e.message}")
        }
    }

    private fun updateConfigIfNeeded(now: Long) {
        val pkg = currentPackageName
        val configPath = if (pkg != null && File("/data/adb/modules/inputblocker/config/profiles/$pkg.conf").exists()) {
            "/data/adb/modules/inputblocker/config/profiles/$pkg.conf"
        } else {
            "/data/adb/modules/inputblocker/config/profiles/default.conf"
        }

        val file = File(configPath)
        val fileLastMod = if (file.exists()) file.lastModified() else -1L

        // Re-read immediately if file modification time changed, otherwise respect TTL
        if (now - lastLoadTime < CACHE_TTL && fileLastMod == lastConfigFileModified && lastConfigFileModified != -1L) return

        try {
            val killSwitch = File("/data/adb/modules/inputblocker/config/kill_switch")
            if (killSwitch.exists()) {
                cachedEnabled = false
                lastLoadTime = now
                lastConfigFileModified = fileLastMod
                return
            }

            testModeActive = File("/data/adb/modules/inputblocker/config/test_mode").exists()

            if (!file.exists()) {
                lastLoadTime = now
                lastConfigFileModified = fileLastMod
                return
            }
            
            val newRegions = ArrayList<Region>()
            var newEnabled = true
            var newPaused = false
            var newLsposedMode = true

            file.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("enabled=") -> newEnabled = trimmed.substring(8) == "1"
                        trimmed.startsWith("paused=") -> newPaused = trimmed.substring(7) == "1"
                        trimmed.startsWith("lsposed_mode=") -> newLsposedMode = trimmed.substring(13) == "1"
                        trimmed.isNotEmpty() && !trimmed.startsWith("#") ->
                            Region.fromString(trimmed)?.let { newRegions.add(it) }
                    }
                }
            }
            cachedRegions = newRegions
            cachedEnabled = newEnabled
            cachedPaused = newPaused
            cachedLsposedMode = newLsposedMode
            lastLoadTime = now
            lastConfigFileModified = fileLastMod
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
