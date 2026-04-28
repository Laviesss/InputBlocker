package com.inputblocker.app

import java.io.Serializable

data class Region(
    var x1: Int = 0,
    var y1: Int = 0,
    var x2: Int = 0,
    var y2: Int = 0
) : Serializable
