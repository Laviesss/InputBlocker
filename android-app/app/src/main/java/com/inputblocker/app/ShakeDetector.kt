package com.inputblocker.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * Battery-efficient accelerometer-based shake detector.
 *
 * Sensitivity presets (threshold in m/s² deviation from gravity ≈9.8):
 * - LOW:   15.0 — hard shake only
 * - MEDIUM: 12.0 — normal shake (default)
 * - HIGH:    9.0 — very gentle, any movement triggers
 *
 * Lifecycle is tied to the caller — register/unregister explicitly.
 */
class ShakeDetector(
    private val context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"
        private const val GRAVITY = 9.8f

        const val SENSITIVITY_LOW = 0
        const val SENSITIVITY_MEDIUM = 1
        const val SENSITIVITY_HIGH = 2

        /** Minimum time between consecutive shake triggers */
        private const val DEBOUNCE_MS = 2000L

        /** Consecutive samples exceeding threshold to confirm a shake */
        private const val MIN_CONSECUTIVE = 3

        /** Sensor sampling rate: SENSOR_DELAY_UI ≈ 60ms */
        private const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_UI
    }

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val powerManager: PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var consecutiveShakes = 0
    @Volatile private var lastShakeTime = 0L
    @Volatile private var isRegistered = false

    /** Current sensitivity threshold, default MEDIUM */
    var sensitivity = SENSITIVITY_MEDIUM
        set(value) {
            val clamped = value.coerceIn(SENSITIVITY_LOW, SENSITIVITY_HIGH)
            if (field != clamped) {
                field = clamped
                Log.d(TAG, "Sensitivity set to $clamped (threshold=${threshold()})")
            }
        }

    fun threshold(): Float = when (sensitivity) {
        SENSITIVITY_LOW -> 15.0f
        SENSITIVITY_HIGH -> 9.0f
        else -> 12.0f
    }

    /**
     * Register sensor listener. No-op if no accelerometer or already registered.
     */
    fun register() {
        if (isRegistered) return
        if (accelerometer == null) {
            Log.w(TAG, "No accelerometer sensor — shake detection unavailable")
            return
        }
        sensorManager.registerListener(this, accelerometer, SENSOR_DELAY)
        isRegistered = true
        Log.d(TAG, "ShakeDetector registered (sensitivity=$sensitivity)")
    }

    /**
     * Unregister sensor listener. Call from onPause/onDestroy.
     */
    fun unregister() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        consecutiveShakes = 0
        Log.d(TAG, "ShakeDetector unregistered")
    }

    /**
     * Screen-off optimization: unregister when screen is off to save battery.
     * Call this from a Screen-On/Off broadcast receiver.
     */
    fun onScreenStateChanged(screenOn: Boolean) {
        if (screenOn) {
            register()
        } else {
            unregister()
        }
    }

    // ── SensorEventListener ───────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Debounce: ignore if a shake was detected recently
        val now = System.currentTimeMillis()
        if (now - lastShakeTime < DEBOUNCE_MS) return

        // High-pass filter: magnitude of deviation from gravity
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val deviation = kotlin.math.abs(magnitude - GRAVITY)

        if (deviation > threshold()) {
            consecutiveShakes++
            if (consecutiveShakes >= MIN_CONSECUTIVE) {
                lastShakeTime = now
                consecutiveShakes = 0
                Log.i(TAG, "Shake detected (deviation=$deviation)")
                mainHandler.post { onShake() }
            }
        } else {
            // Reset counter on non-shake reading
            if (consecutiveShakes > 0) {
                consecutiveShakes--
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}
