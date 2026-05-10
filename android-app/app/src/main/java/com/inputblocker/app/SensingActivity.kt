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
import java.util.ArrayList

class SensingActivity : Activity() {

    companion object {
        private const val TAG = "InputBlocker-Sensing"
        var capturedTouches = ArrayList<Pair<Float, Float>>()
        var detectionDurationMs = 30000L // Default 30s
    }

    private lateinit var rootLayout: FrameLayout
    private lateinit var timerText: TextView
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

        timerText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 24f
            text = "Sensing Ghost Taps... Please wait"
            gravity = android.view.Gravity.CENTER
        }

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
            Log.d(TAG, "Captured touch at normalized ($nx, $ny)")
        }
        return true // Consume all touches
    }

    private fun startCountdown() {
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (detectionDurationMs - elapsed) / 1000
                
                if (remaining <= 0) {
                    timerText.text = "Sensing Complete!"
                    finishDetection()
                } else {
                    timerText.text = "Sensing Ghost Taps... ${remaining}s remaining"
                    handler.postDelayed(this, 1000)
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
