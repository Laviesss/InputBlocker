package com.inputblocker.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.Serializable

class SetupActivity : AppCompatActivity() {

    private lateinit var setupView: SetupView
    private lateinit var tvInstructions: TextView
    private lateinit var tvRegionCount: TextView
    private lateinit var btnUndo: Button
    private lateinit var btnClear: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private var screenWidth = 0
    private var screenHeight = 0
    private var currentTheme = 0
    
    private val regions = mutableListOf<Region>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentTheme = intent.getIntExtra("theme", 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }

        setContentView(R.layout.activity_setup)

        setupView = findViewById(R.id.setup_view)
        tvInstructions = findViewById(R.id.tv_instructions)
        tvRegionCount = findViewById(R.id.tv_region_count)
        btnUndo = findViewById(R.id.btn_undo)
        btnClear = findViewById(R.id.btn_clear)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)

        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels

        applyThemeColors()
        loadRegions()
        setupListeners()
        updateRegionCount()
        
        setupBackHandler()
    }
    
    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (regions.isNotEmpty()) {
                    AlertDialog.Builder(this@SetupActivity)
                        .setTitle("Discard Changes?")
                        .setMessage("You have unsaved regions. Discard them?")
                        .setPositiveButton("Discard") { _, _ -> finish() }
                        .setNegativeButton("Keep Editing", null)
                        .show()
                } else {
                    finish()
                }
            }
        })
    }

    private fun applyThemeColors() {
        val surfaceColor = getSurfaceColor()
        val elevatedColor = getSurfaceElevatedColor()
        val textPrimary = getTextPrimaryColor()
        val textSecondary = getTextSecondaryColor()

        tvRegionCount.setBackgroundColor(surfaceColor)
        tvRegionCount.setTextColor(textPrimary)

        tvInstructions.setBackgroundColor(elevatedColor)
        tvInstructions.setTextColor(textSecondary)

        btnUndo.backgroundTintList = ColorStateList.valueOf(elevatedColor)
        btnUndo.setTextColor(textPrimary)

        btnClear.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.accent_orange)
        )

        btnSave.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.accent_green)
        )

        btnCancel.backgroundTintList = ColorStateList.valueOf(elevatedColor)
        btnCancel.setTextColor(textPrimary)

        setupView.setThemeColors(
            ContextCompat.getColor(this, R.color.accent_blue),
            getBackgroundColor()
        )
    }

    private fun getBackgroundColor(): Int {
        return when (currentTheme) {
            1 -> ContextCompat.getColor(this, R.color.light_background)
            2 -> ContextCompat.getColor(this, R.color.dark_background)
            3 -> ContextCompat.getColor(this, R.color.amoled_background)
            else -> ContextCompat.getColor(this, R.color.dark_background)
        }
    }

    private fun getSurfaceColor(): Int {
        return when (currentTheme) {
            1 -> ContextCompat.getColor(this, R.color.light_surface)
            2 -> ContextCompat.getColor(this, R.color.dark_surface)
            3 -> ContextCompat.getColor(this, R.color.amoled_surface)
            else -> ContextCompat.getColor(this, R.color.dark_surface)
        }
    }

    private fun getSurfaceElevatedColor(): Int {
        return when (currentTheme) {
            1 -> ContextCompat.getColor(this, R.color.light_surface_elevated)
            2 -> ContextCompat.getColor(this, R.color.dark_surface_elevated)
            3 -> ContextCompat.getColor(this, R.color.amoled_surface_elevated)
            else -> ContextCompat.getColor(this, R.color.dark_surface_elevated)
        }
    }

    private fun getTextPrimaryColor(): Int {
        return when (currentTheme) {
            1 -> ContextCompat.getColor(this, R.color.light_text_primary)
            2 -> ContextCompat.getColor(this, R.color.dark_text_primary)
            3 -> ContextCompat.getColor(this, R.color.amoled_text_primary)
            else -> ContextCompat.getColor(this, R.color.dark_text_primary)
        }
    }

    private fun getTextSecondaryColor(): Int {
        return when (currentTheme) {
            1 -> ContextCompat.getColor(this, R.color.light_text_secondary)
            2 -> ContextCompat.getColor(this, R.color.dark_text_secondary)
            3 -> ContextCompat.getColor(this, R.color.amoled_text_secondary)
            else -> ContextCompat.getColor(this, R.color.dark_text_secondary)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadRegions() {
        val regionsList = intent.getSerializableExtra("regions") as? ArrayList<*>
        regionsList?.forEach { obj ->
            if (obj is Region) {
                regions.add(obj)
            }
        }
        setupView.setRegions(regions)
    }

    private fun setupListeners() {
        setupView.setDrawingCallback(object : SetupView.DrawingCallback {
            override fun onRegionDrawn(region: Region) {
                regions.add(region)
                updateRegionCount()
            }

            override fun onRegionCountChanged(count: Int) {
                updateRegionCount()
            }
        })

        btnUndo.setOnClickListener {
            if (regions.isNotEmpty()) {
                regions.removeAt(regions.size - 1)
                setupView.setRegions(regions)
                updateRegionCount()
            }
        }

        btnClear.setOnClickListener {
            regions.clear()
            setupView.setRegions(regions)
            updateRegionCount()
        }

        btnSave.setOnClickListener { saveAndExit() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun updateRegionCount() {
        tvRegionCount.text = "Regions: ${regions.size}\nScreen: ${screenWidth}x${screenHeight}"
    }

    private fun saveAndExit() {
        val resultIntent = Intent()
        resultIntent.putExtra("regions", ArrayList(regions))
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    class SetupView(context: android.content.Context) : View(context) {

        interface DrawingCallback {
            fun onRegionDrawn(region: Region)
            fun onRegionCountChanged(count: Int)
        }

        private val borderPaint = Paint().apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private var fillPaint = Paint().apply {
            color = Color.argb(51, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            style = Paint.Style.FILL
        }

        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }

        private val regionsList = mutableListOf<Region>()
        private var currentRect: RectF? = null
        private var startX = 0f
        private var startY = 0f
        private var isDrawing = false
        
        private var accentColor = Color.parseColor("#2196F3")
        private var backgroundColor = Color.parseColor("#88000000")

        private var callback: DrawingCallback? = null
        private var selectedRegion: Region? = null
        private var isResizing = false
        private var resizeHandle = 0 // 0: none, 1: TL, 2: TR, 3: BL, 4: BR
        private var dragOffsetX = 0f
        private var dragOffsetY = 0f
        
        fun setThemeColors(accent: Int, bg: Int) {
            accentColor = accent
            borderPaint.color = accent
            fillPaint.color = Color.argb(51, Color.red(accent), Color.green(accent), Color.blue(accent))
        }

        fun setDrawingCallback(callback: DrawingCallback) {
            this.callback = callback
        }

        fun setRegions(regions: List<Region>) {
            regionsList.clear()
            regionsList.addAll(regions)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            canvas.drawColor(backgroundColor)
            
            for (i in regionsList.indices) {
                val region = regionsList[i]
                val rect = RectF(
                    region.x1 * screenWidth, region.y1 * screenHeight,
                    region.x2 * screenWidth, region.y2 * screenHeight
                )
                
                val p = Paint(fillPaint).apply {
                    color = if (region == selectedRegion) Color.parseColor("#66B388FF") else Color.argb(51, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                }
                canvas.drawRect(rect, p)
                
                val bp = Paint(borderPaint).apply {
                    color = if (region == selectedRegion) Color.YELLOW else accentColor
                }
                canvas.drawRect(rect, bp)
                
                val label = (i + 1).toString()
                val centerX = rect.centerX()
                val centerY = rect.centerY()
                
                val labelBg = Paint().apply {
                    color = accentColor
                }
                canvas.drawCircle(centerX, centerY, 24f, labelBg)
                
                val labelPaint = Paint(textPaint).apply {
                    color = Color.WHITE
                }
                canvas.drawText(label, centerX, centerY + 12f, labelPaint)
                
                if (region == selectedRegion) {
                    val handleSize = 20f
                    val handlePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
                    canvas.drawRect(rect.left - handleSize/2, rect.top - handleSize/2, rect.left + handleSize/2, rect.top + handleSize/2, handlePaint)
                    canvas.drawRect(rect.right - handleSize/2, rect.top - handleSize/2, rect.right + handleSize/2, rect.top + handleSize/2, handlePaint)
                    canvas.drawRect(rect.left - handleSize/2, rect.bottom - handleSize/2, rect.left + handleSize/2, rect.bottom + handleSize/2, handlePaint)
                    canvas.drawRect(rect.right - handleSize/2, rect.bottom - handleSize/2, rect.right + handleSize/2, rect.bottom + handleSize/2, handlePaint)
                }
            }
            
            currentRect?.let { rect ->
                val p = Paint(fillPaint).apply {
                    color = Color.parseColor("#44FF5722")
                }
                canvas.drawRect(rect, p)
                
                val bp = Paint(borderPaint).apply {
                    color = Color.parseColor("#FF5722")
                }
                canvas.drawRect(rect, bp)
                
                val size = "${rect.width().toInt()}x${rect.height().toInt()}"
                val cx = rect.centerX()
                val cy = rect.centerY()
                
                val bg = Paint().apply {
                    color = Color.parseColor("#CC000000")
                }
                canvas.drawRect(cx - 60f, cy - 20f, cx + 60f, cy + 20f, bg)
                
                val tp = Paint(textPaint).apply {
                    textSize = 24f
                }
                canvas.drawText(size, cx, cy + 8f, tp)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 1. Check if we touched a resize handle of the selected region
                    selectedRegion?.let { region ->
                        val rect = RectF(region.x1 * screenWidth, region.y1 * screenHeight, region.x2 * screenWidth, region.y2 * screenHeight)
                        val handleSize = 40f
                        
                        if (x in (rect.left - handleSize..rect.left + handleSize) && y in (rect.top - handleSize..rect.top + handleSize)) {
                            isResizing = true
                            resizeHandle = 1 // TL
                            return true
                        }
                        if (x in (rect.right - handleSize..rect.right + handleSize) && y in (rect.top - handleSize..rect.top + handleSize)) {
                            isResizing = true
                            resizeHandle = 2 // TR
                            return true
                        }
                        if (x in (rect.left - handleSize..rect.left + handleSize) && y in (rect.bottom - handleSize..rect.bottom + handleSize)) {
                            isResizing = true
                            resizeHandle = 3 // BL
                            return true
                        }
                        if (x in (rect.right - handleSize..rect.right + handleSize) && y in (rect.bottom - handleSize..rect.bottom + handleSize)) {
                            isResizing = true
                            resizeHandle = 4 // BR
                            return true
                        }
                    }
                    
                    // 2. Check if we touched an existing region to select/move it
                    for (region in regionsList) {
                        val rect = RectF(region.x1 * screenWidth, region.y1 * screenHeight, region.x2 * screenWidth, region.y2 * screenHeight)
                        if (rect.contains(x, y)) {
                            selectedRegion = region
                            dragOffsetX = x - rect.centerX()
                            dragOffsetY = y - rect.centerY()
                            invalidate()
                            return true
                        }
                    }
                    
                    // 3. Otherwise, start drawing a new region
                    startX = x
                    startY = y
                    isDrawing = true
                    currentRect = RectF()
                    selectedRegion = null
                    invalidate()
                    return true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing && selectedRegion != null) {
                        val r = selectedRegion!!
                        when (resizeHandle) {
                            1 -> { r.x1 = (x / screenWidth).coerceAtLeast(0f); r.y1 = (y / screenHeight).coerceAtLeast(0f) }
                            2 -> { r.x2 = (x / screenWidth).coerceAtMost(1f); r.y1 = (y / screenHeight).coerceAtLeast(0f) }
                            3 -> { r.x1 = (x / screenWidth).coerceAtLeast(0f); r.y2 = (y / screenHeight).coerceAtMost(1f) }
                            4 -> { r.x2 = (x / screenWidth).coerceAtMost(1f); r.y2 = (y / screenHeight).coerceAtMost(1f) }
                        }
                        invalidate()
                        return true
                    }
                    
                    if (selectedRegion != null && !isResizing) {
                        val r = selectedRegion!!
                        val w = (r.x2 - r.x1) * screenWidth
                        val h = (r.y2 - r.y1) * screenHeight
                        val nx = (x - dragOffsetX) / screenWidth
                        val ny = (y - dragOffsetY) / screenHeight
                        
                        val dx = nx - (r.x1 + w/2 / screenWidth)
                        val dy = ny - (r.y1 + h/2 / screenHeight)
                        
                        r.x1 += dx
                        r.x2 += dx
                        r.y1 += dy
                        r.y2 += dy
                        invalidate()
                        return true
                    }
                    
                    if (isDrawing) {
                        currentRect?.set(
                            minOf(startX, x),
                            minOf(startY, y),
                            maxOf(startX, x),
                            maxOf(startY, y)
                        )
                        invalidate()
                    }
                    return true
                }
                
                MotionEvent.ACTION_UP -> {
                    if (isResizing) {
                        isResizing = false
                        resizeHandle = 0
                    }
                    if (isDrawing && currentRect != null) {
                        if (currentRect!!.width() > 20 && currentRect!!.height() > 20) {
                            val region = Region(
                                currentRect!!.left / screenWidth,
                                currentRect!!.top / screenHeight,
                                currentRect!!.right / screenWidth,
                                currentRect!!.bottom / screenHeight
                            )
                            callback?.onRegionDrawn(region)
                        }
                        currentRect = null
                        isDrawing = false
                        invalidate()
                    }
                    return true
                }
                
                MotionEvent.ACTION_CANCEL -> {
                    currentRect = null
                    isDrawing = false
                    selectedRegion = null
                    isResizing = false
                    invalidate()
                    return true
                }
            }
            
            return super.onTouchEvent(event)
        }

                MotionEvent.ACTION_MOVE -> {
                    if (isDrawing) {
                        currentRect?.set(
                            minOf(startX, x),
                            minOf(startY, y),
                            maxOf(startX, x),
                            maxOf(startY, y)
                        )
                        invalidate()
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (isDrawing && currentRect != null) {
                        if (currentRect!!.width() > 20 && currentRect!!.height() > 20) {
                    val region = Region(
                        currentRect!!.left / screenWidth,
                        currentRect!!.top / screenHeight,
                        currentRect!!.right / screenWidth,
                        currentRect!!.bottom / screenHeight
                    )
                            callback?.onRegionDrawn(region)
                        }
                        currentRect = null
                        isDrawing = false
                        invalidate()
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    currentRect = null
                    isDrawing = false
                    invalidate()
                    return true
                }
            }

            return super.onTouchEvent(event)
        }
    }
}
