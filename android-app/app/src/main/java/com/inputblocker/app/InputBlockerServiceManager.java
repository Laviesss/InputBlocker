package com.inputblocker.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;

public class InputBlockerServiceManager {
    
    private static final String TAG = "InputBlocker-Services";
    
    public static final String NORMAL_SHUTDOWN_FLAG = "/data/local/tmp/inputblocker/normal_shutdown";
    public static final String CRASH_FLAG = "/data/local/tmp/inputblocker/crash_detected";
    
    private static String cachedModulePath = null;
    
    public static String getModulePath(Context context) {
        if (cachedModulePath != null) return cachedModulePath;
        
        String[] paths = {
            "/data/adb/modules/inputblocker",
            "/data/ksu/modules/inputblocker", 
            "/data/apatch/modules/inputblocker",
            "/su/su.d/inputblocker"
        };
        
        for (String path : paths) {
            if (new File(path).exists()) {
                cachedModulePath = path;
                Log.i(TAG, "Detected module path: " + path);
                return path;
            }
        }
        
        cachedModulePath = "/data/adb/modules/inputblocker";
        return cachedModulePath;
    }
    
    public static String getConfigFile(Context context) {
        return getModulePath(context) + "/config/blocked_regions.conf";
    }
    
    public static void startServices(Context context) {
        Log.i(TAG, "Starting InputBlocker services...");
        
        if (shouldStartInSafeMode(context)) {
            Log.i(TAG, "Starting in safe mode - blocking disabled");
        }
        
        Intent overlayIntent = new Intent(context, OverlayService.class);
        context.startForegroundService(overlayIntent);
        
        Intent volumeIntent = new Intent(context, VolumeButtonListenerService.class);
        context.startService(volumeIntent);
        
        Log.i(TAG, "Services started");
    }
    
    private static boolean shouldStartInSafeMode(Context context) {
        String configPath = getConfigFile(context);
        File configFile = new File(configPath);
        File crashFlag = new File(CRASH_FLAG);
        File shutdownFile = new File(NORMAL_SHUTDOWN_FLAG);
        
        boolean wasCleanShutdown = shutdownFile.exists();
        shutdownFile.delete();
        
        if (crashFlag.exists()) {
            crashFlag.delete();
            Log.i(TAG, "Crash flag detected - enabling safe mode");
            return true;
        }
        
        if (!wasCleanShutdown) {
            Log.i(TAG, "Unexpected shutdown detected - enabling safe mode");
            enableSafeMode(context);
            return true;
        }
        
        return false;
    }
    
    public static void enableSafeMode(Context context) {
        try {
            String configPath = getConfigFile(context);
            File configFile = new File(configPath);
            if (configFile.exists()) {
                StringBuilder content = new StringBuilder();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(configFile));
                
                String line;
                boolean foundEnabled = false;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("enabled=")) {
                        content.append("enabled=0\n");
                        foundEnabled = true;
                    } else if (line.startsWith("force_safe_mode=")) {
                        content.append("force_safe_mode=1\n");
                    } else {
                        content.append(line).append("\n");
                    }
                }
                reader.close();
                
                if (!foundEnabled) {
                    content.insert(0, "enabled=0\nforce_safe_mode=1\n\n");
                }
                
                java.io.FileWriter writer = new java.io.FileWriter(configFile);
                writer.write(content.toString());
                writer.close();
                
                Log.i(TAG, "Safe mode enabled - blocking disabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable safe mode", e);
        }
    }
    
    public static void onShutdown() {
        Log.i(TAG, "Shutdown detected");
        
        File shutdownFile = new File(NORMAL_SHUTDOWN_FLAG);
        try {
            shutdownFile.getParentFile().mkdirs();
            shutdownFile.createNewFile();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create shutdown flag", e);
        }
    }
}
