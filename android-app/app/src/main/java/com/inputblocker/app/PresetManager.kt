package com.inputblocker.app

import android.content.Context
import android.util.Log
import com.inputblocker.shared.Region
import java.io.File
import java.net.URL

object PresetManager {
    private const val TAG = "PresetManager"

    fun importFromUrl(context: Context, urlString: String, onComplete: (Boolean, String) -> Unit) {
        Thread {
            try {
                val url = URL(urlString)
                val content = url.readText()
                
                // The .ibpreset format is:
                // DEVICE_MODEL: ...
                // VERSION: ...
                // COUNT: ...
                // data...
                
                val lines = content.lines().filter { it.isNotBlank() }
                if (lines.size < 3) {
                    onComplete(false, "Invalid preset format")
                    return@Thread
                }

                val regions = mutableListOf<Region>()
                for (i in 3 until lines.size) {
                    val line = lines[i]
                    val parts = line.split(",")
                    if (parts.size == 8) {
                        regions.add(Region(
                            isExclude = parts[0].trim().toInt() == 1,
                            type = parts[1].trim().toInt(),
                            x1 = parts[2].trim().toFloat(),
                            y1 = parts[3].trim().toFloat(),
                            x2 = parts[4].trim().toFloat(),
                            y2 = parts[5].trim().toFloat(),
                            minPressure = parts[6].trim().toFloat(),
                            maxDuration = parts[7].trim().toLong()
                        ))
                    }
                }

                // Save to default config
                val configContent = StringBuilder()
                configContent.append("# InputBlocker Community Preset\n")
                configContent.append("enabled=1\n")
                configContent.append("lsposed_mode=0\n\n")
                configContent.append("# Blocked regions:\n")
                for (region in regions) {
                    configContent.append("$region\n")
                }
                
                InputBlockerServiceManager.saveConfig(context, "default", configContent.toString())
                onComplete(true, "Preset imported successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import from URL: $urlString", e)
                onComplete(false, "Import failed: ${e.message}")
            }
        }.start()
    }
}
