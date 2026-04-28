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
        // Initialize Xposed hook in the system server process
        XposedBridge.log("InputBlocker Xposed module initialized")
        
        // We hook InputDispatcher to intercept touch events at the system level
        try {
            // The following code had syntax errors and was non-functional as-is.
            // Hooking InputDispatcher is highly version-dependent.
            // This is kept as a reference for future implementation.
            
            /*
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
                        val x = motionEvent.x
                        val y = motionEvent.y

                        for (region in cachedRegions) {
                            if (x >= region.x1 && x <= region.x2 && y >= region.y1 && y <= region.y2) {
                                XposedBridge.log("InputBlocker-Xposed: Blocking touch at ($x, $y)")
                                param.setResult(null) 
                                return
                            }
                        }
                    }
                }
            )
            */
        } catch (e: Exception) {
            XposedBridge.log("InputBlocker-Xposed: Error hooking InputDispatcher: ${e.message}")
        }
    }

    private fun updateConfigIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastLoadTime < CACHE_TTL) return

        try {
            val configPath = "/data/adb/modules/inputblocker/config/blocked_regions.conf"
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
                                        parts[0].trim().toInt(),
                                        parts[1].trim().toInt(),
                                        parts[2].trim().toInt(),
                                        parts[3].trim().toInt()
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
