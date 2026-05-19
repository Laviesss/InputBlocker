package com.inputblocker.pctool

import java.io.*
import java.util.*

data class Preset(
    val name: String,
    val author: String,
    val regions: List<com.inputblocker.shared.Region>
)

object PresetManager {
    fun exportPreset(file: File, preset: Preset) {
        file.bufferedWriter().use { writer ->
            writer.write("Preset: ${preset.name}\n")
            writer.write("Author: ${preset.author}\n")
            writer.write("---\n")
            preset.regions.forEach { region ->
                writer.write(region.toString() + "\n")
            }
        }
    }

    fun importPreset(file: File): Preset {
        val lines = file.readLines()
        var name = "Unknown Preset"
        var author = "Unknown"
        val regions = mutableListOf<com.inputblocker.shared.Region>()
        
        var headerFinished = false
        for (line in lines) {
            if (line.isBlank()) continue
            if (line == "---") {
                headerFinished = true
                continue
            }
            if (!headerFinished) {
                if (line.startsWith("Preset: ")) name = line.substring(8)
                if (line.startsWith("Author: ")) author = line.substring(8)
            } else {
                com.inputblocker.shared.Region.fromString(line)?.let { regions.add(it) }
            }
        }
        return Preset(name, author, regions)
    }
}
