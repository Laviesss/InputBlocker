package com.inputblocker.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileWriter

/**
 * Senior Engineering: Battery-efficient hardware button listener.
 * Detects VOL_DOWN x3 -> VOL_UP x3 sequence to disable blocking.
 * Uses Handler-based state resets instead of heavy WakeLocks or Timers.
 */
class VolumeButtonListenerService : Service() {

    companion object {
        private const val TAG = "InputBlocker-KillSwitch"
        private const val SEQUENCE_TIMEOUT_MS = 5000L
    }
    
    private val buttonHistory = ArrayList<Int>(10)
    private val timeHistory = ArrayList<Long>(10)
    private val handler = Handler(Looper.getMainLooper())
    
    private var volumeReceiver: BroadcastReceiver? = null
    private var lastVolume = -1
    
    private val resetTask = Runnable {
        buttonHistory.clear()
        timeHistory.clear()
        Log.d(TAG, "Kill-switch sequence reset due to timeout")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        lastVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    handleVolumeEvent(am)
                }
            }
        }
        
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(volumeReceiver, filter)
        }
        
        Log.i(TAG, "Kill-switch listener active")
    }

    private fun handleVolumeEvent(am: AudioManager) {
        val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVol == lastVolume) return
        
        val type = if (currentVol < lastVolume) 0 else 1 // 0: Down, 1: Up
        lastVolume = currentVol
        
        val now = System.currentTimeMillis()
        handler.removeCallbacks(resetTask)
        
        buttonHistory.add(type)
        timeHistory.add(now)
        
        if (buttonHistory.size > 6) {
            buttonHistory.removeAt(0)
            timeHistory.removeAt(0)
        }
        
        if (checkSequence()) {
            triggerKillSwitch()
        } else {
            handler.postDelayed(resetTask, SEQUENCE_TIMEOUT_MS)
        }
    }

    private fun checkSequence(): Boolean {
        if (buttonHistory.size < 6) return false
        
        // Expected: [0, 0, 0, 1, 1, 1] (Down x3, Up x3)
        val expected = listOf(0, 0, 0, 1, 1, 1)
        if (buttonHistory != expected) return false
        
        val duration = timeHistory.last() - timeHistory.first()
        return duration <= SEQUENCE_TIMEOUT_MS
    }

    private fun triggerKillSwitch() {
        Log.w(TAG, "EMERGENCY KILL-SWITCH TRIGGERED!")
        buttonHistory.clear()
        timeHistory.clear()
        
        // Persist the disable state
        InputBlockerServiceManager.enableSafeMode(this)
        
        // Feedback
        vibrate()
        sendBroadcast(Intent("com.inputblocker.DISABLE"))
        Toast.makeText(this, "BLOCKING DISABLED: Emergency sequence detected", Toast.LENGTH_LONG).show()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 200, 100, 200, 100, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        volumeReceiver?.let { unregisterReceiver(it) }
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "Kill-switch listener stopped")
    }
}
