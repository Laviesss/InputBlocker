package com.inputblocker.pctool

import java.io.*
import java.util.*
import com.inputblocker.shared.GhostTap

class ADBHelper : AutoCloseable {
    var deviceSerial: String? = null
        private set
    var connected: Boolean = false
        private set
    var screenWidth: Int = 1080
        private set
    var screenHeight: Int = 1920
        private set
    var cachedModulePath: String? = null
        private set

    init {
        startADBServer()
        connect()
        cachedModulePath = detectModulePath()
    }

    private fun startADBServer() {
        try {
            runProcess("adb", "start-server")
        } catch (e: Exception) {
            println("Failed to start ADB server: ${e.message}")
        }
    }

    private fun connect() {
        try {
            val devices = listDevices()
            if (devices.isEmpty()) {
                println("No devices found")
                return
            }
            if (devices.size > 1) {
                println("Multiple devices found, using first: ${devices[0]}")
            }
            deviceSerial = devices[0]
            connected = true
            println("Connected to device: $deviceSerial")
            getScreenSize()
        } catch (e: Exception) {
            println("ADB connection failed: ${e.message}")
            connected = false
        }
    }

    private fun listDevices(): List<String> {
        val devices = mutableListOf<String>()
        try {
            val output = runProcess("adb", "devices")
            output.lines().forEach { line ->
                if (line.contains("\tdevice")) {
                    val parts = line.split("\t")
                    if (parts.isNotEmpty()) {
                        devices.add(parts[0].trim())
                    }
                }
            }
        } catch (e: Exception) {
            println("Failed to list devices: ${e.message}")
        }
        return devices
    }

    private fun getScreenSize() {
        try {
            val output = runProcess("adb", "-s $deviceSerial shell wm size")
            val parts = output.split(":")
            if (parts.size > 1) {
                val sizeParts = parts[1].trim().split("x")
                if (sizeParts.size == 2) {
                    screenWidth = sizeParts[0].toInt()
                    screenHeight = sizeParts[1].toInt()
                }
            }
        } catch (e: Exception) {
            println("Failed to get screen size: ${e.message}")
        }
    }

    private fun detectModulePath(): String {
        val paths = listOf(
            "/data/adb/modules/inputblocker",      // Magisk
            "/data/ksu/modules/inputblocker",      // KernelSU
            "/data/apatch/modules/inputblocker",   // APatch
            "/su/su.d/inputblocker"                // SuperSU
        )

        for (path in paths) {
            val result = runProcess("adb", "-s $deviceSerial shell test -d '$path' && echo EXISTS || echo MISSING")
            if (result.contains("EXISTS")) {
                println("Detected module path: $path")
                return path
            }
        }
        return paths[0]
    }

    fun ensureConnected(): Boolean {
        if (connected && deviceSerial != null) {
            try {
                val state = runProcess("adb", "-s $deviceSerial get-state")
                if (state.trim().equals("device", ignoreCase = true)) {
                    return true
                }
            } catch (e: Exception) { }
        }
        connect()
        return connected
    }

    // Overload for simple arguments
    fun runProcess(fileName: String, args: String): String {
        val fullCmd = mutableListOf<String>()
        fullCmd.add(fileName)
        args.split(" ").forEach { if (it.isNotEmpty()) fullCmd.add(it) }
        
        return try {
            val process = ProcessBuilder(fullCmd)
                .redirectErrorStream(true)
                .start()
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            println("Error running process $fileName: ${e.message}")
            ""
        }
    }

    fun pullBlockLog(): List<GhostTap> {
        if (!ensureConnected()) return emptyList()
        
        val modulePath = cachedModulePath ?: return emptyList()
        val logPath = "$modulePath/config/blocklog.txt"
        val output = runProcess("adb", "-s $deviceSerial shell cat $logPath")
        
        val taps = mutableListOf<GhostTap>()
        output.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            try {
                // Format: HH:mm:ss | X: 0.xxx, Y: 0.yyy | P: 0.xxx, D: xxxms | Region: [...]
                val parts = line.split("|")
                if (parts.size >= 3) {
                    val timestamp = parts[0].trim()
                    val coordsPart = parts[1].trim()
                    val surgicalPart = parts[2].trim()
                    
                    val xMatch = "X: ([0-9.]+)".toRegex().find(coordsPart)
                    val yMatch = "Y: ([0-9.]+)".toRegex().find(coordsPart)
                    val pMatch = "P: ([0-9.]+)".toRegex().find(surgicalPart)
                    val dMatch = "D: ([0-9]+)ms".toRegex().find(surgicalPart)
                    
                    if (xMatch != null && yMatch != null && pMatch != null && dMatch != null) {
                        taps.add(GhostTap(
                            x = xMatch.groupValues[1].toFloat(),
                            y = yMatch.groupValues[1].toFloat(),
                            pressure = pMatch.groupValues[1].toFloat(),
                            duration = dMatch.groupValues[1].toLong(),
                            timestamp = timestamp
                        ))
                    }
                }
            } catch (e: Exception) {
                println("Error parsing log line: ${e.message}")
            }
        }
        return taps
    }

    fun streamLiveEvents(onEvent: (LiveEvent) -> Unit): java.util.concurrent.Future<*> {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        return executor.submit {
            try {
                val process = ProcessBuilder("adb", "-s $deviceSerial", "logcat", "-s", "InputBlockerLive").start()
                val reader = process.inputStream.bufferedReader()
                reader.forEachLine { line ->
                    // Line format: I/InputBlockerLive: TYPE|X|Y
                    val logContent = line.substringAfter("InputBlockerLive: ").trim()
                    val parts = logContent.split("|")
                    if (parts.size == 3) {
                        onEvent(LiveEvent(
                            type = parts[0],
                            x = parts[1].toFloatOrNull() ?: 0f,
                            y = parts[2].toFloatOrNull() ?: 0f,
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                }
            } catch (e: Exception) {
                println("Live stream error: ${e.message}")
            }
        }
    }

    fun installModule(zipFile: File): Boolean {
        if (!ensureConnected()) return false
        
        return try {
            // 1. Push ZIP to temporary storage
            runProcess("adb", "-s $deviceSerial push ${zipFile.absolutePath} /data/local/tmp/inputblocker.zip")
            
            // 2. Use root shell to install.
            // The most universal way for Magisk/KSU/APatch is to put it in the modules folder and reboot,
            // or use the manager's API. Since we are root, we can manually deploy.
            val modulePath = "/data/adb/modules/inputblocker"
            val installCmd = "su -c \"mkdir -p $modulePath && unzip -o /data/local/tmp/inputblocker.zip -d $modulePath && chmod -R 755 $modulePath\""
            
            val result = runProcess("adb", "-s $deviceSerial shell $installCmd")
            result.contains("unzip") || !result.contains("error", ignoreCase = true)
        } catch (e: Exception) {
            println("Installation failed: ${e.message}")
            false
        }
    }

    fun pullLatencyLog(): List<Long> {
        if (!ensureConnected()) return emptyList()
        
        val modulePath = cachedModulePath ?: return emptyList()
        val logPath = "$modulePath/config/latency.log"
        val output = runProcess("adb", "-s $deviceSerial shell cat $logPath")
        
        return output.lines().mapNotNull { it.trim().toLongOrNull() }
    }

    fun pullConfig(): List<com.inputblocker.shared.Region> {
        if (!ensureConnected()) return emptyList()
        
        val modulePath = cachedModulePath ?: return emptyList()
        val configPath = "$modulePath/config/profiles/default.conf"
        val output = runProcess("adb", "-s $deviceSerial shell cat $configPath")
        
        val regions = mutableListOf<com.inputblocker.shared.Region>()
        output.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("enabled=") && !trimmed.startsWith("force_safe_mode=")) {
                com.inputblocker.shared.Region.fromString(trimmed)?.let { regions.add(it) }
            }
        }
        return regions
    }

    fun pushConfig(regions: List<com.inputblocker.shared.Region>, enabled: Boolean, forceSafeMode: Boolean): Boolean {
        if (!ensureConnected()) return false

        val config = StringBuilder()
        config.appendLine("# InputBlocker Configuration")
        config.appendLine("enabled=${if (enabled) "1" else "0"}")
        config.appendLine("force_safe_mode=${if (forceSafeMode) "1" else "0"}")
        config.appendLine()
        regions.forEach { config.appendLine(it.toString()) }

        return try {
            val tempFile = File.createTempFile("inputblocker_config", ".txt")
            tempFile.writeText(config.toString())

            runProcess("adb", "-s $deviceSerial shell mkdir -p $cachedModulePath/config/profiles")
            runProcess("adb", "-s $deviceSerial push ${tempFile.absolutePath} $cachedModulePath/config/profiles/default.conf")
            runProcess("adb", "-s $deviceSerial shell chmod 644 $cachedModulePath/config/profiles/default.conf")

            tempFile.delete()
            true
        } catch (e: Exception) {
            println("Failed to push config: ${e.message}")
            false
        }
    }

    fun getCurrentConfig(): List<com.inputblocker.shared.Region> {
        val regions = mutableListOf<com.inputblocker.shared.Region>()
        if (!ensureConnected()) return regions

        try {
            val output = runProcess("adb", "-s $deviceSerial shell cat $cachedModulePath/config/profiles/default.conf")
            output.lines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty() && !line.startsWith("#") && !line.startsWith("enabled=") && !line.startsWith("force_safe_mode=")) {
                    com.inputblocker.shared.Region.fromString(line)?.let { regions.add(it) }
                }
            }
        } catch (e: Exception) {
            println("Failed to get current config: ${e.message}")
        }
        return regions
    }

    override fun close() {
        connected = false
    }
}

data class LiveEvent(
    val type: String,
    val x: Float,
    val y: Float,
    val timestamp: Long
)
