package com.inputblocker.shared

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Region(
    val isExclude: Boolean = false,
    val type: Int = 0,               // 0: Rect, 1: Circle, 2: Ellipse
    val x1: Float = 0f,
    val y1: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val minPressure: Float = 0f,
    val maxDuration: Long = 1000L
) {
    constructor(x1: Float, y1: Float, x2: Float, y2: Float) : this(
        isExclude = false,
        type = 0,
        x1 = x1,
        y1 = y1,
        x2 = x2,
        y2 = y2
    )

    val width: Float get() = abs(x2 - x1)
    val height: Float get() = abs(y2 - y1)
    val left: Float get() = min(x1, x2)
    val right: Float get() = max(x1, x2)
    val top: Float get() = min(y1, y2)
    val bottom: Float get() = max(y1, y2)

    fun copyWithCoords(newX1: Float, newY1: Float, newX2: Float, newY2: Float): Region {
        return this.copy(x1 = newX1, y1 = newY1, x2 = newX2, y2 = newY2)
    }

    fun isValid(): Boolean {
        if (type !in 0..2) return false
        if (minPressure < 0f || minPressure > 1f || minPressure.isNaN()) return false
        if (maxDuration < 0L) return false
        if (x1.isNaN() || y1.isNaN() || x2.isNaN() || y2.isNaN()) return false
        return true
    }

    override fun toString(): String {
        return "${if (isExclude) 1 else 0},$type,$x1,$y1,$x2,$y2,$minPressure,$maxDuration"
    }

    fun contains(x: Float, y: Float): Boolean {
        if (x.isNaN() || y.isNaN()) return false
        return when (type) {
            0 -> x >= left && x <= right && y >= top && y <= bottom
            1 -> {
                if (x2 <= 0f) return false
                val dx = x - x1
                val dy = y - y1
                (dx * dx + dy * dy) <= (x2 * x2)
            }
            2 -> {
                if (x2 <= 0f || y2 <= 0f) return false
                val dx = (x - x1) / x2
                val dy = (y - y1) / y2
                (dx * dx + dy * dy) <= 1.0f
            }
            else -> false
        }
    }

    companion object {
        fun fromString(line: String): Region? {
            val parts = line.split(",").map { it.trim() }
            return try {
                val region = when (parts.size) {
                    4 -> Region(x1 = parts[0].toFloat(), y1 = parts[1].toFloat(), x2 = parts[2].toFloat(), y2 = parts[3].toFloat())
                    5 -> Region(type = parts[0].toInt(), x1 = parts[1].toFloat(), y1 = parts[2].toFloat(), x2 = parts[3].toFloat(), y2 = parts[4].toFloat())
                    7 -> Region(type = parts[0].toInt(), x1 = parts[1].toFloat(), y1 = parts[2].toFloat(), x2 = parts[3].toFloat(), y2 = parts[4].toFloat(), minPressure = parts[5].toFloat(), maxDuration = parts[6].toLong())
                    8 -> Region(isExclude = parts[0] == "1", type = parts[1].toInt(), x1 = parts[2].toFloat(), y1 = parts[3].toFloat(), x2 = parts[4].toFloat(), y2 = parts[5].toFloat(), minPressure = parts[6].toFloat(), maxDuration = parts[7].toLong())
                    else -> null
                }
                if (region != null && region.isValid()) region else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
