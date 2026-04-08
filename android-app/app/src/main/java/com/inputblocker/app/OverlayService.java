package com.inputblocker.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {

    private static final String TAG = "InputBlocker-Overlay";
    private static final String CHANNEL_ID = "InputBlockerOverlay";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private TouchBlockView touchBlockView;
    private List<Region> regions = new ArrayList<>();
    private boolean isEnabled = true;
    private boolean forceSafeMode = false;

    private BroadcastReceiver configReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.i(TAG, "OverlayService starting...");
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        loadConfig();
        createOverlayView();
        registerConfigReceiver();
        
        Log.i(TAG, "OverlayService ready - regions: " + regions.size() + ", enabled: " + isEnabled);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "InputBlocker Active",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows when InputBlocker is blocking touches");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String statusText;
        if (forceSafeMode) {
            statusText = "Safe Mode (Disabled) - Reset to enable";
        } else if (isEnabled) {
            statusText = "Blocking " + regions.size() + " region(s)";
        } else {
            statusText = "Blocking disabled";
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("InputBlocker")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void loadConfig() {
        regions.clear();
        
        File configFile = new File(InputBlockerServiceManager.getConfigFile(this));
        if (!configFile.exists()) {
            Log.w(TAG, "Config file not found");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("enabled=")) {
                    isEnabled = line.substring(8).equals("1");
                    Log.i(TAG, "Config enabled: " + isEnabled);
                    continue;
                }

                if (line.startsWith("force_safe_mode=")) {
                    forceSafeMode = line.substring(15).equals("1");
                    if (forceSafeMode) {
                        isEnabled = false;
                        Log.i(TAG, "Safe mode is active - blocking disabled");
                    }
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length == 4) {
                    try {
                        Region region = new Region();
                        region.x1 = Integer.parseInt(parts[0].trim());
                        region.y1 = Integer.parseInt(parts[1].trim());
                        region.x2 = Integer.parseInt(parts[2].trim());
                        region.y2 = Integer.parseInt(parts[3].trim());
                        regions.add(region);
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid region line: " + line);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading config", e);
        }
        
        Log.i(TAG, "Loaded " + regions.size() + " regions");
    }

    private void createOverlayView() {
        if (windowManager == null) {
            Log.e(TAG, "WindowManager is null");
            return;
        }

        touchBlockView = new TouchBlockView(this);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        
        touchBlockView.setRegions(regions);
        touchBlockView.setEnabled(isEnabled && !forceSafeMode);

        try {
            windowManager.addView(touchBlockView, params);
            Log.i(TAG, "Overlay view added");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay view", e);
        }
    }

    private void registerConfigReceiver() {
        configReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.i(TAG, "Received broadcast: " + action);
                
                if ("com.inputblocker.RELOAD".equals(action)) {
                    reloadConfig();
                } else if ("com.inputblocker.DISABLE".equals(action)) {
                    disableBlocking();
                } else if ("com.inputblocker.ENABLE".equals(action)) {
                    boolean forceSafe = intent.getBooleanExtra("force_safe", true);
                    enableBlocking(forceSafe);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.inputblocker.RELOAD");
        filter.addAction("com.inputblocker.DISABLE");
        filter.addAction("com.inputblocker.ENABLE");
        
        try {
            registerReceiver(configReceiver, filter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register receiver", e);
        }
    }

    private void reloadConfig() {
        loadConfig();
        
        if (touchBlockView != null) {
            touchBlockView.setRegions(regions);
            touchBlockView.setEnabled(isEnabled && !forceSafeMode);
        }
        
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, createNotification());
        }
        
        Log.i(TAG, "Config reloaded");
    }

    private void disableBlocking() {
        isEnabled = false;
        
        if (touchBlockView != null) {
            touchBlockView.setEnabled(false);
        }
        
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, createNotification());
        }
        
        Toast.makeText(this, "InputBlocker disabled", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Blocking disabled");
    }

    private void enableBlocking(boolean clearSafeMode) {
        if (clearSafeMode) {
            forceSafeMode = false;
            clearSafeModeFlag();
        }
        
        isEnabled = true;
        
        if (touchBlockView != null) {
            touchBlockView.setEnabled(true);
        }
        
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, createNotification());
        }
        
        Toast.makeText(this, "InputBlocker enabled", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Blocking enabled");
    }

    private void clearSafeModeFlag() {
        try {
            File configFile = new File(InputBlockerServiceManager.getConfigFile(this));
            if (configFile.exists()) {
                StringBuilder content = new StringBuilder();
                BufferedReader reader = new BufferedReader(new FileReader(configFile));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("force_safe_mode=")) {
                        content.append(line).append("\n");
                    }
                }
                reader.close();
                
                FileWriter writer = new FileWriter(configFile);
                writer.write(content.toString());
                writer.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear safe mode flag", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        
        if (intent != null) {
            String action = intent.getAction();
            if ("RELOAD".equals(action)) {
                reloadConfig();
            } else if ("DISABLE".equals(action)) {
                disableBlocking();
            } else if ("ENABLE".equals(action)) {
                enableBlocking(true);
            }
        }
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (configReceiver != null) {
            try {
                unregisterReceiver(configReceiver);
            } catch (Exception ignored) {}
        }
        
        if (touchBlockView != null && windowManager != null) {
            try {
                windowManager.removeView(touchBlockView);
            } catch (Exception ignored) {}
        }
        
        Log.i(TAG, "OverlayService destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static class Region {
        public int x1, y1, x2, y2;
    }

    public class TouchBlockView extends View {
        private Paint blockPaint;
        private Paint borderPaint;
        private Paint textPaint;
        private Paint safeModePaint;
        private List<Region> regions = new ArrayList<>();
        private boolean enabled = true;

        public TouchBlockView(Context context) {
            super(context);
            init();
        }

        private void init() {
            blockPaint = new Paint();
            blockPaint.setColor(Color.parseColor("#1A00FF00"));
            blockPaint.setStyle(Paint.Style.FILL);

            borderPaint = new Paint();
            borderPaint.setColor(Color.parseColor("#00FF00"));
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(4);

            textPaint = new Paint();
            textPaint.setColor(Color.parseColor("#00FF00"));
            textPaint.setTextSize(36);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setFakeBoldText(true);

            safeModePaint = new Paint();
            safeModePaint.setColor(Color.parseColor("#FFFF00"));
            safeModePaint.setTextSize(48);
            safeModePaint.setTextAlign(Paint.Align.CENTER);
            safeModePaint.setFakeBoldText(true);
        }

        public void setRegions(List<Region> regions) {
            this.regions = new ArrayList<>(regions);
            invalidate();
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (!enabled) {
                canvas.drawColor(Color.TRANSPARENT);
                
                if (forceSafeMode) {
                    canvas.drawText("SAFE MODE", canvas.getWidth() / 2f, 100, safeModePaint);
                    canvas.drawText("(Blocking Disabled)", canvas.getWidth() / 2f, 150, safeModePaint);
                }
                return;
            }

            for (Region region : regions) {
                android.graphics.RectF rect = new android.graphics.RectF(
                        region.x1, region.y1, region.x2, region.y2
                );
                
                canvas.drawRect(rect, blockPaint);
                canvas.drawRect(rect, borderPaint);
            }

            if (!regions.isEmpty()) {
                float padding = 10;
                canvas.drawText("BLOCKED: " + regions.size() + " region(s)", 
                        padding, 50, textPaint);
            } else {
                canvas.drawText("No regions configured", 10, 50, textPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!enabled || regions.isEmpty()) {
                return false;
            }

            float x = event.getX();
            float y = event.getY();

            if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {
                
                for (Region region : regions) {
                    if (x >= region.x1 && x <= region.x2 &&
                        y >= region.y1 && y <= region.y2) {
                        
                        Log.d(TAG, "Blocked touch at (" + x + "," + y + ")");
                        return true;
                    }
                }
            }

            return false;
        }
    }
}
