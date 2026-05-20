package com.inputblocker.app

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object InputBlockerServiceManager {
    
    private const val TAG = "InputBlocker-Services"
    
    const val NORMAL_SHUTDOWN_FLAG = "/data/local/tmp/inputblocker/normal_shutdown"
    const val CRASH_FLAG = "/data/local/tmp/inputblocker/crash_detected"
    
    private var cachedModulePath: String? = null
    
    fun runRootCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor(5, TimeUnit.SECONDS)
            output
        } catch (e: Exception) {
            Log.e(TAG, "Root command failed: $command", e)
            ""
        }
    }

    fun getModulePath(context: Context): String {
        cachedModulePath?.let { return it }
        
        val paths = listOf(
            "/data/adb/modules/inputblocker",
            "/data/ksu/modules/inputblocker",
            "/data/apatch/modules/inputblocker",
            "/su/su.d/inputblocker"
        )
        
        for (path in paths) {
            // Check existence via root to be sure
            if (runRootCommand("ls -d $path").contains(path)) {
                cachedModulePath = path
                Log.i(TAG, "Detected module path: $path")
                return path
            }
        }
        
        cachedModulePath = "/data/adb/modules/inputblocker"
        return cachedModulePath!!
    }
    
    fun getConfigFile(context: Context, profile: String = "default"): String {
        return getModulePath(context) + "/config/profiles/$profile.conf"
    }

    fun saveConfig(context: Context, profile: String, content: String) {
        try {
            val path = getConfigFile(context, profile)
            val dir = path.substringBeforeLast("/")
            runRootCommand("mkdir -p $dir")
            
            // Write to a temporary file in app storage first, then move with root
            val tempFile = File(context.cacheDir, "temp_config.conf")
            tempFile.writeText(content)
            
            runRootCommand("cp ${tempFile.absolutePath} $path")
            runRootCommand("chmod 644 $path")
            tempFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config for profile $profile", e)
        }
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
    }
    
    private fun shouldStartInSafeMode(context: Context): Boolean {
        val crashFlag = File(CRASH_FLAG)
        val shutdownFile = File(NORMAL_SHUTDOWN_FLAG)
        
        val wasCleanShutdown = shutdownFile.exists()
        if (wasCleanShutdown) shutdownFile.delete()
        
        if (crashFlag.exists()) {
            crashFlag.delete()
            enableSafeMode(context)
            return true
        }
        
        if (!wasCleanShutdown && !File("/data/local/tmp/inputblocker/booting").exists()) {
            enableSafeMode(context)
            return true
        }
        
        return false
    }
    
    fun enableSafeMode(context: Context) {
        try {
            val path = getConfigFile(context)
            // Use sed via root to disable blocking safely
            runRootCommand("sed -i 's/^enabled=1/enabled=0/' $path")
            runRootCommand("sed -i '/force_safe_mode=/d' $path") // Clear old
            runRootCommand("echo 'force_safe_mode=1' >> $path")
            Log.i(TAG, "Safe mode enabled via root")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable safe mode", e)
        }
    }
    
    fun reportCrash() {
        try {
            runRootCommand("mkdir -p /data/local/tmp/inputblocker && touch ${CRASH_FLAG}")
            Log.e(TAG, "Crash detected! Safe Mode flag set.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report crash", e)
        }
    }

    fun onShutdown() {
        runRootCommand("mkdir -p /data/local/tmp/inputblocker && touch $NORMAL_SHUTDOWN_FLAG")
    }

    fun clearEmergencyReset(context: Context) {
        runRootCommand("rm -f ${getModulePath(context)}/config/kill_switch")
    }

    fun createBackup(context: Context): Boolean {
        return try {
            val modulePath = getModulePath(context)
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val backupDir = "/storage/emulated/0/InputBlocker/backups"
            
            runRootCommand("mkdir -p $backupDir")
            runRootCommand("tar -czf $backupDir/backup_$timestamp.tar.gz -C $modulePath/config .")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}")
            false
        }
    }

    fun restoreBackup(context: Context, backupFile: File): Boolean {
        return try {
            val modulePath = getModulePath(context)
            runRootCommand("mkdir -p $modulePath/config")
            runRootCommand("tar -xzf ${backupFile.absolutePath} -C $modulePath/config")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}")
            false
        }
    }
}
