package com.inputblocker.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.util.ArrayList

class SensingActivity : Activity() {

    companion object {
        private const val TAG = "InputBlocker-Sensing"
        var capturedTouches = ArrayList<Pair<Float, Float>>()
        var detectionDurationMs = 30000L // Default 30s
    }

    private lateinit var rootLayout: FrameLayout
    private lateinit var timerText: TextView
    private lateinit var tapCounterText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStop: Button
    private lateinit var heatmapView: HeatmapView
    private var startTime = 0L
    private var handler: Handler? = null

    private var origLockscreenSetting: String? = null
    private var lockscreenRestored = false
    private var lastCapturedTime = 0L
    private var lastNx = -1f
    private var lastNy = -1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            origLockscreenSetting = savedInstanceState.getString("SAVED_ORIG_LOCKSCREEN_SETTING")
            lockscreenRestored = savedInstanceState.getBoolean("SAVED_LOCKSCREEN_RESTORED", false)
        } else {
            origLockscreenSetting = intent.getStringExtra("EXTRA_ORIG_LOCKSCREEN_SETTING")
        }

        // Set window flags to stay visible over keyguard and keep display alive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KeyguardManager::class.java)
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Full screen, black background
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        heatmapView = HeatmapView(this)

        // Timer text — centered
        timerText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 24f
            text = "Sensing Ghost Taps..."
            gravity = Gravity.CENTER
        }

        // Tap counter — top-left overlay
        tapCounterText = TextView(this).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 16f
            text = "Taps captured: 0"
            setPadding(24, 48, 24, 0)
        }

        // Progress bar — bottom
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = (detectionDurationMs / 1000).toInt()
            progress = 0
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                24,
                Gravity.BOTTOM
            ).apply { setMargins(48, 0, 48, 100) }
        }

        // Stop & Review button — bottom-right, visible after first tap
        btnStop = Button(this).apply {
            text = "Stop & Review"
            visibility = View.GONE
            setOnClickListener { stopAndReview() }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply { setMargins(0, 0, 48, 140) }
        }

        rootLayout.addView(heatmapView)
        rootLayout.addView(timerText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        rootLayout.addView(tapCounterText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ))
        rootLayout.addView(progressBar)
        rootLayout.addView(btnStop)

        setContentView(rootLayout)

        capturedTouches.clear()

        val performPowerCycle = intent.getBooleanExtra("EXTRA_PERFORM_POWER_CYCLE", false)
        if (performPowerCycle && savedInstanceState == null) {
            timerText.text = "Cycling screen power..."
            Thread {
                try {
                    Thread.sleep(300) // Brief pause to ensure activity window attached
                    val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                    Log.i(TAG, "Executing power off event...")
                    InputBlockerServiceManager.runRootCommand("input keyevent 26") // Screen off

                    // Adaptive screen-state check: poll up to 2500ms for screen to power down
                    val pollStart = System.currentTimeMillis()
                    while (pm?.isInteractive == true && System.currentTimeMillis() - pollStart < 2500L) {
                        Thread.sleep(100)
                    }

                    Log.i(TAG, "Executing wakeup event...")
                    InputBlockerServiceManager.runRootCommand("input keyevent KEYCODE_WAKEUP") // Wake

                    // Adaptive check: poll up to 1500ms for screen to wake up
                    val wakeStart = System.currentTimeMillis()
                    while (pm?.isInteractive == false && System.currentTimeMillis() - wakeStart < 1500L) {
                        Thread.sleep(100)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Power cycle failed: ${e.message}")
                }
                runOnUiThread {
                    startTime = System.currentTimeMillis()
                    startCountdown()
                }
            }.start()
        } else {
            startTime = System.currentTimeMillis()
            startCountdown()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val now = System.currentTimeMillis()
        val action = event.actionMasked

        // Capture discrete taps, partial touches, stationary stuck touches, and moves
        if (action == MotionEvent.ACTION_DOWN ||
            action == MotionEvent.ACTION_POINTER_DOWN ||
            action == MotionEvent.ACTION_MOVE ||
            action == MotionEvent.ACTION_HOVER_MOVE) {

            val metrics = resources.displayMetrics
            val width = if (metrics.widthPixels > 0) metrics.widthPixels.toFloat() else 1f
            val height = if (metrics.heightPixels > 0) metrics.heightPixels.toFloat() else 1f

            val nx = (event.x / width).coerceIn(0f, 1f)
            val ny = (event.y / height).coerceIn(0f, 1f)

            // Throttle capturing: capture if at least 30ms passed OR position shifted by > 0.002
            val dt = now - lastCapturedTime
            val dx = Math.abs(nx - lastNx)
            val dy = Math.abs(ny - lastNy)

            if (dt > 30 || dx > 0.002f || dy > 0.002f || action == MotionEvent.ACTION_DOWN) {
                lastCapturedTime = now
                lastNx = nx
                lastNy = ny

                capturedTouches.add(Pair(nx, ny))
                heatmapView.addPoint(nx, ny)
                tapCounterText.text = "Taps captured: ${capturedTouches.size}"
                if (capturedTouches.size == 1) {
                    btnStop.visibility = View.VISIBLE
                }
                Log.d(TAG, "Captured touch at normalized ($nx, $ny) [action=$action]")
            }
        }
        return true // Consume all touches
    }

    private fun startCountdown() {
        handler = Handler(Looper.getMainLooper())
        handler?.post(object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (detectionDurationMs - elapsed).coerceAtLeast(0)
                val secondsElapsed = (elapsed / 1000).toInt()

                timerText.text = if (remaining > 0) {
                    "Sensing... ${remaining / 1000}s remaining"
                } else {
                    "Sensing Complete!"
                }
                progressBar.progress = secondsElapsed

                if (remaining > 0) {
                    handler?.postDelayed(this, 1000)
                } else {
                    launchReview()
                }
            }
        })
    }

    private fun stopAndReview() {
        handler?.removeCallbacksAndMessages(null)
        launchReview()
    }

    private fun launchReview() {
        restoreLockscreenSettingSilently()
        val intent = Intent(this@SensingActivity, DetectionReviewActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun restoreLockscreenSettingSilently() {
        if (lockscreenRestored) return
        lockscreenRestored = true
        val orig = origLockscreenSetting ?: return
        Thread {
            try {
                if (InputBlockerServiceManager.hasRootAccess()) {
                    InputBlockerServiceManager.runRootCommand("settings put secure lockscreen.disabled $orig")
                    Log.i(TAG, "Silently restored lockscreen.disabled setting to $orig")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore lockscreen setting: ${e.message}")
            }
        }.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("SAVED_ORIG_LOCKSCREEN_SETTING", origLockscreenSetting)
        outState.putBoolean("SAVED_LOCKSCREEN_RESTORED", lockscreenRestored)
    }

    override fun onDestroy() {
        super.onDestroy()
        restoreLockscreenSettingSilently()
        handler?.removeCallbacksAndMessages(null)
    }

    class HeatmapView(context: android.content.Context) : View(context) {
        private val points = mutableListOf<Pair<Float, Float>>()
        private val paint = Paint().apply {
            color = Color.RED
            alpha = 60
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        fun addPoint(nx: Float, ny: Float) {
            points.add(Pair(nx, ny))
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            for (p in points) {
                canvas.drawCircle(p.first * w, p.second * h, 20f, paint)
            }
        }
    }
}
