package com.inputblocker.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

object InputBlockerServiceManager {
    
    private const val TAG = "InputBlocker-Services"
    
    const val NORMAL_SHUTDOWN_FLAG = "/data/local/tmp/inputblocker/normal_shutdown"
    // Synced with hook module crash path (system_server crash detection)
    const val CRASH_FLAG = "/data/adb/modules/inputblocker/config/crash_detected"
    const val CRASH_COUNTER_FILE = "/data/local/tmp/inputblocker/crash_count"
    
    /** Max consecutive crashes before safe mode is forced without requiring crash flag file */
    private const val MAX_CONSECUTIVE_CRASHES = 3
    
    private var cachedModulePath: String? = null
    
    /** Test hook: set to override command execution for unit tests */
    internal var testCommandRunner: ((String) -> String)? = null

    /**
     * Checks whether root access (su) is available on this device.
     * Returns true if su executes and returns a non-error response.
     */
    fun hasRootAccess(): Boolean {
        testCommandRunner?.let { return it("echo ok").contains("ok") }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo ok"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()
            output.contains("ok")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether LSPosed is installed by looking for its APK or module path.
     */
    fun isLsposedInstalled(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "sh", "-c",
                "ls /data/data/org.lsposed.lsposed 2>/dev/null || " +
                "ls /data/app/*lsposed* 2>/dev/null || " +
                "pm list packages 2>/dev/null | grep -q lsposed"
            ))
            process.waitFor()
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun runRootCommand(command: String): String {
        testCommandRunner?.let { return it(command) }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            val startTime = System.currentTimeMillis()
            val timeout = 5000L
            var exitCode: Int? = null
            while (System.currentTimeMillis() - startTime < timeout && exitCode == null) {
                try { exitCode = process.exitValue() } catch (_: IllegalThreadStateException) {
                    Thread.sleep(100)
                }
            }
            if (exitCode == null) process.destroy()
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
    
    fun getConfigDir(context: Context): String {
        return getModulePath(context) + "/config"
    }

    fun getConfigFile(context: Context, profile: String = "default"): String {
        return getConfigDir(context) + "/profiles/$profile.conf"
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

        // Check consecutive crash count for safe mode
        val crashCount = getCrashCount()
        if (crashCount >= MAX_CONSECUTIVE_CRASHES) {
            Log.w(TAG, "$crashCount consecutive crashes detected — forcing safe mode")
            enableSafeMode(context)
            runRootCommand("rm -f $CRASH_COUNTER_FILE")
        }

        if (shouldStartInSafeMode(context)) {
            Log.i(TAG, "Starting in safe mode - blocking disabled")
        }
        
        val overlayIntent = Intent(context, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(overlayIntent)
        } else {
            context.startService(overlayIntent)
        }
        
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
    
    fun reportCrash(error: Throwable? = null) {
        try {
            runRootCommand("mkdir -p /data/local/tmp/inputblocker && touch ${CRASH_FLAG}")
            // Increment consecutive crash counter
            val current = getCrashCount()
            runRootCommand("echo '${current + 1}' > $CRASH_COUNTER_FILE")

            // Write detailed crash log
            val timestamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()
            ).format(java.util.Date())
            val crashLogDir = "/data/local/tmp/inputblocker/crash_logs"
            val crashLogFile = "$crashLogDir/crash_$timestamp.log"
            val detail = buildString {
                appendLine("=== InputBlocker Crash Report ===")
                appendLine("Time: $timestamp")
                if (error != null) {
                    appendLine("Error: ${error.message}")
                    appendLine("Stack:")
                    for (ste in error.stackTrace) {
                        appendLine("  at $ste")
                    }
                }
                appendLine("Crash Count: ${current + 1}")
            }
            runRootCommand("mkdir -p $crashLogDir")
            // Write via temp file + root cp (echo can mangle special chars)
            val tempFile = java.io.File(getCrashCountDir(), "crash_payload.tmp")
            tempFile.writeText(detail)
            runRootCommand("cp ${tempFile.absolutePath} $crashLogFile && chmod 644 $crashLogFile && rm -f ${tempFile.absolutePath}")

            Log.e(TAG, "Crash detected! Consecutive crash count: ${current + 1}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report crash", e)
        }
    }

    fun getCrashCount(): Int {
        return try {
            val result = runRootCommand("cat $CRASH_COUNTER_FILE 2>/dev/null").trim()
            result.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun resetCrashCounter() {
        runRootCommand("rm -f $CRASH_COUNTER_FILE")
    }

    fun getCrashCountDir(): String {
        return "/data/local/tmp/inputblocker"
    }

    fun onShutdown() {
        runRootCommand("mkdir -p /data/local/tmp/inputblocker && touch $NORMAL_SHUTDOWN_FLAG")
        // Reset consecutive crash counter on clean shutdown
        runRootCommand("rm -f $CRASH_COUNTER_FILE")
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
