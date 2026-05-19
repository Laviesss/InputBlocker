package com.inputblocker.pctool

import com.inputblocker.shared.Region
import java.io.File

data class Preset(
    val name: String,
    val enabled: Boolean,
    val safeMode: Boolean,
    val regions: List<Region>
)

object PresetManager {
    private const val PRESET_EXTENSION = ".ibpreset"

    fun exportPreset(file: File, preset: Preset): Boolean {
        return try {
            val content = StringBuilder()
            content.appendLine("# InputBlocker Community Preset")
            content.appendLine("name=${preset.name}")
            content.appendLine("enabled=${if (preset.enabled) "1" else "0"}")
            content.appendLine("safe_mode=${if (preset.safeMode) "1" else "0"}")
            content.appendLine()
            content.appendLine("# Regions")
            preset.regions.forEach { content.appendLine(it.toString()) }
            
            file.writeText(content.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importPreset(file: File): Preset? {
        return try {
            val lines = file.readLines()
            var name = "Unknown Preset"
            var enabled = true
            var safeMode = true
            val regions = mutableListOf<Region>()

            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("#") || trimmed.isEmpty()) return@forEach
                
                if (trimmed.startsWith("name=")) {
                    name = trimmed.substring(5)
                } else if (trimmed.startsWith("enabled=")) {
                    enabled = trimmed.substring(8) == "1"
                } else if (trimmed.startsWith("safe_mode=")) {
                    safeMode = trimmed.substring(10) == "1"
                } else {
                    Region.fromString(trimmed)?.let { regions.add(it) }
                }
            }
            Preset(name, enabled, safeMode, regions)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
