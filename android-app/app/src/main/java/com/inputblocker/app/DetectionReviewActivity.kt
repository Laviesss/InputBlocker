package com.inputblocker.app

import com.inputblocker.shared.ClusterUtils
import com.inputblocker.shared.Region
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
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
import android.widget.FrameLayout
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.ArrayList

class DetectionReviewActivity : Activity() {

    companion object {
        private const val TAG = "InputBlocker-Review"
    }

    private var regions = mutableListOf<Region>()
    private var clusterTapCounts = mutableListOf<Int>()
    private lateinit var rootLayout: FrameLayout
    private lateinit var reviewCanvas: ReviewCanvas
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnFineTune: Button
    private lateinit var tvCount: TextView
    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Process captured points into suggested regions
        val points = SensingActivity.capturedTouches
        if (points.isEmpty()) {
            Toast.makeText(this, "No ghost taps detected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val prefs = getSharedPreferences("InputBlockerPrefs", Context.MODE_PRIVATE)
        val eps = prefs.getFloat("dbscan_eps", 0.03f)
        val minPts = prefs.getInt("dbscan_minpts", 3)

        val clustered = ClusterUtils.clusterPairs(points, eps, minPts)
        regions.addAll(clustered.map { ClusterUtils.calculateBoundingBox(it) })
        clusterTapCounts.addAll(clustered.map { it.size })
        
        if (regions.isEmpty()) {
            Toast.makeText(this, "No clear clusters found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. UI Setup
        setupUI()

        // 3. Show the 3-option confirmation dialog
        showConfirmationDialog()
    }

    private fun setupUI() {
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        reviewCanvas = ReviewCanvas(this)
        reviewCanvas.setRegions(regions, clusterTapCounts)
        
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 50)
            visibility = View.GONE // Hidden until 'Refine' is chosen
        }

        tvCount = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            val totalTaps = clusterTapCounts.sum()
            text = "$totalTaps taps in ${regions.size} region${if (regions.size != 1) "s" else ""}"
        }

        btnSave = Button(this).apply {
            text = "Apply & Block"
            setOnClickListener { saveAndApply() }
        }

        btnFineTune = Button(this).apply {
            text = "Fine-tune Clustering"
            setOnClickListener { showTuningDialog() }
        }

        btnCancel = Button(this).apply {
            text = "Discard"
            setOnClickListener { finish() }
        }

        controls.addView(tvCount)
        controls.addView(btnFineTune)
        controls.addView(btnSave)
        controls.addView(btnCancel)

        rootLayout.addView(reviewCanvas)
        rootLayout.addView(controls)

        setContentView(rootLayout as View)
    }

    private fun showConfirmationDialog() {
        val clusterDetails = buildString {
            append("Found ${regions.size} ghost tap area${if (regions.size != 1) "s" else ""}:\n")
            for ((i, count) in clusterTapCounts.withIndex()) {
                val r = regions[i]
                val tapCount = count
                val w = (r.x2 - r.x1) * 100
                val h = (r.y2 - r.y1) * 100
                append("  #${i + 1}: $tapCount tap${if (tapCount != 1) "s" else ""} (${"%.0f".format(w)}×${"%.0f".format(h)}%)\n")
            }
            append("\nWhat would you like to do?")
        }

        AlertDialog.Builder(this)
            .setTitle("Ghost Taps Detected")
            .setMessage(clusterDetails.trimEnd())
            .setPositiveButton("Refine") { _, _ ->
                enterEditingMode()
            }
            .setNeutralButton("Accept All") { _, _ ->
                saveAndApply()
            }
            .setNegativeButton("Discard") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun enterEditingMode() {
        isEditing = true
        val controls = rootLayout.getChildAt(1) as LinearLayout
        controls.visibility = View.VISIBLE
        Toast.makeText(this, "Editing mode: Drag to move, corner to resize, tap to remove", Toast.LENGTH_SHORT).show()
    }

    private fun showTuningDialog() {
        val prefs = getSharedPreferences("InputBlockerPrefs", Context.MODE_PRIVATE)
        var currentEps = prefs.getFloat("dbscan_eps", 0.03f)
        var currentMinPts = prefs.getInt("dbscan_minpts", 3)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        // EPS Slider
        val epsLabel = TextView(this).apply {
            text = "Eps (Radius): ${String.format("%.3f", currentEps)}"
            setTextColor(Color.BLACK)
        }
        val epsSeekBar = SeekBar(this).apply {
            max = 100
            progress = (currentEps * 1000).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentEps = progress / 1000f
                    epsLabel.text = "Eps (Radius): ${String.format("%.3f", currentEps)}"
                    updateClustering(currentEps, currentMinPts)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    prefs.edit().putFloat("dbscan_eps", currentEps).apply()
                }
            })
        }

        // MinPts Slider
        val minPtsLabel = TextView(this).apply {
            text = "Min Points: $currentMinPts"
            setTextColor(Color.BLACK)
        }
        val minPtsSeekBar = SeekBar(this).apply {
            max = 10
            progress = currentMinPts
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentMinPts = if (progress < 2) 2 else progress
                    minPtsLabel.text = "Min Points: $currentMinPts"
                    updateClustering(currentEps, currentMinPts)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    prefs.edit().putInt("dbscan_minpts", currentMinPts).apply()
                }
            })
        }

        layout.addView(epsLabel)
        layout.addView(epsSeekBar)
        layout.addView(minPtsLabel)
        layout.addView(minPtsSeekBar)

        AlertDialog.Builder(this)
            .setTitle("Fine-tune Clustering")
            .setMessage("Adjust the DBSCAN parameters to better fit your ghost tap patterns. The preview updates in real-time.")
            .setView(layout)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun updateClustering(eps: Float, minPts: Int) {
        val points = SensingActivity.capturedTouches
        if (points.isEmpty()) return

        regions.clear()
        clusterTapCounts.clear()
        val reclustered = ClusterUtils.clusterPairs(points, eps, minPts)
        regions.addAll(reclustered.map { ClusterUtils.calculateBoundingBox(it) })
        clusterTapCounts.addAll(reclustered.map { it.size })
        reviewCanvas.setRegions(regions, clusterTapCounts)
        val totalTaps = clusterTapCounts.sum()
        tvCount.text = "$totalTaps taps in ${regions.size} region${if (regions.size != 1) "s" else ""}"
    }


    private fun saveAndApply() {
        try {
            val content = StringBuilder()
            content.append("# Detected Regions\n")
            content.append("enabled=1\n")
            content.append("force_safe_mode=0\n\n")
            
            for (region in regions) {
                content.append("$region\n")
            }
            
            InputBlockerServiceManager.saveConfig(this, "default", content.toString())
            
            val intent = Intent("com.inputblocker.RELOAD")
            intent.setPackage(packageName)
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
        private val selectedPaint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        private val handlePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private var regionsList = mutableListOf<Region>()
        private var regionTapCounts = mutableListOf<Int>()
        private var selectedRegion: Region? = null
        private var isDragging = false
        private var isResizing = false
        private var activeHandle: Handle? = null
        private var lastX = 0f
        private var lastY = 0f

        private val labelPaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val labelBgPaint = Paint().apply {
            color = Color.argb(160, 0, 0, 0)
            style = Paint.Style.FILL
        }

        fun setRegions(list: List<Region>, tapCounts: List<Int> = emptyList()) {
            regionsList.clear()
            regionTapCounts.clear()
            regionsList.addAll(list)
            regionTapCounts.addAll(tapCounts)
            // Pad tap counts if we got fewer than regions
            while (regionTapCounts.size < regionsList.size) {
                regionTapCounts.add(0)
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            for ((i, region) in regionsList.withIndex()) {
                val rect = RectF(
                    region.x1 * width, region.y1 * height,
                    region.x2 * width, region.y2 * height
                )
                canvas.drawRect(rect, blockPaint)
                
                val paint = if (region == selectedRegion) selectedPaint else borderPaint
                canvas.drawRect(rect, paint)

                // Draw tap count label near top-left of this region
                val count = regionTapCounts.getOrElse(i) { 0 }
                if (count > 0) {
                    val label = "${count}t"
                    val textWidth = labelPaint.measureText(label)
                    val lx = rect.left + 4f
                    val ly = rect.top + 4f
                    canvas.drawRect(lx, ly - labelPaint.textSize + 4f,
                        lx + textWidth + 8f, ly + 4f, labelBgPaint)
                    canvas.drawText(label, lx + 4f, ly, labelPaint)
                }

                if (region == selectedRegion) {
                    drawHandles(canvas, rect)
                }
            }
        }

        private fun drawHandles(canvas: Canvas, rect: RectF) {
            val hSize = 30f
            canvas.drawRect(rect.left - hSize/2, rect.top - hSize/2, rect.left + hSize/2, rect.top + hSize/2, handlePaint)
            canvas.drawRect(rect.right - hSize/2, rect.top - hSize/2, rect.right + hSize/2, rect.top + hSize/2, handlePaint)
            canvas.drawRect(rect.left - hSize/2, rect.bottom - hSize/2, rect.left + hSize/2, rect.bottom + hSize/2, handlePaint)
            canvas.drawRect(rect.right - hSize/2, rect.bottom - hSize/2, rect.right + hSize/2, rect.bottom + hSize/2, handlePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = x
                    lastY = y
                    
                    if (!isEditing) return false

                    // Check handles of selected region first
                    if (selectedRegion != null) {
                        val handle = getHandleAt(x, y)
                        if (handle != null) {
                            isResizing = true
                            activeHandle = handle
                            return true
                        }
                    }

                    // Check if tapped inside any region
                    val tappedRegion = regionsList.find { r: Region ->
                        x >= r.x1 * width && x <= r.x2 * width && y >= r.y1 * height && y <= r.y2 * height
                    }

                    if (tappedRegion != null) {
                        if (tappedRegion == selectedRegion) {
                            // Already selected, start dragging
                            isDragging = true
                        } else {
                            // Select new region
                            selectedRegion = tappedRegion
                            isDragging = true
                        }
                        invalidate()
                        return true
                    } else {
                        selectedRegion = null
                        invalidate()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (selectedRegion == null) return false

                    val dx = (x - lastX) / width
                    val dy = (y - lastY) / height
                    
                                 if (isResizing) {
                                     val r = selectedRegion!!
                                     selectedRegion = when (activeHandle) {
                                         Handle.TOP_LEFT -> r.copy(x1 = r.x1 + dx, y1 = r.y1 + dy) as Region
                                         Handle.TOP_RIGHT -> r.copy(x2 = r.x2 + dx, y1 = r.y1 + dy) as Region
                                         Handle.BOTTOM_LEFT -> r.copy(x1 = r.x1 + dx, y2 = r.y2 + dy) as Region
                                         Handle.BOTTOM_RIGHT -> r.copy(x2 = r.x2 + dx, y2 = r.y2 + dy) as Region
                                         else -> r as Region
                                     }
                                 } else if (isDragging) {
                                     val r = selectedRegion!!
                                     selectedRegion = r.copy(
                                         x1 = r.x1 + dx,
                                         x2 = r.x2 + dx,
                                         y1 = r.y1 + dy,
                                         y2 = r.y2 + dy
                                     )
                                 }

                    
                                 // Clamp to [0, 1] and prevent inversion
                                 selectedRegion = selectedRegion?.let { r: Region ->
                                     val nx1 = r.x1.coerceAtLeast(0f)
                                     val ny1 = r.y1.coerceAtLeast(0f)
                                     val nx2 = r.x2.coerceAtMost(1f)
                                     val ny2 = r.y2.coerceAtMost(1f)
                                     
                                     val fx1 = if (nx1 > nx2) nx2 else nx1
                                     val fx2 = if (nx1 > nx2) nx1 else nx2
                                     val fy1 = if (ny1 > ny2) ny2 else ny1
                                     val fy2 = if (ny1 > ny2) ny1 else ny2
                                     
                                     r.copy(x1 = fx1, y1 = fy1, x2 = fx2, y2 = fy2)
                                 }

                    
                    lastX = x
                    lastY = y
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    isResizing = false
                    activeHandle = null
                }
            }
            return true
        }

        private fun getHandleAt(x: Float, y: Float): Handle? {
            val r = selectedRegion ?: return null
            val hSize = 30f
            val lx = r.x1 * width
            val ly = r.y1 * height
            val rx = r.x2 * width
            val ry = r.y2 * height

            return when {
                x in (lx-hSize..lx+hSize) && y in (ly-hSize..ly+hSize) -> Handle.TOP_LEFT
                x in (rx-hSize..rx+hSize) && y in (ly-hSize..ly+hSize) -> Handle.TOP_RIGHT
                x in (lx-hSize..lx+hSize) && y in (ry-hSize..ry+hSize) -> Handle.BOTTOM_LEFT
                x in (rx-hSize..rx+hSize) && y in (ry-hSize..ry+hSize) -> Handle.BOTTOM_RIGHT
                else -> null
            }
        }
    }
}

