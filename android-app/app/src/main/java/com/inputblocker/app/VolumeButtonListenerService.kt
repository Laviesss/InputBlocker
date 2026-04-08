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
    
    private var receiver: BroadcastReceiver? = null
    private var audioManager: AudioManager? = null
    private var isListening = false
    
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
        
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.action?.let { action ->
                    when (action) {
                        Intent.ACTION_MEDIA_BUTTON -> {
                            val event = intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
                            event?.let { handleMediaButton(it) }
                        }
                        "android.media.VOLUME_CHANGED_ACTION" -> handleVolumeChange()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_BUTTON)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        
        try {
            registerReceiver(receiver, filter)
            isListening = true
            Log.i(TAG, "Volume button listener started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver", e)
        }
    }

    private fun handleMediaButton(event: android.view.KeyEvent) {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            
            Log.d(TAG, "Media button: $keyCode")
            
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> onVolumeButtonPressed(false)
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> onVolumeButtonPressed(true)
            }
        }
    }

    private fun handleVolumeChange() {
        val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        Log.d(TAG, "Volume changed to: $currentVolume")
    }

    private fun onVolumeButtonPressed(isUp: Boolean) {
        val now = System.currentTimeMillis()
        
        buttonPressTimes.add(now)
        buttonTypes.add(if (isUp) 1 else 0)
        
        resetTimeoutTimer()
        
        Log.d(TAG, "Button pressed: ${if (isUp) "UP" else "DOWN"} (sequence: $getSequenceString())")
        
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
        
        for (i in buttonTypes.indices) {
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
        
        receiver?.let {
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
