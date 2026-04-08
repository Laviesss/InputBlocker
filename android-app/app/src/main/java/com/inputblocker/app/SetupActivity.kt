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
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAW_OVER_SYSTEM_BARS)
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

    override fun onBackPressed() {
        if (regions.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Discard Changes?")
                .setMessage("You have unsaved regions. Discard them?")
                .setPositiveButton("Discard") { _, _ -> super.onBackPressed() }
                .setNegativeButton("Keep Editing", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    data class Region(
        var x1: Int = 0,
        var y1: Int = 0,
        var x2: Int = 0,
        var y2: Int = 0
    ) : Serializable

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
                val rect = RectF(region.x1.toFloat(), region.y1.toFloat(), region.x2.toFloat(), region.y2.toFloat())

                val p = Paint(fillPaint).apply {
                    color = Color.argb(51, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                }
                canvas.drawRect(rect, p)

                val bp = Paint(borderPaint).apply {
                    color = accentColor
                }
                canvas.drawRect(rect, bp)

                val label = (i + 1).toString()
                val centerX = (region.x1 + region.x2) / 2f
                val centerY = (region.y1 + region.y2) / 2f

                val labelBg = Paint().apply {
                    color = accentColor
                }
                canvas.drawCircle(centerX, centerY, 24f, labelBg)

                val labelPaint = Paint(textPaint).apply {
                    color = Color.WHITE
                }
                canvas.drawText(label, centerX, centerY + 12f, labelPaint)
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
                    startX = x
                    startY = y
                    isDrawing = true
                    currentRect = RectF()
                    invalidate()
                    return true
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
                                currentRect!!.left.toInt(),
                                currentRect!!.top.toInt(),
                                currentRect!!.right.toInt(),
                                currentRect!!.bottom.toInt()
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
