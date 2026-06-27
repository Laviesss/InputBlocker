package com.inputblocker.app

import com.inputblocker.shared.ClusterUtils
import com.inputblocker.shared.GhostTap
import com.inputblocker.shared.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterUtilsTest {

    // ── clusterTaps ──────────────────────────────────────────────────────

    @Test
    fun `clusterTaps with empty list returns empty`() {
        val result = ClusterUtils.clusterTaps(emptyList(), 0.1f, 3)
        assertTrue("Expected empty result for empty input", result.isEmpty())
    }

    @Test
    fun `clusterTaps with fewer taps than minPoints returns empty`() {
        val taps = listOf(
            GhostTap(0.5f, 0.5f, 0.05f, 200L, "t1")
        )
        val result = ClusterUtils.clusterTaps(taps, 0.1f, 3)
        assertTrue("Expected empty when taps < minPoints", result.isEmpty())
    }

    @Test
    fun `clusterTaps with exactly minPoints close together forms one cluster`() {
        val taps = listOf(
            GhostTap(0.5f, 0.5f, 0.05f, 200L, "t1"),
            GhostTap(0.51f, 0.5f, 0.05f, 200L, "t2"),
            GhostTap(0.5f, 0.51f, 0.05f, 200L, "t3")
        )
        val result = ClusterUtils.clusterTaps(taps, 0.02f, 3)
        assertEquals("Expected exactly 1 cluster", 1, result.size)
        assertEquals("Expected all 3 taps in cluster", 3, result[0].size)
    }

    @Test
    fun `clusterTaps with two distant clusters returns two clusters`() {
        val taps = listOf(
            GhostTap(0.1f, 0.1f, 0.05f, 200L, "t1"),
            GhostTap(0.11f, 0.1f, 0.05f, 200L, "t2"),
            GhostTap(0.1f, 0.11f, 0.05f, 200L, "t3"),
            GhostTap(0.8f, 0.8f, 0.05f, 200L, "t4"),
            GhostTap(0.81f, 0.8f, 0.05f, 200L, "t5"),
            GhostTap(0.8f, 0.81f, 0.05f, 200L, "t6")
        )
        val result = ClusterUtils.clusterTaps(taps, 0.02f, 3)
        assertEquals("Expected exactly 2 clusters", 2, result.size)
        assertEquals("Expected 3 taps in first cluster", 3, result[0].size)
        assertEquals("Expected 3 taps in second cluster", 3, result[1].size)
    }

    @Test
    fun `clusterTaps with loose epsilon merges clusters`() {
        val taps = listOf(
            GhostTap(0.1f, 0.1f, 0.05f, 200L, "t1"),
            GhostTap(0.1f, 0.11f, 0.05f, 200L, "t2"),
            GhostTap(0.11f, 0.1f, 0.05f, 200L, "t3"),
            GhostTap(0.8f, 0.8f, 0.05f, 200L, "t4"),
            GhostTap(0.8f, 0.81f, 0.05f, 200L, "t5"),
            GhostTap(0.81f, 0.8f, 0.05f, 200L, "t6")
        )
        // Epsilon large enough to span both clusters
        val result = ClusterUtils.clusterTaps(taps, 1.0f, 3)
        assertEquals("Expected 1 merged cluster with large epsilon", 1, result.size)
        assertEquals("Expected all 6 taps in merged cluster", 6, result[0].size)
    }

    @Test
    fun `clusterTaps with noise points only returns empty`() {
        val taps = listOf(
            GhostTap(0.1f, 0.1f, 0.05f, 200L, "t1"),
            GhostTap(0.9f, 0.9f, 0.05f, 200L, "t2"),
            GhostTap(0.2f, 0.8f, 0.05f, 200L, "t3")
        )
        val result = ClusterUtils.clusterTaps(taps, 0.01f, 3)
        assertTrue("Expected empty when all points are noise", result.isEmpty())
    }

    @Test
    fun `clusterTaps with co-located points treats identical coordinates as close`() {
        val taps = listOf(
            GhostTap(0.5f, 0.5f, 0.05f, 200L, "t1"),
            GhostTap(0.5f, 0.5f, 0.05f, 200L, "t2"),
            GhostTap(0.5f, 0.5f, 0.05f, 200L, "t3")
        )
        val result = ClusterUtils.clusterTaps(taps, 0.001f, 3)
        assertEquals("Expected 1 cluster for co-located points", 1, result.size)
    }

    @Test
    fun `clusterTaps with chain of close points forms one cluster`() {
        val taps = (0 until 5).map { i ->
            val x = 0.1f + i * 0.015f
            GhostTap(x, 0.5f, 0.05f, 200L, "t$i")
        }
        // Each point is within 0.02 of its neighbor, forming a chain
        val result = ClusterUtils.clusterTaps(taps, 0.02f, 3)
        assertEquals("Expected 1 cluster from chain", 1, result.size)
        assertEquals("Expected all 5 taps in chain cluster", 5, result[0].size)
    }

    @Test
    fun `clusterTaps with borderline points at epsilon edge`() {
        val taps = listOf(
            GhostTap(0.5f, 0.5f, 0.05f, 200L, "t1"),
            GhostTap(0.51f, 0.5f, 0.05f, 200L, "t2"), // 0.01 away from both
            GhostTap(0.52f, 0.5f, 0.05f, 200L, "t3")  // 0.01 away from t2
        )
        val result = ClusterUtils.clusterTaps(taps, 0.01f, 3)
        // t2 has 3 neighbors (t1, t2, t3) within epsilon → core point
        // → creates cluster containing all 3 points via expansion
        assertEquals("Expected 1 cluster from border-case chain", 1, result.size)
        assertEquals("Expected all 3 taps in cluster", 3, result[0].size)
    }

    @Test
    fun `clusterTaps uses default minPoints of 3 when not specified`() {
        val twoTapsClose = listOf(
            GhostTap(0.5f, 0.5f, 0.05f, 200L, "t1"),
            GhostTap(0.51f, 0.5f, 0.05f, 200L, "t2")
        )
        val result = ClusterUtils.clusterTaps(twoTapsClose, 0.02f, 3)
        assertTrue("Expected empty with 2 taps and minPoints=3", result.isEmpty())
    }

    // ── calculateBoundingBox ──────────────────────────────────────────────

    @Test
    fun `calculateBoundingBox with empty cluster returns zero region`() {
        val region = ClusterUtils.calculateBoundingBox(emptyList())
        assertEquals(0f, region.x1, 0.001f)
        assertEquals(0f, region.y1, 0.001f)
        assertEquals(0f, region.x2, 0.001f)
        assertEquals(0f, region.y2, 0.001f)
    }

    @Test
    fun `calculateBoundingBox with single point adds padding`() {
        val cluster = listOf(GhostTap(0.5f, 0.5f, 0.05f, 200L, "t1"))
        val region = ClusterUtils.calculateBoundingBox(cluster)
        assertEquals(0.49f, region.x1, 0.001f)
        assertEquals(0.49f, region.y1, 0.001f)
        assertEquals(0.51f, region.x2, 0.001f)
        assertEquals(0.51f, region.y2, 0.001f)
    }

    @Test
    fun `calculateBoundingBox encloses all points`() {
        val cluster = listOf(
            GhostTap(0.2f, 0.3f, 0.05f, 200L, "t1"),
            GhostTap(0.7f, 0.6f, 0.05f, 200L, "t2"),
            GhostTap(0.4f, 0.9f, 0.05f, 200L, "t3")
        )
        val region = ClusterUtils.calculateBoundingBox(cluster)
        assertTrue("x1 should enclose leftmost", region.x1 <= 0.2f)
        assertTrue("y1 should enclose topmost", region.y1 <= 0.3f)
        assertTrue("x2 should enclose rightmost", region.x2 >= 0.7f)
        assertTrue("y2 should enclose bottommost", region.y2 >= 0.9f)
    }

    @Test
    fun `calculateBoundingBox clamps padding to 0 and 1`() {
        val cluster = listOf(GhostTap(0.0f, 1.0f, 0.05f, 200L, "t1"))
        val region = ClusterUtils.calculateBoundingBox(cluster)
        assertEquals(0f, region.x1, 0.001f)
        assertEquals(0.99f, region.y1, 0.001f)
        assertEquals(0.01f, region.x2, 0.001f)
        assertEquals(1f, region.y2, 0.001f)
    }

    @Test
    fun `calculateBoundingBox returns Region with correct defaults`() {
        val cluster = listOf(GhostTap(0.5f, 0.5f, 0.05f, 200L, "t1"))
        val region = ClusterUtils.calculateBoundingBox(cluster)
        assertEquals(false, region.isExclude)
        assertEquals(0, region.type)
        assertEquals(0f, region.minPressure, 0f)
        assertEquals(1000L, region.maxDuration)
    }

    // ── suggestThresholds ─────────────────────────────────────────────────

    @Test
    fun `suggestThresholds with empty cluster returns defaults`() {
        val (pressure, duration) = ClusterUtils.suggestThresholds(emptyList())
        assertEquals(0.1f, pressure, 0.001f)
        assertEquals(1000L, duration)
    }

    @Test
    fun `suggestThresholds uses max pressure plus small offset`() {
        val cluster = listOf(
            GhostTap(0.5f, 0.5f, 0.10f, 200L, "t1"),
            GhostTap(0.5f, 0.5f, 0.15f, 200L, "t2"),
            GhostTap(0.5f, 0.5f, 0.12f, 200L, "t3")
        )
        val (pressure, _) = ClusterUtils.suggestThresholds(cluster)
        // max pressure = 0.15, suggested = 0.15 + 0.02 = 0.17
        assertEquals(0.17f, pressure, 0.001f)
    }

    @Test
    fun `suggestThresholds uses min duration minus offset floored at 100`() {
        val cluster = listOf(
            GhostTap(0.5f, 0.5f, 0.05f, 300L, "t1"),
            GhostTap(0.5f, 0.5f, 0.05f, 500L, "t2")
        )
        val (_, duration) = ClusterUtils.suggestThresholds(cluster)
        // min duration = 300, suggested = 300 - 50 = 250
        assertEquals(250L, duration)
    }

    @Test
    fun `suggestThresholds floors duration at 100ms`() {
        val cluster = listOf(
            GhostTap(0.5f, 0.5f, 0.05f, 80L, "t1")
        )
        val (_, duration) = ClusterUtils.suggestThresholds(cluster)
        // min duration = 80, suggested = 80 - 50 = 30 → coerceAtLeast(100) = 100
        assertEquals(100L, duration)
    }
}
