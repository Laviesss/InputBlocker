package com.inputblocker.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.ArrayList

class SensingActivity : Activity() {

    companion object {
        private const val TAG = "InputBlocker-Sensing"
        var capturedTouches = ArrayList<Pair<Float, Float>>()
        var detectionDurationMs = 30000L // Default 30s
    }

    private lateinit var rootLayout: FrameLayout
    private lateinit var timerText: TextView
    private lateinit var heatmapView: HeatmapView
    private var startTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen, black background
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        heatmapView = HeatmapView(this)

        timerText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 24f
            text = "Sensing Ghost Taps... Please wait"
            gravity = android.view.Gravity.CENTER
        }

        rootLayout.addView(heatmapView)
        rootLayout.addView(timerText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER
        ))

        setContentView(rootLayout)

        // Start detection
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
            Log.d(TAG, "Captured touch at normalized ($nx, $ny)")
        }
        return true // Consume all touches
    }


    private fun startCountdown() {
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (detectionDurationMs - elapsed).coerceAtLeast(0)
                
                timerText.text = "Sensing Ghost Taps... ${remaining / 1000}s remaining"
                
                if (remaining > 0) {
                    handler.postDelayed(this, 1000)
                } else {
                    timerText.text = "Sensing Complete!"
                    val intent = Intent(this@SensingActivity, DetectionReviewActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        })
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
            }
        })
    }

    private fun finishDetection() {
        val intent = Intent(this, DetectionReviewActivity::class.java)
        // We've already captured touches in the companion object, 
        // but we pass a flag or the size to trigger processing.
        intent.putExtra("points_captured", true)
        startActivity(intent)
        
        Toast.makeText(this, "Sensing complete. Reviewing results...", Toast.LENGTH_LONG).show()
        finish()
    }
}
