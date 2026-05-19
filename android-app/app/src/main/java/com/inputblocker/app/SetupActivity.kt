package com.inputblocker.app

import com.inputblocker.shared.Region
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
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
import java.lang.ref.WeakReference
import java.util.ArrayList

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

        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

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
        val colors = ThemeManager.getThemeColors(this, currentTheme)
        ThemeManager.applyThemeToViewHierarchy(findViewById(android.R.id.content), colors)

        setupView.setThemeColors(
            colors.accent,
            colors.background
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

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun loadRegions() {
        val regionsList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("regions", ArrayList::class.java) as? ArrayList<Region>
        } else {
            intent.getSerializableExtra("regions") as? ArrayList<Region>
        }
        
        regionsList?.forEach { region ->
            regions.add(region)
        }
        setupView.setRegions(regions)
    }

    private fun setupListeners() {
        setupView.setDrawingCallback(object : SetupViewCallback {
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

    interface SetupViewCallback {
        fun onRegionDrawn(region: Region)
        fun onRegionCountChanged(count: Int)
    }

    // Static nested class to avoid XML inflation issues
    class SetupView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

        private var accentColor = Color.parseColor("#2196F3")
        private var backgroundColorVal = Color.parseColor("#88000000")

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
        
        private var callback: SetupViewCallback? = null
        private var selectedRegion: Region? = null
        private var isResizing = false
        private var resizeHandle = 0 // 0: none, 1: TL, 2: TR, 3: BL, 4: BR
        private var dragOffsetX = 0f
        private var dragOffsetY = 0f
        
        fun setThemeColors(accent: Int, bg: Int) {
            accentColor = accent
            backgroundColorVal = bg
            borderPaint.color = accent
            fillPaint.color = Color.argb(51, Color.red(accent), Color.green(accent), Color.blue(accent))
            invalidate()
        }

        fun setDrawingCallback(callback: SetupViewCallback) {
            this.callback = callback
        }

        fun setRegions(regions: List<Region>) {
            regionsList.clear()
            regionsList.addAll(regions)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            canvas.drawColor(backgroundColorVal)
            
            val screenWidth = width.toFloat()
            val screenHeight = height.toFloat()
            
            for (i in regionsList.indices) {
                val region = regionsList[i]
                val rect = RectF(
                    region.x1 * screenWidth, region.y1 * screenHeight,
                    region.x2 * screenWidth, region.y2 * screenHeight
                )
                
                val p = Paint(fillPaint).apply {
                    color = if (region == selectedRegion) Color.parseColor("#66B388FF") else fillPaint.color
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
            val screenWidth = width.toFloat()
            val screenHeight = height.toFloat()
            
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
                        val index = regionsList.indexOf(r)
                        if (index != -1) {
                            val updated = when (resizeHandle) {
                                1 -> r.copy(x1 = (x / screenWidth).coerceIn(0f, r.x2 - 0.05f), y1 = (y / screenHeight).coerceIn(0f, r.y2 - 0.05f))
                                2 -> r.copy(x2 = (x / screenWidth).coerceIn(r.x1 + 0.05f, 1f), y1 = (y / screenHeight).coerceIn(0f, r.y2 - 0.05f))
                                3 -> r.copy(x1 = (x / screenWidth).coerceIn(0f, r.x2 - 0.05f), y2 = (y / screenHeight).coerceIn(r.y1 + 0.05f, 1f))
                                4 -> r.copy(x2 = (x / screenWidth).coerceIn(r.x1 + 0.05f, 1f), y2 = (y / screenHeight).coerceIn(r.y1 + 0.05f, 1f))
                                else -> r
                            }
                            regionsList[index] = updated
                            selectedRegion = updated
                        }
                        invalidate()
                        return true
                    }
                    
                    if (selectedRegion != null) {
                        val r = selectedRegion!!
                        val index = regionsList.indexOf(r)
                        if (index != -1) {
                            val w = r.x2 - r.x1
                            val h = r.y2 - r.y1
                            
                            val newCenterX = x / screenWidth
                            val newCenterY = y / screenHeight
                            
                            val newX1 = (newCenterX - w/2).coerceIn(0f, 1f - w)
                            val newY1 = (newCenterY - h/2).coerceIn(0f, 1f - h)
                            
                            val updated = r.copy(
                                x1 = newX1,
                                x2 = newX1 + w,
                                y1 = newY1,
                                y2 = newY1 + h
                            )
                            regionsList[index] = updated
                            selectedRegion = updated
                        }
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
        
        override fun performClick(): Boolean {
            return super.performClick()
        }
    }
}
