package com.inputblocker.app

import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object UpdateChecker {
    
    private const val TAG = "InputBlocker-Update"
    private const val GITHUB_API = "https://api.github.com/repos/Laviesss/InputBlocker/releases/latest"
    
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    
    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val body: String,
        val publishedAt: String
    )
    
    interface UpdateCallback {
        fun onUpdateAvailable(info: UpdateInfo, currentVersion: String)
        fun onNoUpdateAvailable(currentVersion: String)
        fun onError(error: String)
    }
    
    fun checkForUpdate(callback: UpdateCallback) {
        executor.execute {
            try {
                val currentVersion = getCurrentVersion()
                // App uses GitHub API for updates as requested
                val updateInfo = fetchLatestRelease()
                
                if (updateInfo != null) {
                    if (isNewerVersion(updateInfo.version, currentVersion)) {
                        callback.onUpdateAvailable(updateInfo, currentVersion)
                    } else {
                        callback.onNoUpdateAvailable(currentVersion)
                    }
                } else {
                    callback.onNoUpdateAvailable(currentVersion)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                callback.onError(e.message ?: "Unknown error")
            }
        }
    }

    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = App.instance.packageManager.getPackageInfo(App.instance.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun fetchLatestRelease(): UpdateInfo? {
        return try {
            val url = URL(GITHUB_API)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "InputBlocker-Android")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(reader)
                
                UpdateInfo(
                    version = json.optString("tag_name", "").removePrefix("v"),
                    releaseUrl = json.optString("html_url", ""),
                    body = json.optString("body", ""),
                    publishedAt = json.optString("published_at", "")
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch release", e)
            null
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLen = maxOf(latestParts.size, currentParts.size)
        
        for (i in 0 until maxLen) {
            val latestNum = latestParts.getOrElse(i) { 0 }
            val currentNum = currentParts.getOrElse(i) { 0 }
            
            when {
                latestNum > currentNum -> return true
                latestNum < currentNum -> return false
            }
        }
        
        return false
    }
}

    
    fun checkForUpdate(callback: UpdateCallback) {
        executor.execute {
            try {
                val currentVersion = getCurrentVersion()
                
                // 1. Try to get the update URL from module.prop
                val updateUrl = getUpdateUrlFromModuleProp()
                
                val updateInfo = if (updateUrl != null) {
                    fetchUpdateFromJson(updateUrl) ?: fetchLatestRelease()
                } else {
                    fetchLatestRelease()
                }
                
                if (updateInfo != null) {
                    if (isNewerVersion(updateInfo.version, currentVersion)) {
                        callback.onUpdateAvailable(updateInfo, currentVersion)
                    } else {
                        callback.onNoUpdateAvailable(currentVersion)
                    }
                } else {
                    callback.onNoUpdateAvailable(currentVersion)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                callback.onError(e.message ?: "Unknown error")
            }
        }
    }

    private fun getUpdateUrlFromModuleProp(): String? {
        return try {
            val propFile = File(InputBlockerServiceManager.getModulePath(App.instance) + "/module.prop")
            if (!propFile.exists()) return null
            
            propFile.readLines().find { it.startsWith("updateJson=") }
                ?.substringAfter("updateJson=")?.trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchUpdateFromJson(urlStr: String): UpdateInfo? {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(reader)
                
                UpdateInfo(
                    version = json.optString("version", ""),
                    releaseUrl = json.optString("url", ""),
                    body = json.optString("changelog", ""),
                    isCritical = json.optBoolean("is_critical", false)
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "JSON update fetch failed: ${e.message}")
            null
        }
    }

    private fun fetchLatestRelease(): UpdateInfo? {
        return try {
            val url = URL(FALLBACK_GITHUB_API)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "InputBlocker-Android")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(reader)
                
                UpdateInfo(
                    version = json.optString("tag_name", "").removePrefix("v"),
                    releaseUrl = json.optString("html_url", ""),
                    body = json.optString("body", ""),
                    publishedAt = json.optString("published_at", "")
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "GitHub API fetch failed: ${e.message}")
            null
        }
    }

    private fun getCurrentVersion(): String {
        return try {
            val moduleProp = File(InputBlockerServiceManager.getModulePath(App.instance) + "/module.prop")
            if (moduleProp.exists()) {
                moduleProp.readLines().firstOrNull { it.startsWith("version=") }
                    ?.substringAfter("=")
                    ?.removePrefix("v")
                    ?.trim() ?: "1.0.0"
            } else {
                "1.0.0"
            }
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLen = maxOf(latestParts.size, currentParts.size)
        
        for (i in 0 until maxLen) {
            val latestNum = latestParts.getOrElse(i) { 0 }
            val currentNum = currentParts.getOrElse(i) { 0 }
            
            when {
                latestNum > currentNum -> return true
                latestNum < currentNum -> return false
            }
        }
        
        return false
    }
}

