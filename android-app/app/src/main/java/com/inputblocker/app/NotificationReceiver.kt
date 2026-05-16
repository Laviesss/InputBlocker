package com.inputblocker.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TOGGLE_BLOCKING = "com.inputblocker.ACTION_TOGGLE_BLOCKING"
        const val ACTION_SAFE_MODE = "com.inputblocker.ACTION_SAFE_MODE"
        const val ACTION_SYNC = "com.inputblocker.ACTION_SYNC"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("NotificationReceiver", "Received action: $action")

        when (action) {
            ACTION_TOGGLE_BLOCKING -> {
                // We don't know current state here, so we fetch it from prefs
                val prefs = context.getSharedPreferences("InputBlockerPrefs", Context.MODE_PRIVATE)
                val currentStatus = prefs.getBoolean("enabled", true)
                val newStatus = !currentStatus
                
                prefs.edit().putBoolean("enabled", newStatus).apply()
                
                val cmd = if (newStatus) "enabled=1" else "enabled=0"
                InputBlockerServiceManager.runRootCommand("sed -i 's/^enabled=.*/$cmd/' /data/adb/modules/inputblocker/config/blocked_regions.conf")
                InputBlockerServiceManager.runRootCommand("am broadcast -a com.inputblocker.RELOAD")
            }
            ACTION_SAFE_MODE -> {
                // Force Safe Mode: disable and clear regions
                InputBlockerServiceManager.runRootCommand("sed -i 's/^enabled=.*/enabled=0/' /data/adb/modules/inputblocker/config/blocked_regions.conf")
                InputBlockerServiceManager.runRootCommand("sed -i 's/^force_safe_mode=.*/force_safe_mode=1/' /data/adb/modules/inputblocker/config/blocked_regions.conf")
                
                // Clear regions manually using a shell script approach (truncating the region list)
                // This is tricky with sed, so we just call a reload and handle in service.sh if needed
                // For now, we'll trigger a reload and let the app handle the actual clear if it's open
                InputBlockerServiceManager.runRootCommand("am broadcast -a com.inputblocker.RELOAD")
            }
            ACTION_SYNC -> {
                InputBlockerServiceManager.runRootCommand("am broadcast -a com.inputblocker.RELOAD")
            }
        }
    }
}
