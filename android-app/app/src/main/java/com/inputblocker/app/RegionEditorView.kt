package com.inputblocker.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.inputblocker.shared.Region
import kotlin.math.abs

class RegionEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var regions = mutableListOf<Region>()
    private var selectedRegionIndex: Int? = null
    
    private var isDrawing = false
    private var drawStart = PointF()
    private var drawEnd = PointF()
    
    private var isDragging = false
    private var dragOffset = PointF()
    
    private var isResizing = false
    private var resizeHandle = -1 // 0: TL, 1: TR, 2: BL, 3: BR
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var onRegionChanged: ((List<Region>) -> Unit)? = null

    fun setRegions(newRegions: List<Region>) {
        regions.clear()
        regions.addAll(newRegions)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw Existing Regions
        regions.forEachIndexed { index, region ->
            val isSelected = index == selectedRegionIndex
            
            // Colors
            val color = if (region.isExclude) Color.RED else Color.BLUE
            paint.color = if (isSelected) Color.WHITE else color
            fillPaint.color = color
            fillPaint.alpha = 50
            
            val left = region.x1 * width
            val top = region.y1 * height
            val right = region.x2 * width
            val bottom = region.y2 * height
            
            canvas.drawRect(left, top, right, bottom, fillPaint)
            canvas.drawRect(left, top, right, bottom, paint)
            
            if (isSelected) {
                drawHandles(canvas, left, top, right, bottom)
            }
        }
        
        // Draw active drawing rectangle
        if (isDrawing) {
            paint.color = Color.YELLOW
            canvas.drawRect(drawStart.x, drawStart.y, drawEnd.x, drawEnd.y, paint)
        }
    }

    private fun drawHandles(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        paint.color = Color.WHITE
        val s = 20f
        canvas.drawRect(l-s/2, t-s/2, l+s/2, t+s/2, paint) // TL
        canvas.drawRect(r-s/2, t-s/2, r+s/2, t+s/2, paint) // TR
        canvas.drawRect(l-s/2, b-s/2, l+s/2, b+s/2, paint) // BL
        canvas.drawRect(r-s/2, b-s/2, r+s/2, b+s/2, paint) // BR
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val nx = x / width
        val ny = y / height

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 1. Check for resize handles first
                if (selectedRegionIndex != null) {
                    val r = regions[selectedRegionIndex!!]
                    val l = r.x1 * width
                    val t = r.y1 * height
                    val rt = r.x2 * width
                    val b = r.y2 * height
                    val s = 40f
                    
                    if (abs(x - l) < s && abs(y - t) < s) { isResizing = true; resizeHandle = 0; return true }
                    if (abs(x - rt) < s && abs(y - t) < s) { isResizing = true; resizeHandle = 1; return true }
                    if (abs(x - l) < s && abs(y - b) < s) { isResizing = true; resizeHandle = 2; return true }
                    if (abs(x - rt) < s && abs(y - b) < s) { isResizing = true; resizeHandle = 3; return true }
                }

                // 2. Check for region selection/dragging
                var found = false
                for (i in regions.indices.reversed()) {
                    if (regions[i].contains(nx, ny)) {
                        selectedRegionIndex = i
                        isDragging = true
                        dragOffset.set(x - (regions[i].x1 * width), y - (regions[i].y1 * height))
                        found = true
                        break
                    }
                }

                if (!found) {
                    selectedRegionIndex = null
                    isDrawing = true
                    drawStart.set(x, y)
                    drawEnd.set(x, y)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    drawEnd.set(x, y)
                } else if (isDragging && selectedRegionIndex != null) {
                    val r = regions[selectedRegionIndex!!]
                    val newX1 = (x - dragOffset.x) / width
                    val newY1 = (y - dragOffset.y) / height
                    regions[selectedRegionIndex!!] = r.copy(
                        x1 = newX1.coerceAtLeast(0f),
                        y1 = newY1.coerceAtLeast(0f),
                        x2 = (newX1 + (r.x2 - r.x1)).coerceAtMost(1f),
                        y2 = (newY1 + (r.y2 - r.y1)).coerceAtMost(1f)
                    )
                } else if (isResizing && selectedRegionIndex != null) {
                    val r = regions[selectedRegionIndex!!]
                    val updated = when (resizeHandle) {
                        0 -> r.copy(x1 = nx.coerceAtLeast(0f), y1 = ny.coerceAtLeast(0f))
                        1 -> r.copy(x2 = nx.coerceAtMost(1f), y1 = ny.coerceAtLeast(0f))
                        2 -> r.copy(x1 = nx.coerceAtLeast(0f), y2 = ny.coerceAtMost(1f))
                        3 -> r.copy(x2 = nx.coerceAtMost(1f), y2 = ny.coerceAtMost(1f))
                        else -> r
                    }
                    regions[selectedRegionIndex!!] = updated
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDrawing) {
                    val x1 = Math.min(drawStart.x, drawEnd.x) / width
                    val y1 = Math.min(drawStart.y, drawEnd.y) / height
                    val x2 = Math.max(drawStart.x, drawEnd.x) / width
                    val y2 = Math.max(drawStart.y, drawEnd.y) / height
                    if (abs(x2 - x1) > 0.01f && abs(y2 - y1) > 0.01f) {
                        regions.add(Region(x1, y1, x2, y2))
                        onRegionChanged?.invoke(regions)
                    }
                }
                isDrawing = false
                isDragging = false
                isResizing = false
                selectedRegionIndex = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
