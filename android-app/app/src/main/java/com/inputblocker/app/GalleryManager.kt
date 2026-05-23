package com.inputblocker.app

import android.content.Context
import android.util.Log
import com.inputblocker.shared.Region
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Senior Engineering: Community Preset Gallery System.
 * Fetches verified blocking configurations from GitHub Pages.
 */
object GalleryManager {

    private const val TAG = "InputBlocker-Gallery"
    private const val GALLERY_URL = "https://laviesss.github.io/InputBlocker/gallery.json"
    private val executor = Executors.newSingleThreadExecutor()

    data class PresetMetadata(
        val id: String,
        val deviceModel: String,
        val description: String,
        val regionCount: Int,
        val downloadUrl: String
    )

    interface GalleryCallback {
        fun onPresetsLoaded(presets: List<PresetMetadata>)
        fun onError(message: String)
    }

    interface ImportCallback {
        fun onSuccess()
        fun onError(message: String)
    }

    fun fetchPresets(callback: GalleryCallback) {
        executor.execute {
            try {
                val url = URL(GALLERY_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val array = JSONArray(json)
                    val presets = mutableListOf<PresetMetadata>()
                    
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        presets.add(PresetMetadata(
                            id = obj.getString("id"),
                            deviceModel = obj.getString("model"),
                            description = obj.getString("description"),
                            regionCount = obj.getInt("count"),
                            downloadUrl = obj.getString("url")
                        ))
                    }
                    callback.onPresetsLoaded(presets)
                } else {
                    callback.onError("Server returned ${conn.responseCode}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch presets", e)
                callback.onError(e.message ?: "Network error")
            }
        }
    }

    fun importPreset(context: Context, preset: PresetMetadata, callback: ImportCallback) {
        executor.execute {
            try {
                val url = URL(preset.downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = conn.inputStream.bufferedReader().readText()
                    val newRegions = mutableListOf<Region>()
                    
                    content.lineSequence().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            Region.fromString(trimmed)?.let { newRegions.add(it) }
                        }
                    }
                    
                    if (newRegions.isNotEmpty()) {
                        saveMergedConfig(context, newRegions)
                        callback.onSuccess()
                    } else {
                        callback.onError("Preset contains no valid regions")
                    }
                } else {
                    callback.onError("Download failed: ${conn.responseCode}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                callback.onError(e.message ?: "Import error")
            }
        }
    }

    private fun saveMergedConfig(context: Context, newRegions: List<Region>) {
        val configBuilder = StringBuilder()
        configBuilder.append("# Community Preset Imported\n")
        configBuilder.append("enabled=1\n\n")
        for (region in newRegions) {
            configBuilder.append("$region\n")
        }
        InputBlockerServiceManager.saveConfig(context, "default", configBuilder.toString())
    }
}
