package com.inputblocker.app

import java.io.Serializable

data class Region(
    var isExclude: Boolean = false, // True if this is an exclude zone
    var type: Int = 0,               // 0: Rect, 1: Circle, 2: Ellipse
    var x1: Float = 0f,
    var y1: Float = 0f,
    var x2: Float = 0f,
    var y2: Float = 0f,
    var minPressure: Float = 0f,    // Block if pressure < this
    var maxDuration: Long = 1000L   // Block if duration < this (ms)
) : Serializable {

    // Helper for manual Rect creation
    constructor(x1: Float, y1: Float, x2: Float, y2: Float) : this(
        isExclude = false,
        type = 0,
        x1 = x1,
        y1 = y1,
        x2 = x2,
        y2 = y2
    )

    override fun toString(): String {
        return "${if (isExclude) 1 else 0},$type,$x1,$y1,$x2,$y2,$minPressure,$maxDuration"
    }

    companion object {
        /**
         * Parses a region string. Supported formats (backwards compatible):
         * - x1,y1,x2,y2
         * - type,x1,y1,x2,y2
         * - type,x1,y1,x2,y2,minPressure,maxDuration
         * - isExclude,type,x1,y1,x2,y2,minPressure,maxDuration (FULL)
         */
        fun fromString(line: String): Region? {
            val parts = line.split(",").map { it.trim() }
            return try {
                when (parts.size) {
                    4 -> Region(
                        x1 = parts[0].toFloat(),
                        y1 = parts[1].toFloat(),
                        x2 = parts[2].toFloat(),
                        y2 = parts[3].toFloat()
                    )
                    5 -> Region(
                        type = parts[0].toInt(),
                        x1 = parts[1].toFloat(),
                        y1 = parts[2].toFloat(),
                        x2 = parts[3].toFloat(),
                        y2 = parts[4].toFloat()
                    )
                    7 -> Region(
                        type = parts[0].toInt(),
                        x1 = parts[1].toFloat(),
                        y1 = parts[2].toFloat(),
                        x2 = parts[3].toFloat(),
                        y2 = parts[4].toFloat(),
                        minPressure = parts[5].toFloat(),
                        maxDuration = parts[6].toLong()
                    )
                    8 -> Region(
                        isExclude = parts[0] == "1",
                        type = parts[1].toInt(),
                        x1 = parts[2].toFloat(),
                        y1 = parts[3].toFloat(),
                        x2 = parts[4].toFloat(),
                        y2 = parts[5].toFloat(),
                        minPressure = parts[6].toFloat(),
                        maxDuration = parts[7].toLong()
                    )
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
