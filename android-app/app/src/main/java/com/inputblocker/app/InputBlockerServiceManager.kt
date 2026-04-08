package com.inputblocker.app

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

object InputBlockerServiceManager {
    
    private const val TAG = "InputBlocker-Services"
    
    const val NORMAL_SHUTDOWN_FLAG = "/data/local/tmp/inputblocker/normal_shutdown"
    const val CRASH_FLAG = "/data/local/tmp/inputblocker/crash_detected"
    
    private var cachedModulePath: String? = null
    
    fun getModulePath(context: Context): String {
        cachedModulePath?.let { return it }
        
        val paths = listOf(
            "/data/adb/modules/inputblocker",
            "/data/ksu/modules/inputblocker",
            "/data/apatch/modules/inputblocker",
            "/su/su.d/inputblocker"
        )
        
        for (path in paths) {
            if (File(path).exists()) {
                cachedModulePath = path
                Log.i(TAG, "Detected module path: $path")
                return path
            }
        }
        
        cachedModulePath = "/data/adb/modules/inputblocker"
        return cachedModulePath!!
    }
    
    fun getConfigFile(context: Context): String {
        return getModulePath(context) + "/config/blocked_regions.conf"
    }
    
    fun startServices(context: Context) {
        Log.i(TAG, "Starting InputBlocker services...")
        
        if (shouldStartInSafeMode(context)) {
            Log.i(TAG, "Starting in safe mode - blocking disabled")
        }
        
        val overlayIntent = Intent(context, OverlayService::class.java)
        context.startForegroundService(overlayIntent)
        
        val volumeIntent = Intent(context, VolumeButtonListenerService::class.java)
        context.startService(volumeIntent)
        
        Log.i(TAG, "Services started")
    }
    
    private fun shouldStartInSafeMode(context: Context): Boolean {
        val configPath = getConfigFile(context)
        val configFile = File(configPath)
        val crashFlag = File(CRASH_FLAG)
        val shutdownFile = File(NORMAL_SHUTDOWN_FLAG)
        
        val wasCleanShutdown = shutdownFile.exists()
        shutdownFile.delete()
        
        if (crashFlag.exists()) {
            crashFlag.delete()
            Log.i(TAG, "Crash flag detected - enabling safe mode")
            return true
        }
        
        if (!wasCleanShutdown) {
            Log.i(TAG, "Unexpected shutdown detected - enabling safe mode")
            enableSafeMode(context)
            return true
        }
        
        return false
    }
    
    fun enableSafeMode(context: Context) {
        try {
            val configPath = getConfigFile(context)
            val configFile = File(configPath)
            if (configFile.exists()) {
                val lines = configFile.readLines().toMutableList()
                val content = StringBuilder()
                var foundEnabled = false
                var foundSafeMode = false
                
                for (line in lines) {
                    when {
                        line.startsWith("enabled=") -> {
                            content.append("enabled=0\n")
                            foundEnabled = true
                        }
                        line.startsWith("force_safe_mode=") -> {
                            content.append("force_safe_mode=1\n")
                            foundSafeMode = true
                        }
                        else -> content.append(line).append("\n")
                    }
                }
                
                if (!foundEnabled) {
                    content.insert(0, "enabled=0\nforce_safe_mode=1\n\n")
                }
                
                configFile.writeText(content.toString())
                Log.i(TAG, "Safe mode enabled - blocking disabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable safe mode", e)
        }
    }
    
    fun onShutdown() {
        Log.i(TAG, "Shutdown detected")
        
        val shutdownFile = File(NORMAL_SHUTDOWN_FLAG)
        try {
            shutdownFile.parentFile?.mkdirs()
            shutdownFile.createNewFile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create shutdown flag", e)
        }
    }
}
