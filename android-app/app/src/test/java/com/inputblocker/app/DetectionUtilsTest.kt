package com.inputblocker.app

import org.junit.Test
import org.junit.Assert.*

class DetectionUtilsTest {

    // ── Edge: empty input ───────────────────────────────────────

    @Test
    fun `detectRegions with empty input returns empty list`() {
        assertTrue(DetectionUtils.detectRegions(emptyList()).isEmpty())
    }

    // ── Noise handling ──────────────────────────────────────────

    @Test
    fun `detectRegions with all noise returns empty when below minPts`() {
        // 2 points with minPts=3 → no cluster can form
        val points = listOf(
            0.1f to 0.1f,
            0.11f to 0.1f
        )
        assertTrue(DetectionUtils.detectRegions(points, eps = 0.03f, minPts = 3).isEmpty())
    }

    @Test
    fun `detectRegions with exactly minPts minus one returns empty`() {
        val points = listOf(
            0.5f to 0.5f,
            0.51f to 0.5f
        )
        assertTrue(DetectionUtils.detectRegions(points, eps = 0.03f, minPts = 3).isEmpty())
    }

    // ── Single cluster ──────────────────────────────────────────

    @Test
    fun `detectRegions returns one region for a single cluster`() {
        // 3 points tightly grouped
        val points = listOf(
            0.1f to 0.1f,
            0.11f to 0.1f,
            0.09f to 0.1f
        )
        val regions = DetectionUtils.detectRegions(points)
        assertEquals(1, regions.size)
    }

    @Test
    fun `detectRegions with exactly minPts forms a cluster`() {
        val points = listOf(
            0.2f to 0.2f,
            0.21f to 0.2f,
            0.19f to 0.2f
        )
        val regions = DetectionUtils.detectRegions(points, eps = 0.03f, minPts = 3)
        assertEquals(1, regions.size)
    }

    // ── Multiple clusters ───────────────────────────────────────

    @Test
    fun `detectRegions returns two regions for two distant clusters`() {
        val points = listOf(
            // Cluster A (top-left)
            0.1f to 0.1f,
            0.11f to 0.1f,
            0.09f to 0.1f,
            // Cluster B (bottom-right)
            0.8f to 0.8f,
            0.81f to 0.8f,
            0.79f to 0.8f
        )
        val regions = DetectionUtils.detectRegions(points, eps = 0.03f, minPts = 3)
        assertEquals(2, regions.size)
    }

    // ── Epsilon sensitivity ─────────────────────────────────────

    @Test
    fun `detectRegions with tight eps separates nearby clusters`() {
        // Two clusters 0.05 apart (center-to-center), eps=0.03 → separate
        val points = listOf(
            0.1f to 0.1f, 0.11f to 0.1f, 0.09f to 0.1f,
            0.15f to 0.15f, 0.16f to 0.15f, 0.14f to 0.15f
        )
        val regions = DetectionUtils.detectRegions(points, eps = 0.03f, minPts = 3)
        assertEquals(2, regions.size)
    }

    @Test
    fun `detectRegions with wide eps merges nearby clusters`() {
        val points = listOf(
            0.1f to 0.1f, 0.11f to 0.1f, 0.09f to 0.1f,
            0.15f to 0.15f, 0.16f to 0.15f, 0.14f to 0.15f
        )
        val regions = DetectionUtils.detectRegions(points, eps = 0.1f, minPts = 3)
        assertEquals(1, regions.size)
    }

    // ── Duplicate / co-located points ───────────────────────────

    @Test
    fun `detectRegions with all points at same coordinate returns one region`() {
        val points = List(5) { 0.5f to 0.5f }
        val regions = DetectionUtils.detectRegions(points, eps = 0.03f, minPts = 3)
        assertEquals(1, regions.size)
    }

    // ── Bounding box geometry ───────────────────────────────────

    @Test
    fun `bounding box includes padding`() {
        val points = listOf(
            0.2f to 0.2f,
            0.21f to 0.2f,
            0.19f to 0.2f
        )
        val regions = DetectionUtils.detectRegions(points, eps = 0.03f, minPts = 3)
        assertEquals(1, regions.size)

        val r = regions[0]
        // Without padding: x1=0.19, y1=0.20, x2=0.21, y2=0.20
        // With 0.01 padding:  x1=0.18, y1=0.19, x2=0.22, y2=0.21
        assertEquals(0.18f, r.x1, 0.001f)
        assertEquals(0.19f, r.y1, 0.001f)
        assertEquals(0.22f, r.x2, 0.001f)
        assertEquals(0.21f, r.y2, 0.001f)
    }

    @Test
    fun `bounding box values are clamped to 0 to 1 range`() {
        val points = listOf(
            0.0f to 0.0f,
            0.01f to 0.0f,
            0.005f to 0.0f
        )
        val regions = DetectionUtils.detectRegions(points, eps = 0.03f, minPts = 3)
        assertEquals(1, regions.size)

        val r = regions[0]
        // Without clamp: x1 = -0.01, x1 would be < 0
        // x2 = 0.02 (0.01 + 0.01), but y2 = 0.01 (0.0 + 0.01)
        assertTrue("x1 should be >= 0", r.x1 >= 0f)
        assertTrue("y1 should be >= 0", r.y1 >= 0f)
        assertTrue("x2 should be <= 1", r.x2 <= 1f)
        assertTrue("y2 should be <= 1", r.y2 <= 1f)
    }

    @Test
    fun `bounding box has min-x less than or equal to max-x`() {
        val points = listOf(
            0.3f to 0.3f,
            0.35f to 0.3f,
            0.4f to 0.3f
        )
        val regions = DetectionUtils.detectRegions(points, eps = 0.1f, minPts = 3)
        assertEquals(1, regions.size)
        assertTrue("x1 <= x2", regions[0].x1 <= regions[0].x2)
        assertTrue("y1 <= y2", regions[0].y1 <= regions[0].y2)
    }

    // ── Default parameters ──────────────────────────────────────

    @Test
    fun `detectRegions uses default eps and minPts`() {
        // 4 points tight together → should cluster with defaults (eps=0.03, minPts=3)
        val points = listOf(
            0.5f to 0.5f,
            0.51f to 0.5f,
            0.49f to 0.5f,
            0.5f to 0.51f
        )
        val regions = DetectionUtils.detectRegions(points)
        assertEquals(1, regions.size)
    }

    @Test
    fun `detectRegions returns empty with 2 points and default minPts`() {
        // Default minPts=3, only 2 points → no cluster
        val points = listOf(
            0.5f to 0.5f,
            0.51f to 0.5f
        )
        assertTrue(DetectionUtils.detectRegions(points).isEmpty())
    }

    // ── Region properties ───────────────────────────────────────

    @Test
    fun `detected regions are blocking (not exclude) rectangles`() {
        val points = listOf(
            0.3f to 0.3f,
            0.31f to 0.3f,
            0.29f to 0.3f
        )
        val regions = DetectionUtils.detectRegions(points, eps = 0.03f, minPts = 3)
        assertEquals(1, regions.size)

        val r = regions[0]
        assertFalse("Region should not be an exclude zone", r.isExclude)
        assertEquals("Region type should be rectangle (0)", 0, r.type)
    }
}
