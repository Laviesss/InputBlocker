package com.inputblocker.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClusterUtilsTest {

    @Test
    fun testCalculateBoundingBoxZeroCoordinates() {
        val taps = listOf(
            GhostTap(x = 0.0f, y = 0.0f, pressure = 0.05f, duration = 100L, timestamp = "12:00:00")
        )
        val bbox = ClusterUtils.calculateBoundingBox(taps)
        assertEquals(0.0f, bbox.left)
        assertEquals(0.0f, bbox.top)
        assertTrue(bbox.right >= 0.0f)
        assertTrue(bbox.bottom >= 0.0f)
    }

    @Test
    fun testClusterTaps() {
        val taps = listOf(
            GhostTap(x = 0.10f, y = 0.10f, pressure = 0.02f, duration = 50L, timestamp = "1"),
            GhostTap(x = 0.11f, y = 0.11f, pressure = 0.03f, duration = 60L, timestamp = "2"),
            GhostTap(x = 0.90f, y = 0.90f, pressure = 0.01f, duration = 40L, timestamp = "3")
        )

        val clusters = ClusterUtils.clusterTaps(taps, epsilon = 0.05f, minPoints = 2)
        assertEquals(1, clusters.size)
        assertEquals(2, clusters[0].size)
    }

    @Test
    fun testSuggestThresholds() {
        val taps = listOf(
            GhostTap(x = 0.1f, y = 0.1f, pressure = 0.08f, duration = 200L, timestamp = "1"),
            GhostTap(x = 0.2f, y = 0.2f, pressure = 0.10f, duration = 150L, timestamp = "2")
        )
        val (suggestedPressure, suggestedDuration) = ClusterUtils.suggestThresholds(taps)
        assertEquals(0.12f, suggestedPressure, 0.001f)
        assertEquals(100L, suggestedDuration)
    }
}
