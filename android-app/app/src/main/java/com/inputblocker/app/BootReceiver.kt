package com.inputblocker.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "InputBlocker-Boot"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action ||
            Intent.ACTION_LOCKED_BOOT_COMPLETED == intent.action) {
            
            Log.i(TAG, "Boot completed, starting InputBlocker services...")
            InputBlockerServiceManager.startServices(context)
        }
    }
}
