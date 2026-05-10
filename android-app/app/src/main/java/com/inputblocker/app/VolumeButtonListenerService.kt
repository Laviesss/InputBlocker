package com.inputblocker.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.util.Timer
import java.util.TimerTask

class VolumeButtonListenerService : Service() {

    companion object {
        private const val TAG = "InputBlocker-KillSwitch"
        private const val REQUIRED_DOWN_COUNT = 3
        private const val REQUIRED_UP_COUNT = 3
        private const val TIMEOUT_MS = 5000L
    }
    
    private val buttonPressTimes = mutableListOf<Long>()
    private val buttonTypes = mutableListOf<Int>()
    private var timeoutTimer: Timer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var volumeReceiver: BroadcastReceiver? = null
    private var buttonReceiver: BroadcastReceiver? = null
    private var audioManager: AudioManager? = null
    private var isListening = false
    private var lastVolume = -1
    
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): VolumeButtonListenerService = this@VolumeButtonListenerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "InputBlocker::VolumeListener")
        wakeLock?.acquire(10 * 60 * 1000L)
        
        lastVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    handleVolumeChange()
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        
        try {
            registerReceiver(volumeReceiver, filter)
            
            buttonReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.inputblocker.BUTTON_PRESSED") {
                        val key = intent.getStringExtra("key") ?: "unknown"
                        handleButtonPressed(key)
                    }
                }
            }
            val btnFilter = IntentFilter("com.inputblocker.BUTTON_PRESSED")
            registerReceiver(buttonReceiver, btnFilter)
            
            isListening = true
            Log.i(TAG, "Volume and Power button listeners started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver", e)
        }
    }

    private fun handleVolumeChange() {
        val now = System.currentTimeMillis()
        if (timeoutTimer != null) timeoutTimer?.cancel()
        
        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        if (currentVol == lastVolume) return
        
        val type = if (currentVol < lastVolume) "VOL_DOWN" else "VOL_UP"
        lastVolume = currentVol
        
        handleButtonPressed(type)
    }

    private fun handleButtonPressed(key: String) {
        val now = System.currentTimeMillis()
        if (timeoutTimer != null) timeoutTimer?.cancel()
        
        buttonPressTimes.add(now)
        buttonTypes.add(if (key == "POWER") 1 else 0)
        
        if (buttonPressTimes.size > 10) {
            buttonPressTimes.removeAt(0)
            buttonTypes.removeAt(0)
        }
        
        checkPatterns()
        
        timeoutTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    buttonPressTimes.clear()
                    buttonTypes.clear()
                    Log.i(TAG, "Pattern timeout - resetting counts")
                }
            }, TIMEOUT_MS)
        }
    }

    private fun checkPatterns() {
        if (buttonPressTimes.size < 3) return
        
        // Check for Volume Down x3 -> Volume Up x3 (Existing)
        // (This is handled by handleVolumeChange, but we can integrate it here if we want)
        
        // Check for Power Button x5 (New Advanced Kill Switch)
        if (buttonPressTimes.size >= 5) {
            val last5 = buttonTypes.takeLast(5)
            if (last5.all { it == 1 }) {
                Log.i(TAG, "Power button x5 detected! Triggering emergency disable")
                triggerEmergencyDisable()
            }
        }
    }

    private fun triggerEmergencyDisable() {
        val intent = Intent("com.inputblocker.DISABLE")
        sendBroadcast(intent)
        Toast.makeText(this, "EMERGENCY DISABLE: Power sequence detected", Toast.LENGTH_LONG).show()
    }
        }
        
        lastVolume = currentVolume
    }

    private fun onVolumeButtonPressed(isUp: Boolean) {
        val now = System.currentTimeMillis()
        
        buttonPressTimes.add(now)
        buttonTypes.add(if (isUp) 1 else 0)
        
        resetTimeoutTimer()
        
        Log.d(TAG, "Button pressed: ${if (isUp) "UP" else "DOWN"} (sequence: ${getSequenceString()})")
        
        if (checkSequence()) {
            triggerKillSwitch()
        }
    }

    private fun getSequenceString(): String {
        return buttonTypes.joinToString("") { if (it == 0) "D" else "U" }
    }

    private fun checkSequence(): Boolean {
        if (buttonTypes.size < 6) return false
        
        val firstTime = buttonPressTimes[0]
        var downCount = 0
        var upCount = 0
        
        for (i in buttonPressTimes.indices) {
            if (buttonPressTimes[i] - firstTime > TIMEOUT_MS) {
                buttonPressTimes.clear()
                buttonTypes.clear()
                return false
            }
            
            if (buttonTypes[i] == 0) downCount++ else upCount++
        }
        
        if (downCount == REQUIRED_DOWN_COUNT && upCount == REQUIRED_UP_COUNT) {
            val lastTime = buttonPressTimes.last()
            if (lastTime - firstTime <= TIMEOUT_MS) {
                return true
            }
        }
        
        if (buttonTypes.size > 6) {
            buttonPressTimes.clear()
            buttonTypes.clear()
        }
        
        return false
    }

    private fun resetTimeoutTimer() {
        timeoutTimer?.cancel()
        
        timeoutTimer = Timer()
        timeoutTimer?.schedule(object : TimerTask() {
            override fun run() {
                Log.d(TAG, "Sequence timeout - clearing")
                buttonPressTimes.clear()
                buttonTypes.clear()
            }
        }, TIMEOUT_MS + 100)
    }

    private fun triggerKillSwitch() {
        Log.i(TAG, "KILL SWITCH ACTIVATED!")
        
        buttonPressTimes.clear()
        buttonTypes.clear()
        
        disableBlocking()
        
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let {
            if (it.hasVibrator()) {
                val pattern = longArrayOf(0, 200, 100, 200, 100, 200)
                it.vibrate(pattern, -1)
            }
        }
    }

    private fun disableBlocking() {
        try {
            val configFile = File(InputBlockerServiceManager.getConfigFile(this))
            if (configFile.exists()) {
                val lines = configFile.readLines().map { line ->
                    if (line.startsWith("enabled=")) "enabled=0" else line
                }
                FileWriter(configFile).use { it.write(lines.joinToString("\n")) }
            }
            
            val broadcastIntent = Intent("com.inputblocker.DISABLE")
            sendBroadcast(broadcastIntent)
            
            Log.i(TAG, "Blocking disabled via kill switch")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable blocking", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        volumeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) { }
        }
        
        timeoutTimer?.cancel()
        
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        
        isListening = false
        Log.i(TAG, "Volume button listener stopped")
    }

    fun isListening(): Boolean = isListening
}
