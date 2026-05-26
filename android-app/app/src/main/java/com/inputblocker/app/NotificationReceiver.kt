package com.inputblocker.app

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

        val configPath = InputBlockerServiceManager.getConfigFile(context, "default")

        when (action) {
            ACTION_TOGGLE_BLOCKING -> {
                val prefs = context.getSharedPreferences("InputBlockerPrefs", Context.MODE_PRIVATE)
                val currentStatus = prefs.getBoolean("enabled", true)
                val newStatus = !currentStatus
                
                prefs.edit().putBoolean("enabled", newStatus).apply()
                
                val cmd = if (newStatus) "enabled=1" else "enabled=0"
                InputBlockerServiceManager.runRootCommand("sed -i 's/^enabled=.*/$cmd/' $configPath")
                val reloadIntent = Intent("com.inputblocker.RELOAD")
                reloadIntent.setPackage(context.packageName)
                context.sendBroadcast(reloadIntent)
            }
            ACTION_SAFE_MODE -> {
                InputBlockerServiceManager.enableSafeMode(context)
                val reloadIntent = Intent("com.inputblocker.RELOAD")
                reloadIntent.setPackage(context.packageName)
                context.sendBroadcast(reloadIntent)
            }
            ACTION_SYNC -> {
                val reloadIntent = Intent("com.inputblocker.RELOAD")
                reloadIntent.setPackage(context.packageName)
                context.sendBroadcast(reloadIntent)
            }
        }
    }
}
