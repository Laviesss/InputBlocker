package com.inputblocker.app

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class DetectionReviewActivity : Activity() {

    companion object {
        private const val TAG = "InputBlocker-Review"
    }

    private var regions = mutableListOf<Region>()
    private lateinit var rootLayout: FrameLayout
    private lateinit var reviewCanvas: ReviewCanvas
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var tvCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get proposed regions from intent
        val proposed = intent.getSerializableExtra("regions") as? ArrayList<Region>
        if (proposed == null || proposed.isEmpty()) {
            Toast.makeText(this, "No regions to review", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        regions.addAll(proposed)

        // UI Setup
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        reviewCanvas = ReviewCanvas(this)
        reviewCanvas.setRegions(regions)
        
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 50)
        }

        tvCount = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            text = "Proposed Regions: ${regions.size}"
        }

        btnSave = Button(this).apply {
            text = "Apply & Block"
            setOnClickListener { saveAndApply() }
        }

        btnCancel = Button(this).apply {
            text = "Discard"
            setOnClickListener { finish() }
        }

        controls.addView(tvCount)
        controls.addView(btnSave)
        controls.addView(btnCancel)

        rootLayout.addView(reviewCanvas)
        rootLayout.addView(controls)

        setContentView(rootLayout)
    }

    private fun saveAndApply() {
        // Save to default config
        try {
            val configFile = File(InputBlockerServiceManager.getConfigFile(this, "default"))
            configFile.parentFile?.mkdirs()
            
            val content = StringBuilder()
            content.append("enabled=1\n")
            // We assume safe_mode=0 for now or preserve it
            content.append("force_safe_mode=0\n\n")
            
            for (region in regions) {
                content.append("${region.x1},${region.y1},${region.x2},${region.y2}\n")
            }
            
            configFile.writeText(content.toString())
            
            // Trigger reload
            val intent = Intent("com.inputblocker.RELOAD")
            sendBroadcast(intent)
            
            Toast.makeText(this, "Regions applied successfully!", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save regions", e)
            Toast.makeText(this, "Error saving config", Toast.LENGTH_SHORT).show()
        }
    }

    inner class ReviewCanvas(context: Context) : View(context) {
        private val blockPaint = Paint().apply {
            color = Color.parseColor("#4DB388FF")
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint().apply {
            color = Color.parseColor("#00FF00")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private var regionsList = mutableListOf<Region>()

        fun setRegions(list: List<Region>) {
            regionsList.clear()
            regionsList.addAll(list)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            for (region in regionsList) {
                val rect = RectF(
                    region.x1 * width, region.y1 * height,
                    region.x2 * width, region.y2 * height
                )
                canvas.drawRect(rect, blockPaint)
                canvas.drawRect(rect, borderPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val nx = event.x / width
                val ny = event.y / height
                
                // Remove region if touched
                val iterator = regionsList.iterator()
                while (iterator.hasNext()) {
                    val r = iterator.next()
                    if (nx >= r.x1 && nx <= r.x2 && ny >= r.y1 && ny <= r.y2) {
                        iterator.remove()
                        tvCount.text = "Proposed Regions: ${regionsList.size}"
                        invalidate()
                        break
                    }
                }
            }
            return true
        }
    }
}
