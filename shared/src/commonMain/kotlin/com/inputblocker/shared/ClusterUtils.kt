package com.inputblocker.shared

import kotlin.math.sqrt

data class Point(val x: Float, val y: Float)

data class GhostTap(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val duration: Long,
    val timestamp: String
)

object ClusterUtils {
    /**
     * Implements DBSCAN (Density-Based Spatial Clustering of Applications with Noise).
     * This is used to find "hotspots" of ghost taps in the block logs.
     */
    fun clusterTaps(taps: List<GhostTap>, epsilon: Float, minPoints: Int): List<List<GhostTap>> {
        if (taps.isEmpty()) return emptyList()

        val points = taps.map { Point(it.x, it.y) }
        val clusters = mutableListOf<MutableList<Point>>()
        val visited = mutableSetOf<Point>()
        val noise = mutableSetOf<Point>()

        for (point in points) {
            if (point in visited) continue
            visited.add(point)

            val neighbors = findNeighbors(point, points, epsilon)
            if (neighbors.size < minPoints) {
                noise.add(point)
            } else {
                val cluster = mutableListOf<Point>()
                expandCluster(point, neighbors, clusters, cluster, visited, points, epsilon, minPoints)
                clusters.add(cluster)
            }
        }

        // Map Points back to GhostTaps
        return clusters.map { clusterPoints ->
            taps.filter { tap -> Point(tap.x, tap.y) in clusterPoints }
        }
    }

    private fun expandCluster(
        point: Point,
        neighbors: List<Point>,
        clusters: MutableList<MutableList<Point>>,
        cluster: MutableList<Point>,
        visited: MutableSet<Point>,
        allPoints: List<Point>,
        epsilon: Float,
        minPoints: Int
    ) {
        cluster.add(point)
        val queue = neighbors.toMutableList()

        var i = 0
        while (i < queue.size) {
            val nextPoint = queue[i]
            if (nextPoint !in visited) {
                visited.add(nextPoint)
                val nextNeighbors = findNeighbors(nextPoint, allPoints, epsilon)
                if (nextNeighbors.size >= minPoints) {
                    queue.addAll(nextNeighbors.filter { it !in visited })
                }
            }
            if (nextPoint !in cluster) {
                cluster.add(nextPoint)
            }
            i++
        }
    }

    private fun findNeighbors(point: Point, allPoints: List<Point>, epsilon: Float): List<Point> {
        return allPoints.filter { other ->
            val dx = point.x - other.x
            val dy = point.y - other.y
            sqrt(dx * dx + dy * dy) <= epsilon
        }
    }

    /**
     * Calculates the smallest bounding box that encompasses a cluster of taps.
     * Returns a Region object.
     */
    fun calculateBoundingBox(cluster: List<GhostTap>): Region {
        if (cluster.isEmpty()) return Region(0f, 0f, 0f, 0f)

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (tap in cluster) {
            if (tap.x < minX) minX = tap.x
            if (tap.x > maxX) maxX = tap.x
            if (tap.y < minY) minY = tap.y
            if (tap.y > maxY) maxY = tap.y
        }

        val padding = 0.01f
        return Region(
            isExclude = false,
            type = 0,
            x1 = (minX - padding).coerceAtLeast(0f),
            y1 = (minY - padding).coerceAtLeast(0f),
            x2 = (maxX + padding).coerceAtMost(1f),
            y2 = (maxY + padding).coerceAtMost(1f),
            minPressure = 0f,
            maxDuration = 1000L
        )
    }

    /**
     * Suggests optimal pressure and duration thresholds based on a cluster of ghost taps.
     * Returns a Pair(suggestedMinPressure, suggestedMaxDuration).
     */
    fun suggestThresholds(cluster: List<GhostTap>): Pair<Float, Long> {
        if (cluster.isEmpty()) return Pair(0.1f, 1000L)
        
        // The max pressure in the cluster is the "upper bound" of the noise.
        // We suggest a value slightly above this to block all noise but allow fingers.
        val maxNoisePressure = cluster.maxOf { it.pressure }
        val suggestedMinPressure = maxNoisePressure + 0.02f
        
        // The min duration in the cluster is the "shortest" ghost tap.
        // We suggest a value slightly below this to block all ghost taps.
        val minNoiseDuration = cluster.minOf { it.duration }
        val suggestedMaxDuration = (minNoiseDuration - 50).coerceAtLeast(100L)
        
        return Pair(suggestedMinPressure, suggestedMaxDuration)
    }
}
