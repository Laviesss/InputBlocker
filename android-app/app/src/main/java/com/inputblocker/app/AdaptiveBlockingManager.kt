package com.inputblocker.app

import com.inputblocker.shared.Region
import android.content.Context
import android.util.Log
import java.io.File

object AdaptiveBlockingManager {

    private const val TAG = "InputBlocker-Adaptive"

    fun analyzeAndOptimize(context: Context) {
        try {
            val modulePath = InputBlockerServiceManager.getModulePath(context)
            val logFile = File("$modulePath/config/blocklog.txt")
            if (!logFile.exists()) return

            val lines = logFile.readLines()
            if (lines.isEmpty()) return

            // Load current regions
            val currentRegions = mutableListOf<Region>()
            val configFile = File(InputBlockerServiceManager.getConfigFile(context))
            if (configFile.exists()) {
                configFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("#") && !trimmed.startsWith("enabled=") && !trimmed.startsWith("lsposed_mode=")) {
                        Region.fromString(trimmed)?.let { currentRegions.add(it) }
                    }
                }
            }

            if (currentRegions.isEmpty()) return

            val touches = parseLog(lines)
            val optimizedRegions = mutableListOf<Region>()
            var changesMade = false

            for (region in currentRegions) {
                val hits = touches.filter { (x, y) -> 
                    x >= region.x1 && x <= region.x2 && y >= region.y1 && y <= region.y2 
                }

                if (hits.size >= 5) { // Threshold for optimization
                    val bounds = calculateBounds(hits)
                    // Add small padding
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
            Log.e(TAG, "Optimization failed", e)
        }
    }

    private fun parseLog(lines: List<String>): List<Pair<Float, Float>> {
        val touches = mutableListOf<Pair<Float, Float>>()
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
            } catch (e: Exception) {}
        }
        return touches
    }

    private fun calculateBounds(hits: List<Pair<Float, Float>>): FloatArray {
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
        content.append("# InputBlocker Optimized Configuration\n")
        content.append("enabled=1\n")
        content.append("lsposed_mode=0\n\n")
        for (region in regions) {
            content.append("$region\n")
        }
        InputBlockerServiceManager.saveConfig(context, "default", content.toString())
    }
}
