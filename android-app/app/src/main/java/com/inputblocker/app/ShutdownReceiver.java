package com.inputblocker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ShutdownReceiver extends BroadcastReceiver {
    
    private static final String TAG = "InputBlocker-Shutdown";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
            Log.i(TAG, "Shutdown detected");
            InputBlockerServiceManager.onShutdown();
        }
    }
}
