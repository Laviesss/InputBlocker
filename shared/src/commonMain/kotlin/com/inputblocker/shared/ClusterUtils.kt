package com.inputblocker.shared

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
     * Core DBSCAN (Density-Based Spatial Clustering of Applications with Noise).
     *
     * Index-based: the "visited" set keys on list indices, not coordinate values,
     * so two taps at the exact same (x, y) stay distinct instead of collapsing
     * into a single point. Distance is compared squared (no sqrt) for speed.
     *
     * [xOf]/[yOf] extract coordinates so one algorithm serves both [GhostTap]
     * clusters and raw [Pair<Float, Float>] coordinate clusters.
     */
    private fun <T> clusterByIndex(
        items: List<T>,
        epsilon: Float,
        minPoints: Int,
        xOf: (T) -> Float,
        yOf: (T) -> Float
    ): List<List<T>> {
        if (items.isEmpty() || epsilon <= 0f || minPoints <= 0) return emptyList()

        val indexed = items.withIndex().toList()
        val clusters = mutableListOf<List<T>>()
        val visited = mutableSetOf<Int>()

        for ((index, item) in indexed) {
            if (index in visited) continue
            visited.add(index)

            val neighbors = findNeighborIndices(item, indexed, epsilon, xOf, yOf)
            if (neighbors.size >= minPoints) {
                val clusterIndices = mutableListOf<Int>()
                expandClusterIndices(index, neighbors, clusterIndices, visited, indexed, epsilon, minPoints, xOf, yOf)
                clusters.add(clusterIndices.map { items[it] })
            }
        }

        return clusters
    }

    private fun <T> expandClusterIndices(
        startIndex: Int,
        initialNeighbors: List<Int>,
        clusterIndices: MutableList<Int>,
        visited: MutableSet<Int>,
        allItems: List<IndexedValue<T>>,
        epsilon: Float,
        minPoints: Int,
        xOf: (T) -> Float,
        yOf: (T) -> Float
    ) {
        clusterIndices.add(startIndex)
        val queue = initialNeighbors.toMutableList()

        var i = 0
        while (i < queue.size) {
            val nextIndex = queue[i]
            if (nextIndex !in visited) {
                visited.add(nextIndex)
                val nextNeighbors = findNeighborIndices(allItems[nextIndex].value, allItems, epsilon, xOf, yOf)
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

    private fun <T> findNeighborIndices(
        target: T,
        allItems: List<IndexedValue<T>>,
        epsilon: Float,
        xOf: (T) -> Float,
        yOf: (T) -> Float
    ): List<Int> {
        val result = mutableListOf<Int>()
        val epsSq = epsilon * epsilon
        val tx = xOf(target)
        val ty = yOf(target)
        for (item in allItems) {
            val dx = tx - xOf(item.value)
            val dy = ty - yOf(item.value)
            if (dx * dx + dy * dy <= epsSq) {
                result.add(item.index)
            }
        }
        return result
    }

    /**
     * DBSCAN for [GhostTap] — clusters ghost tap logs into hotspots.
     */
    fun clusterTaps(taps: List<GhostTap>, epsilon: Float, minPoints: Int): List<List<GhostTap>> =
        clusterByIndex(taps, epsilon, minPoints, { it.x }, { it.y })

    /**
     * DBSCAN for raw [Pair<Float, Float>] coordinates — used by on-device detection
     * and anywhere that doesn't need full GhostTap metadata.
     */
    fun clusterPairs(
        points: List<Pair<Float, Float>>,
        epsilon: Float,
        minPoints: Int
    ): List<List<Pair<Float, Float>>> =
        clusterByIndex(points, epsilon, minPoints, { it.first }, { it.second })

    /**
     * Calculates the smallest bounding box that encompasses a cluster of taps.
     * Returns a Region object.
     */
    @JvmName("calculateBoundingBoxFromGhostTaps")
    fun calculateBoundingBox(cluster: List<GhostTap>): Region {
        if (cluster.isEmpty()) return Region(0f, 0f, 0f, 0f)
        return buildBoundingBox(
            minX = cluster.minOf { it.x },
            maxX = cluster.maxOf { it.x },
            minY = cluster.minOf { it.y },
            maxY = cluster.maxOf { it.y }
        )
    }

    /**
     * Calculates the smallest bounding box that encompasses a cluster of raw
     * [Pair<Float, Float>] coordinates. Returns a Region object.
     */
    @JvmName("calculateBoundingBoxFromPairs")
    fun calculateBoundingBox(points: List<Pair<Float, Float>>): Region {
        if (points.isEmpty()) return Region(0f, 0f, 0f, 0f)
        return buildBoundingBox(
            minX = points.minOf { it.first },
            maxX = points.maxOf { it.first },
            minY = points.minOf { it.second },
            maxY = points.maxOf { it.second }
        )
    }

    private fun buildBoundingBox(minX: Float, maxX: Float, minY: Float, maxY: Float): Region {
        val minSize = 0.05f // Minimum 5% screen size for reliable touch blocking
        val currentW = maxX - minX
        val currentH = maxY - minY

        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f

        val halfW = (if (currentW < minSize) minSize else currentW + 0.02f) / 2f
        val halfH = (if (currentH < minSize) minSize else currentH + 0.02f) / 2f

        val x1 = (cx - halfW).coerceIn(0f, 1f)
        val x2 = (cx + halfW).coerceIn(0f, 1f)
        val y1 = (cy - halfH).coerceIn(0f, 1f)
        val y2 = (cy + halfH).coerceIn(0f, 1f)

        return Region(
            isExclude = false,
            type = 0,
            x1 = x1,
            y1 = y1,
            x2 = x2,
            y2 = y2,
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
