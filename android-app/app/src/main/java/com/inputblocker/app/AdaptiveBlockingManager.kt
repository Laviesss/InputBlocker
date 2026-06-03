package com.inputblocker.app

import android.content.Context
import android.util.Log
import com.inputblocker.shared.Region
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Senior Engineering: Adaptive Tuning System.
 * Analyzes the blocklog.txt to calculate optimal region dimensions.
 * Aims to minimize blocking area while maximizing ghost tap coverage.
 */
object AdaptiveBlockingManager {

    private const val TAG = "InputBlocker-Adaptive"

    fun analyzeAndOptimize(context: Context) {
        try {
            val modulePath = InputBlockerServiceManager.getModulePath(context)
            val logFile = File("$modulePath/config/blocklog.txt")
            if (!logFile.exists()) return

            val lines = logFile.readLines()
            if (lines.isEmpty()) return

            val currentRegions = loadCurrentRegions(context)
            if (currentRegions.isEmpty()) return

            val touches = parseLog(lines)
            val optimizedRegions = ArrayList<Region>(currentRegions.size)
            var changesMade = false

            for (region in currentRegions) {
                val hits = touches.filter { (x, y) -> 
                    x >= region.x1 && x <= region.x2 && y >= region.y1 && y <= region.y2 
                }

                if (hits.size >= 10) { // High confidence threshold
                    val bounds = calculateTightBounds(hits)
                    val padding = 0.005f
                    val optimized = region.copy(
                        x1 = (bounds[0] - padding).coerceAtLeast(0f),
                        y1 = (bounds[1] - padding).coerceAtLeast(0f),
                        x2 = (bounds[2] + padding).coerceAtMost(1f),
                        y2 = (bounds[3] + padding).coerceAtMost(1f)
                    )
                    optimizedRegions.add(optimized)
                    changesMade = true
                } else {
                    optimizedRegions.add(region)
                }
            }

            if (changesMade) {
                saveOptimizedConfig(context, optimizedRegions)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Adaptive optimization failed", e)
        }
    }

    private fun loadCurrentRegions(context: Context): List<Region> {
        val regions = mutableListOf<Region>()
        val configFile = File(InputBlockerServiceManager.getConfigFile(context))
        if (!configFile.exists()) return regions

        try {
            BufferedReader(FileReader(configFile)).use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.contains("=")) {
                        Region.fromString(trimmed)?.let { regions.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load current regions: ${e.message}")
        }
        return regions
    }

    private fun parseLog(lines: List<String>): List<Pair<Float, Float>> {
        val touches = ArrayList<Pair<Float, Float>>(lines.size)
        for (line in lines) {
            try {
                // Format: "HH:mm:ss | X: 0.123, Y: 0.456 | ..."
                val parts = line.split(" | ")
                if (parts.size >= 2) {
                    val coordStr = parts[1]
                    val x = coordStr.substringAfter("X: ").substringBefore(",").toFloat()
                    val y = coordStr.substringAfter("Y: ").toFloat()
                    touches.add(Pair(x, y))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse log line: ${e.message}")
            }
        }
        return touches
    }

    private fun calculateTightBounds(hits: List<Pair<Float, Float>>): FloatArray {
        var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
        for (hit in hits) {
            if (hit.first < minX) minX = hit.first
            if (hit.first > maxX) maxX = hit.first
            if (hit.second < minY) minY = hit.second
            if (hit.second > maxY) maxY = hit.second
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    private fun saveOptimizedConfig(context: Context, regions: List<Region>) {
        val content = StringBuilder()
        content.append("# InputBlocker AUTO-OPTIMIZED Configuration\n")
        content.append("enabled=1\n\n")
        for (region in regions) {
            content.append("$region\n")
        }
        InputBlockerServiceManager.saveConfig(context, "default", content.toString())
    }
}
