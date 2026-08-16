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
     * Used to find "hotspots" of ghost taps in the block logs.
     */
    fun clusterTaps(taps: List<GhostTap>, epsilon: Float, minPoints: Int): List<List<GhostTap>> {
        if (taps.isEmpty() || epsilon <= 0f || minPoints <= 0) return emptyList()

        val indexedTaps = taps.withIndex().toList()
        val clusters = mutableListOf<List<GhostTap>>()
        val visited = mutableSetOf<Int>()

        for ((index, tap) in indexedTaps) {
            if (index in visited) continue
            visited.add(index)

            val neighbors = findNeighborIndices(tap, indexedTaps, epsilon)
            if (neighbors.size >= minPoints) {
                val clusterIndices = mutableListOf<Int>()
                expandClusterIndices(index, neighbors, clusterIndices, visited, indexedTaps, epsilon, minPoints)
                clusters.add(clusterIndices.map { taps[it] })
            }
        }

        return clusters
    }

    private fun expandClusterIndices(
        startIndex: Int,
        initialNeighbors: List<Int>,
        clusterIndices: MutableList<Int>,
        visited: MutableSet<Int>,
        allTaps: List<IndexedValue<GhostTap>>,
        epsilon: Float,
        minPoints: Int
    ) {
        clusterIndices.add(startIndex)
        val queue = initialNeighbors.toMutableList()

        var i = 0
        while (i < queue.size) {
            val nextIndex = queue[i]
            if (nextIndex !in visited) {
                visited.add(nextIndex)
                val nextNeighbors = findNeighborIndices(allTaps[nextIndex].value, allTaps, epsilon)
                if (nextNeighbors.size >= minPoints) {
                    for (nn in nextNeighbors) {
                        if (nn !in queue) {
                            queue.add(nn)
                        }
                    }
                }
            }
            if (nextIndex !in clusterIndices) {
                clusterIndices.add(nextIndex)
            }
            i++
        }
    }

    private fun findNeighborIndices(
        target: GhostTap,
        allTaps: List<IndexedValue<GhostTap>>,
        epsilon: Float
    ): List<Int> {
        val result = mutableListOf<Int>()
        val epsSq = epsilon * epsilon
        for (item in allTaps) {
            val dx = target.x - item.value.x
            val dy = target.y - item.value.y
            if (dx * dx + dy * dy <= epsSq) {
                result.add(item.index)
            }
        }
        return result
    }

    /**
     * Calculates the smallest bounding box that encompasses a cluster of taps.
     * Returns a Region object.
     */
    fun calculateBoundingBox(cluster: List<GhostTap>): Region {
        if (cluster.isEmpty()) return Region(0f, 0f, 0f, 0f)

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

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
        val suggestedMinPressure = (maxNoisePressure + 0.02f).coerceIn(0f, 1f)

        // The min duration in the cluster is the "shortest" ghost tap.
        // We suggest a value slightly below this to block all ghost taps.
        val minNoiseDuration = cluster.minOf { it.duration }
        val suggestedMaxDuration = (minNoiseDuration - 50).coerceAtLeast(100L)

        return Pair(suggestedMinPressure, suggestedMaxDuration)
    }
}
