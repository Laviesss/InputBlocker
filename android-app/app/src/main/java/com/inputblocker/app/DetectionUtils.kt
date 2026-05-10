package com.inputblocker.app

import kotlin.math.pow
import kotlin.math.sqrt

object DetectionUtils {

    /**
     * Uses a DBSCAN-inspired clustering algorithm to group raw touch points into regions.
     * 
     * @param points List of normalized touch coordinates (0.0 to 1.0).
     * @param eps The maximum distance between two points to be considered neighbors.
     *            Since points are normalized, 0.03 is roughly 3% of screen width.
     * @param minPts The minimum number of points required to form a dense region.
     * @return A list of suggested blocked regions.
     */
    fun detectRegions(
        points: List<Pair<Float, Float>>, 
        eps: Float = 0.03f, 
        minPts: Int = 3
    ): List<Region> {
        if (points.isEmpty()) return emptyList()

        val visited = mutableSetOf<Int>()
        val clusters = mutableListOf<MutableList<Pair<Float, Float>>>()

        for (i in points.indices) {
            if (i in visited) continue
            visited.add(i)

            val neighbors = findNeighbors(i, points, eps)

            if (neighbors.size >= minPts) {
                val cluster = mutableListOf<Pair<Float, Float>>()
                expandCluster(i, neighbors, points, visited, cluster, eps, minPts)
                clusters.add(cluster)
            }
        }

        return clusters.map { cluster ->
            calculateBoundingBox(cluster)
        }
    }

    private fun findNeighbors(
        index: Int, 
        points: List<Pair<Float, Float>>, 
        eps: Float
    ): MutableList<Int> {
        val neighbors = mutableListOf<Int>()
        val p1 = points[index]
        
        for (i in points.indices) {
            val p2 = points[i]
            val dist = sqrt(
                (p1.first - p2.first).toDouble().pow(2.0) + 
                (p1.second - p2.second).toDouble().pow(2.0)
            ).toFloat()
            
            if (dist <= eps) {
                neighbors.add(i)
            }
        }
        return neighbors
    }

    private fun expandCluster(
        rootIdx: Int,
        neighbors: MutableList<Int>,
        points: List<Pair<Float, Float>>,
        visited: MutableSet<Int>,
        cluster: MutableList<Pair<Float, Float>>,
        eps: Float,
        minPts: Int
    ) {
        cluster.add(points[rootIdx])
        
        var i = 0
        while (i < neighbors.size) {
            val nextIdx = neighbors[i]
            
            if (nextIdx !in visited) {
                visited.add(nextIdx)
                val nextNeighbors = findNeighbors(nextIdx, points, eps)
                if (nextNeighbors.size >= minPts) {
                    neighbors.addAll(nextNeighbors.filter { it !in neighbors })
                }
            }
            
            // If the point is part of any cluster, it belongs to this one too
            // (Actually DBSCAN typically assigns to the first cluster it finds)
            // We add the point to the cluster if it hasn't been added yet
            val p = points[nextIdx]
            if (p !in cluster) {
                cluster.add(p)
            }
            
            i++
        }
    }

    private fun calculateBoundingBox(points: List<Pair<Float, Float>>): Region {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (p in points) {
            if (p.first < minX) minX = p.first
            if (p.first > maxX) maxX = p.first
            if (p.second < minY) minY = p.second
            if (p.second > maxY) maxY = p.second
        }

        // Add a small padding (1% of screen) to ensure the ghost taps are fully covered
        val padding = 0.01f
        return Region(
            x1 = (minX - padding).coerceAtLeast(0f),
            y1 = (minY - padding).coerceAtLeast(0f),
            x2 = (maxX + padding).coerceAtMost(1f),
            y2 = (maxY + padding).coerceAtMost(1f)
        )
    }
}
