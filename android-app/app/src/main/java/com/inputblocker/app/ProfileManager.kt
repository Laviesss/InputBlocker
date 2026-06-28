package com.inputblocker.app

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

object ProfileManager {

    private const val TAG = "ProfileManager"
    
    fun getProfilePath(packageName: String?): String {
        return if (packageName != null) {
            "${Constants.PROFILES_DIR}/$packageName.conf"
        } else {
            "${Constants.PROFILES_DIR}/default.conf"
        }
    }

    /**
     * Lists all available profile names by scanning the profiles directory via root.
     * Always includes "default" as a fallback.
     */
    fun getProfiles(): List<String> {
        val result = InputBlockerServiceManager.runRootCommand(
            "ls ${Constants.PROFILES_DIR}/*.conf 2>/dev/null"
        )
        val profiles = if (result.isNotBlank()) {
            result.trim().lines()
                .map { it.substringAfterLast("/").removeSuffix(".conf") }
                .filter { it.isNotBlank() && it.isNotEmpty() }
                .toMutableSet()
        } else {
            mutableSetOf()
        }
        profiles.add("default")
        return profiles.sorted()
    }

    /**
     * Returns the next profile name in cyclic order.
     * If [currentProfile] is the last in the list, wraps to the first.
     */
    fun getNextProfile(currentProfile: String): String {
        val profiles = getProfiles()
        if (profiles.size <= 1) return currentProfile
        val currentIndex = profiles.indexOf(currentProfile)
        if (currentIndex < 0 || currentIndex >= profiles.size - 1) {
            return profiles.first()
        }
        return profiles[currentIndex + 1]
    }

    fun applyProfile(packageName: String?) {
        val path = getProfilePath(packageName)
        Log.d(TAG, "Applying profile: $path")
        // The hook module (Vector/LSPosed) already handles loading based on package,
        // but we can trigger a manual reload here if needed.
    }
}
