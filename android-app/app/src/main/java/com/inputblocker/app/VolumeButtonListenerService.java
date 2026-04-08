package com.inputblocker.app;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class VolumeButtonListenerService extends Service {

    private static final String TAG = "InputBlocker-KillSwitch";
    
    private static final int REQUIRED_DOWN_COUNT = 3;
    private static final int REQUIRED_UP_COUNT = 3;
    private static final long TIMEOUT_MS = 5000;
    
    private static final String CRASH_FLAG_FILE = "/data/local/tmp/inputblocker/crash_detected";
    
    private List<Long> buttonPressTimes = new ArrayList<>();
    private List<Integer> buttonTypes = new ArrayList<>();
    private Timer timeoutTimer;
    private PowerManager.WakeLock wakeLock;
    
    private BroadcastReceiver receiver;
    private AudioManager audioManager;
    private boolean isListening = false;
    
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public VolumeButtonListenerService getService() {
            return VolumeButtonListenerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "InputBlocker::VolumeListener");
        wakeLock.acquire(10 * 60 * 1000L);
        
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                
                if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
                    android.view.KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (event != null) {
                        handleMediaButton(event);
                    }
                } else if (action.equals("android.media.VOLUME_CHANGED_ACTION")) {
                    handleVolumeChange();
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_BUTTON);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        
        try {
            registerReceiver(receiver, filter);
            isListening = true;
            Log.i(TAG, "Volume button listener started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register receiver", e);
        }
    }

    private void handleMediaButton(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            
            Log.d(TAG, "Media button: " + keyCode);
            
            if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                onVolumeButtonPressed(false);
            } else if (keyCode == android.view.KeyEvent.KEYEvent.KEYCODE_VOLUME_UP) {
                onVolumeButtonPressed(true);
            }
        }
    }

    private void handleVolumeChange() {
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        Log.d(TAG, "Volume changed to: " + currentVolume);
    }

    private void onVolumeButtonPressed(boolean isUp) {
        long now = System.currentTimeMillis();
        
        buttonPressTimes.add(now);
        buttonTypes.add(isUp ? 1 : 0);
        
        resetTimeoutTimer();
        
        Log.d(TAG, "Button pressed: " + (isUp ? "UP" : "DOWN") + 
              " (sequence: " + getSequenceString() + ")");
        
        if (checkSequence()) {
            triggerKillSwitch();
        }
    }

    private String getSequenceString() {
        StringBuilder sb = new StringBuilder();
        for (Integer type : buttonTypes) {
            sb.append(type == 0 ? "D" : "U");
        }
        return sb.toString();
    }

    private boolean checkSequence() {
        if (buttonTypes.size() < 6) return false;
        
        int downCount = 0;
        int upCount = 0;
        long firstTime = buttonPressTimes.get(0);
        
        for (int i = 0; i < buttonTypes.size(); i++) {
            if (buttonPressTimes.get(i) - firstTime > TIMEOUT_MS) {
                buttonPressTimes.clear();
                buttonTypes.clear();
                return false;
            }
            
            if (buttonTypes.get(i) == 0) downCount++;
            else upCount++;
        }
        
        if (downCount == REQUIRED_DOWN_COUNT && upCount == REQUIRED_UP_COUNT) {
            long lastTime = buttonPressTimes.get(buttonPressTimes.size() - 1);
            if (lastTime - firstTime <= TIMEOUT_MS) {
                return true;
            }
        }
        
        if (buttonTypes.size() > 6) {
            buttonPressTimes.clear();
            buttonTypes.clear();
        }
        
        return false;
    }

    private void resetTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }
        
        timeoutTimer = new Timer();
        timeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "Sequence timeout - clearing");
                buttonPressTimes.clear();
                buttonTypes.clear();
            }
        }, TIMEOUT_MS + 100);
    }

    private void triggerKillSwitch() {
        Log.i(TAG, "KILL SWITCH ACTIVATED!");
        
        buttonPressTimes.clear();
        buttonTypes.clear();
        
        disableBlocking();
        
        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 200, 100, 200, 100, 200};
            vibrator.vibrate(pattern, -1);
        }
    }

    private void disableBlocking() {
        try {
            File configFile = new File(InputBlockerServiceManager.getConfigFile(this));
            if (configFile.exists()) {
                StringBuilder content = new StringBuilder();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(configFile));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("enabled=")) {
                        content.append("enabled=0\n");
                    } else {
                        content.append(line).append("\n");
                    }
                }
                reader.close();
                
                FileWriter writer = new FileWriter(configFile);
                writer.write(content.toString());
                writer.close();
            }
            
            Intent broadcastIntent = new Intent("com.inputblocker.DISABLE");
            sendBroadcast(broadcastIntent);
            
            Log.i(TAG, "Blocking disabled via kill switch");
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to disable blocking", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {}
        }
        
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        isListening = false;
        Log.i(TAG, "Volume button listener stopped");
    }

    public boolean isListening() {
        return isListening;
    }
}
