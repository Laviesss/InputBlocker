package com.inputblocker.pctool

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import com.inputblocker.shared.GhostTap
import com.inputblocker.shared.Region

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

    private var streamExecutor: ExecutorService? = null
    private var streamProcess: Process? = null

    companion object {
        private val cmdExecutor = Executors.newCachedThreadPool { r ->
            Thread(r).apply { isDaemon = true }
        }
    }

    init {
        startADBServer()
        connect()
        if (connected) {
            cachedModulePath = detectModulePath()
        }
    }

    private fun startADBServer() {
        try {
            runCmd("adb", "start-server")
        } catch (e: Exception) {
            println("Failed to start ADB server: ${e.message}")
        }
    }

    private fun connect() {
        try {
            val devices = listDevices()
            if (devices.isEmpty()) {
                println("No devices found")
                connected = false
                deviceSerial = null
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
            deviceSerial = null
        }
    }

    private fun listDevices(): List<String> {
        val devices = mutableListOf<String>()
        try {
            val output = runCmd("adb", "devices")
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
        val serial = deviceSerial ?: return
        try {
            val output = runCmd("adb", "-s", serial, "shell", "wm", "size")
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
        val serial = deviceSerial ?: return "/data/adb/modules/inputblocker"
        val paths = listOf(
            "/data/adb/modules/inputblocker",      // Magisk
            "/data/ksu/modules/inputblocker",      // KernelSU
            "/data/apatch/modules/inputblocker",   // APatch
            "/su/su.d/inputblocker"                // SuperSU
        )

        for (path in paths) {
            val result = runCmd("adb", "-s", serial, "shell", "test -d '$path' && echo EXISTS || echo MISSING")
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
                val state = runCmd("adb", "-s", deviceSerial!!, "get-state")
                if (state.trim().equals("device", ignoreCase = true)) {
                    return true
                }
            } catch (_: Exception) { }
        }
        connect()
        return connected
    }

    fun runCmd(vararg args: String): String {
        return runCmdList(args.toList())
    }

    fun runCmdList(cmd: List<String>): String {
        return try {
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val reader = process.inputStream.bufferedReader()
            val future = CompletableFuture.supplyAsync({
                reader.readText()
            }, cmdExecutor)
            val output = try {
                future.get(10, TimeUnit.SECONDS)
            } catch (_: TimeoutException) {
                process.destroyForcibly()
                ""
            }
            process.waitFor(1, TimeUnit.SECONDS)
            output
        } catch (e: Exception) {
            println("Error running command ${cmd.firstOrNull()}: ${e.message}")
            ""
        }
    }

    // Legacy compatibility method
    fun runProcess(fileName: String, args: String): String {
        val cmd = mutableListOf<String>()
        cmd.add(fileName)
        args.split(" ").filter { it.isNotEmpty() }.forEach { cmd.add(it) }
        return runCmdList(cmd)
    }

    fun pullBlockLog(): List<GhostTap> {
        if (!ensureConnected()) return emptyList()
        val serial = deviceSerial ?: return emptyList()
        val modulePath = cachedModulePath ?: detectModulePath()
        val logPath = "$modulePath/config/blocklog.txt"
        val output = runCmd("adb", "-s", serial, "shell", "cat", logPath)
        
        val taps = mutableListOf<GhostTap>()
        output.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            try {
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

    fun streamLiveEvents(onEvent: (LiveEvent) -> Unit): Future<*> {
        val serial = deviceSerial ?: throw IllegalStateException("No device connected")
        val executor = Executors.newSingleThreadExecutor()
        streamExecutor = executor
        return executor.submit {
            try {
                val process = ProcessBuilder("adb", "-s", serial, "logcat", "-s", "InputBlockerLive").start()
                streamProcess = process
                val reader = process.inputStream.bufferedReader()
                reader.forEachLine { line ->
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
        val serial = deviceSerial ?: return false
        
        return try {
            runCmd("adb", "-s", serial, "push", zipFile.absolutePath, "/data/local/tmp/inputblocker.zip")
            
            val modulePath = "/data/adb/modules/inputblocker"
            val installCmd = "su -c \"mkdir -p $modulePath && unzip -o /data/local/tmp/inputblocker.zip -d $modulePath && chmod -R 755 $modulePath\""
            
            val result = runCmd("adb", "-s", serial, "shell", installCmd)
            result.contains("unzip") || !result.contains("error", ignoreCase = true)
        } catch (e: Exception) {
            println("Installation failed: ${e.message}")
            false
        }
    }

    fun pullLatencyLog(): List<Long> {
        if (!ensureConnected()) return emptyList()
        val serial = deviceSerial ?: return emptyList()
        val modulePath = cachedModulePath ?: detectModulePath()
        val logPath = "$modulePath/config/latency.log"
        val output = runCmd("adb", "-s", serial, "shell", "cat", logPath)
        
        return output.lines().mapNotNull { it.trim().toLongOrNull() }
    }

    fun pullConfig(): List<Region> {
        if (!ensureConnected()) return emptyList()
        val serial = deviceSerial ?: return emptyList()
        val modulePath = cachedModulePath ?: detectModulePath()
        val configPath = "$modulePath/config/profiles/default.conf"
        val output = runCmd("adb", "-s", serial, "shell", "cat", configPath)
        
        val regions = mutableListOf<Region>()
        output.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("enabled=") && !trimmed.startsWith("force_safe_mode=")) {
                Region.fromString(trimmed)?.let { regions.add(it) }
            }
        }
        return regions
    }

    fun pushConfig(regions: List<Region>, enabled: Boolean, forceSafeMode: Boolean): Boolean {
        if (!ensureConnected()) return false
        val serial = deviceSerial ?: return false
        val modulePath = cachedModulePath ?: detectModulePath()

        val config = StringBuilder()
        config.appendLine("# InputBlocker Configuration")
        config.appendLine("enabled=${if (enabled) "1" else "0"}")
        config.appendLine("force_safe_mode=${if (forceSafeMode) "1" else "0"}")
        config.appendLine()
        regions.forEach { config.appendLine(it.toString()) }

        return try {
            val tempFile = File.createTempFile("inputblocker_config", ".txt")
            tempFile.writeText(config.toString())

            runCmd("adb", "-s", serial, "shell", "mkdir", "-p", "$modulePath/config/profiles")
            runCmd("adb", "-s", serial, "push", tempFile.absolutePath, "$modulePath/config/profiles/default.conf")
            runCmd("adb", "-s", serial, "shell", "chmod", "644", "$modulePath/config/profiles/default.conf")

            tempFile.delete()
            true
        } catch (e: Exception) {
            println("Failed to push config: ${e.message}")
            false
        }
    }

    fun getCurrentConfig(): List<Region> {
        return pullConfig()
    }

    override fun close() {
        connected = false
        try {
            streamProcess?.destroyForcibly()
            streamExecutor?.shutdownNow()
        } catch (_: Exception) { }
    }
}

data class LiveEvent(
    val type: String,
    val x: Float,
    val y: Float,
    val timestamp: Long
)
