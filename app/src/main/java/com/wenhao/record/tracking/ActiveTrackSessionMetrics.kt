package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.GeoMath

internal object ActiveTrackSessionMetrics {
    private const val MIN_EFFECTIVE_DISTANCE_METERS = 2.5f

    fun updatedDistanceKm(
        previousDistanceKm: Double,
        previousPoints: List<TrackPoint>,
        updatedPoints: List<TrackPoint>,
    ): Double {
        if (updatedPoints === previousPoints) return previousDistanceKm
        if (updatedPoints == previousPoints) return previousDistanceKm
        if (updatedPoints.size < 2) return 0.0

        val isSimpleAppend = previousPoints.isNotEmpty() &&
            updatedPoints.size == previousPoints.size + 1 &&
            isIdenticalPrefix(previousPoints, updatedPoints)
        if (isSimpleAppend) {
            val deltaMeters = GeoMath.distanceMeters(previousPoints.last(), updatedPoints.last())
            return previousDistanceKm + effectiveDistanceKm(deltaMeters)
        }

        return calculateDistanceKm(updatedPoints)
    }

    private fun isIdenticalPrefix(previous: List<TrackPoint>, updated: List<TrackPoint>): Boolean {
        // Traverse backwards as differences are usually at the end
        for (i in previous.indices.reversed()) {
            if (previous[i] !== updated[i] && previous[i] != updated[i]) {
                return false
            }
        }
        return true
    }

    fun calculateDistanceKm(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var totalDistanceKm = 0.0
        for (i in 0 until points.size - 1) {
            totalDistanceKm += effectiveDistanceKm(GeoMath.distanceMeters(points[i], points[i + 1]))
        }
        return totalDistanceKm
    }

    private fun effectiveDistanceKm(distanceMeters: Float): Double {
        return if (distanceMeters > MIN_EFFECTIVE_DISTANCE_METERS) {
            distanceMeters / 1000.0
        } else {
            0.0
        }
    }
}
