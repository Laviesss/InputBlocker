package com.inputblocker.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegionTest {

    @Test
    fun testRectContains() {
        val rect = Region(isExclude = false, type = 0, x1 = 0.1f, y1 = 0.1f, x2 = 0.5f, y2 = 0.5f)
        assertTrue(rect.contains(0.3f, 0.3f))
        assertTrue(rect.contains(0.1f, 0.1f))
        assertFalse(rect.contains(0.0f, 0.3f))
        assertFalse(rect.contains(0.6f, 0.3f))
    }

    @Test
    fun testCircleContains() {
        val circle = Region(isExclude = false, type = 1, x1 = 0.5f, y1 = 0.5f, x2 = 0.2f, y2 = 0.2f)
        assertTrue(circle.contains(0.5f, 0.5f))
        assertTrue(circle.contains(0.6f, 0.5f))
        assertFalse(circle.contains(0.8f, 0.5f))
    }

    @Test
    fun testEllipseContainsZeroRadius() {
        val ellipseZero = Region(isExclude = false, type = 2, x1 = 0.5f, y1 = 0.5f, x2 = 0f, y2 = 0f)
        assertFalse(ellipseZero.contains(0.5f, 0.5f))
    }

    @Test
    fun testFromStringParsingAndValidation() {
        val validStr = "0,0,0.1,0.1,0.5,0.5,0.15,300"
        val region = Region.fromString(validStr)
        assertNotNull(region)
        assertEquals(0.1f, region.x1)
        assertEquals(300L, region.maxDuration)

        val invalidPressure = "0,0,0.1,0.1,0.5,0.5,2.5,300"
        assertNull(Region.fromString(invalidPressure))

        val invalidFormat = "invalid,data"
        assertNull(Region.fromString(invalidFormat))
    }
}
