package com.inputblocker.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ShutdownReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "InputBlocker-Shutdown"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_SHUTDOWN == intent.action) {
            Log.i(TAG, "Shutdown detected")
            InputBlockerServiceManager.onShutdown()
        }
    }
}
