package com.inputblocker.app

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on and fullscreen during sensing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

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

        // Start sensing
        startTime = System.currentTimeMillis()
        capturedTouches.clear()
        startCountdown()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val nx = event.x / resources.displayMetrics.widthPixels
            val ny = event.y / resources.displayMetrics.heightPixels
            capturedTouches.add(Pair(nx, ny))
            heatmapView.addPoint(nx, ny)
            tapCounterText.text = "Taps captured: ${capturedTouches.size}"
            if (capturedTouches.size == 1) {
                btnStop.visibility = View.VISIBLE
            }
            Log.d(TAG, "Captured touch at normalized ($nx, $ny)")
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
        val intent = Intent(this@SensingActivity, DetectionReviewActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
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
