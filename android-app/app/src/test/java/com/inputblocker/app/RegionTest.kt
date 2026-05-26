package com.inputblocker.app

import com.inputblocker.shared.Region
import org.junit.Test
import org.junit.Assert.*

class RegionTest {

    @Test
    fun testRegionParsing() {
        // Test 4-part format (legacy)
        val r4 = Region.fromString("0.1,0.2,0.3,0.4")
        assertNotNull(r4)
        assertEquals(0.1f, r4!!.x1)
        assertEquals(0.4f, r4.y2)
        assertFalse(r4.isExclude)
        assertEquals(0, r4.type)

        // Test 5-part format
        val r5 = Region.fromString("1, 0.5, 0.5, 0.05, 0.05")
        assertNotNull(r5)
        assertEquals(1, r5!!.type)
        assertEquals(0.5f, r5.x1)

        // Test 8-part format (full)
        val r8 = Region.fromString("1, 0, 0.1, 0.1, 0.2, 0.2, 0.5, 500")
        assertNotNull(r8)
        assertTrue(r8!!.isExclude)
        assertEquals(0, r8.type)
        assertEquals(0.5f, r8.minPressure)
        assertEquals(500L, r8.maxDuration)

        // Test invalid format
        val rInvalid = Region.fromString("0.1,0.2")
        assertNull(rInvalid)
    }

    @Test
    fun testRegionSerialization() {
        val r = Region(isExclude = true, type = 2, x1 = 0.5f, y1 = 0.5f, x2 = 0.1f, y2 = 0.1f, minPressure = 0.2f, maxDuration = 1000L)
        val s = r.toString()
        assertEquals("1,2,0.5,0.5,0.1,0.1,0.2,1000", s)
        
        val rParsed = Region.fromString(s)
        assertEquals(r.isExclude, rParsed?.isExclude)
        assertEquals(r.type, rParsed?.type)
        assertEquals(r.x1, rParsed?.x1)
        assertEquals(r.maxDuration, rParsed?.maxDuration)
    }
}
