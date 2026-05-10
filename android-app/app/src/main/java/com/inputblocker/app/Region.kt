package com.inputblocker.app

import java.io.Serializable

data class Region(
    var x1: Float = 0f,
    var y1: Float = 0f,
    var x2: Float = 0f,
    var y2: Float = 0f
) : Serializable
