package com.wenhao.record.ui.map

import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.GeoMath
import kotlin.math.max

internal data class CollapsedRenderSegment(
    val points: List<TrackPoint>,
    val clusterCenters: List<TrackPoint>,
)

internal object TrackRenderClusterCollapser {
    private const val CLUSTER_RADIUS_METERS = 24f
    private const val CLUSTER_MIN_POINTS = 4
    private const val CLUSTER_MIN_PATH_METERS = 48f
    private const val CLUSTER_PATH_TO_RADIUS_FACTOR = 3.1f
    private const val CLUSTER_DEDUP_DISTANCE_METERS = 3f

    fun collapse(segment: List<TrackPoint>): CollapsedRenderSegment {
        if (segment.size < CLUSTER_MIN_POINTS) {
            return CollapsedRenderSegment(
                points = segment,
                clusterCenters = emptyList(),
            )
        }

        val collapsedPoints = mutableListOf<TrackPoint>()
        val clusterCenters = mutableListOf<TrackPoint>()
        var index = 0

        while (index < segment.size) {
            val clusterEndIndex = findClusterEndIndex(segment, index)
            if (clusterEndIndex == null) {
                appendIfDistinct(collapsedPoints, segment[index])
                index += 1
                continue
            }

            val clusterPoint = collapseCluster(segment.subList(index, clusterEndIndex + 1))
            appendIfDistinct(collapsedPoints, clusterPoint)
            clusterCenters += clusterPoint
            index = clusterEndIndex + 1
        }

        return CollapsedRenderSegment(
            points = collapsedPoints,
            clusterCenters = clusterCenters,
        )
    }

    private fun findClusterEndIndex(
        segment: List<TrackPoint>,
        startIndex: Int,
    ): Int? {
        if (startIndex + CLUSTER_MIN_POINTS > segment.size) return null

        val runPoints = mutableListOf<TrackPoint>()
        var centerLatitude = 0.0
        var centerLongitude = 0.0

        for (index in startIndex until segment.size) {
            val candidate = segment[index]
            val candidateLatitude = candidate.getLatitudeForDistance()
            val candidateLongitude = candidate.getLongitudeForDistance()

            val withinCluster = if (runPoints.isEmpty()) {
                true
            } else {
                GeoMath.distanceMeters(
                    centerLatitude,
                    centerLongitude,
                    candidateLatitude,
                    candidateLongitude,
                ) <= CLUSTER_RADIUS_METERS
            }

            if (!withinCluster) break

            runPoints += candidate
            val sampleCount = runPoints.size.toDouble()
            centerLatitude += (candidateLatitude - centerLatitude) / sampleCount
            centerLongitude += (candidateLongitude - centerLongitude) / sampleCount
        }

        if (runPoints.size < CLUSTER_MIN_POINTS) return null

        val pathDistanceMeters = runPoints.zipWithNext().sumOf { (start, end) ->
            GeoMath.distanceMeters(start, end).toDouble()
        }.toFloat()
        val maxRadiusMeters = runPoints.maxOf { point ->
            GeoMath.distanceMeters(
                centerLatitude,
                centerLongitude,
                point.getLatitudeForDistance(),
                point.getLongitudeForDistance(),
            ).toDouble()
        }.toFloat()
        val requiredPathDistanceMeters = max(
            CLUSTER_MIN_PATH_METERS,
            maxRadiusMeters * CLUSTER_PATH_TO_RADIUS_FACTOR,
        )
        if (pathDistanceMeters < requiredPathDistanceMeters) return null

        return startIndex + runPoints.lastIndex
    }

    private fun collapseCluster(points: List<TrackPoint>): TrackPoint {
        var latSum = 0.0
        var lonSum = 0.0
        var wgs84LatSum = 0.0
        var wgs84LonSum = 0.0
        var wgs84Count = 0
        var accSum = 0.0
        var accCount = 0
        var altSum = 0.0
        var altCount = 0

        for (p in points) {
            latSum += p.latitude
            lonSum += p.longitude
            if (p.wgs84Latitude != null) { wgs84LatSum += p.wgs84Latitude; wgs84Count++ }
            if (p.wgs84Longitude != null) { wgs84LonSum += p.wgs84Longitude; wgs84Count++ }
            if (p.accuracyMeters != null) { accSum += p.accuracyMeters.toDouble(); accCount++ }
            if (p.altitudeMeters != null) { altSum += p.altitudeMeters; altCount++ }
        }

        val size = points.size.toDouble()
        val centerLatitude = latSum / size
        val centerLongitude = lonSum / size
        val centerWgs84Latitude = if (wgs84Count > 0) wgs84LatSum / wgs84Count else null
        val centerWgs84Longitude = if (wgs84Count > 0) wgs84LonSum / wgs84Count else null
        val centerAccuracy = if (accCount > 0) (accSum / accCount).toFloat() else null
        val centerAltitude = if (altCount > 0) altSum / altCount else null
        val sample = points[points.size / 2]

        return sample.copy(
            latitude = centerLatitude,
            longitude = centerLongitude,
            accuracyMeters = centerAccuracy,
            altitudeMeters = centerAltitude,
            wgs84Latitude = centerWgs84Latitude,
            wgs84Longitude = centerWgs84Longitude,
        )
    }

    private fun appendIfDistinct(
        points: MutableList<TrackPoint>,
        candidate: TrackPoint,
    ) {
        val previous = points.lastOrNull()
        if (previous != null) {
            val distanceMeters = GeoMath.distanceMeters(previous, candidate)
            if (distanceMeters <= CLUSTER_DEDUP_DISTANCE_METERS) {
                points[points.lastIndex] = candidate
                return
            }
        }
        points += candidate
    }
}
