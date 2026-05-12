package com.inputblocker.app

import android.util.Log
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.io.BufferedReader
import java.io.FileReader

class InputBlockerXposed : IXposedHookZygoteInit {
    companion object {
        private const val TAG = "InputBlocker-Xposed"
        private var cachedRegions = mutableListOf<Region>()
        private var cachedEnabled = true
        private var lastLoadTime = 0L
        private const val CACHE_TTL = 5000L // 5 seconds
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
                        if (!cachedEnabled) return

                        val motionEvent = param.args.find { it is android.view.MotionEvent } as? android.view.MotionEvent ?: return
                        
                        // Get screen size for normalization
                        val metrics = android.util.DisplayMetrics()
                        try {
                            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
                            val app = XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as? android.app.Application
                            val windowManager = app?.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
                            windowManager?.defaultDisplay?.getRealMetrics(metrics)
                        } catch (e: Exception) {
                            // Fallback if ActivityThread is not available (e.g. early zygote)
                            return
                        }

                        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) return
                        
                        val nx = motionEvent.x / metrics.widthPixels
                        val ny = motionEvent.y / metrics.heightPixels

                        for (region in cachedRegions) {
                            if (nx >= region.x1 && nx <= region.x2 && ny >= region.y1 && ny <= region.y2) {
                                // XposedBridge.log("InputBlocker-Xposed: Blocking touch at normalized ($nx, $ny)")
                                param.setResult(null) 
                                return
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("InputBlocker-Xposed: Error hooking InputDispatcher: ${e.message}")
        }
    }

    private fun updateConfigIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastLoadTime < CACHE_TTL) return

        try {
            val configPath = "/data/adb/modules/inputblocker/config/profiles/default.conf"
            val file = File(configPath)
            if (!file.exists()) return
            
            val newRegions = mutableListOf<Region>()
            var newEnabled = true

            BufferedReader(FileReader(file)).use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.isEmpty() || trimmed.startsWith("#") -> {}
                        trimmed.startsWith("enabled=") -> {
                            newEnabled = trimmed.substring(8) == "1"
                        }
                        else -> {
                            val parts = trimmed.split(",")
                            if (parts.size == 4) {
                                try {
                                    newRegions.add(Region(
                                        parts[0].trim().toFloat(),
                                        parts[1].trim().toFloat(),
                                        parts[2].trim().toFloat(),
                                        parts[3].trim().toFloat()
                                    ))
                                } catch (e: Exception) {}
                            }
                        }
                    }
                }
            }
            cachedRegions = newRegions
            cachedEnabled = newEnabled
            lastLoadTime = now
        } catch (e: Exception) {
            XposedBridge.log("InputBlocker-Xposed: Error loading config: ${e.message}")
        }
    }
}
