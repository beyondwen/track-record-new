package com.wenhao.record.data.tracking

import com.wenhao.record.map.GeoMath
import kotlin.math.max

data class SanitizedTrackPath(
    val points: List<TrackPoint>,
    val segments: List<List<TrackPoint>>,
    val totalDistanceKm: Double,
    val removedPointCount: Int
)

object TrackPathSanitizer {

    private const val MAX_POOR_ACCURACY_METERS = 90f
    private const val MAX_JUMP_DISTANCE_METERS = 220f
    private const val MAX_JUMP_SPEED_METERS_PER_SECOND = 60f
    private const val MAX_NO_TIMESTAMP_SPLIT_DISTANCE_METERS = 1_200f

    fun sanitize(points: List<TrackPoint>, sortByTimestamp: Boolean): SanitizedTrackPath {
        if (points.isEmpty()) {
            return SanitizedTrackPath(
                points = emptyList(),
                segments = emptyList(),
                totalDistanceKm = 0.0,
                removedPointCount = 0
            )
        }

        val orderedPoints = orderPoints(points, sortByTimestamp)
        val segments = mutableListOf<MutableList<TrackPoint>>()
        var removedPointCount = 0

        orderedPoints.forEach { point ->
            if (!point.hasValidCoordinate()) {
                removedPointCount++
                return@forEach
            }

            val currentSegment = segments.lastOrNull()
            if (currentSegment == null || currentSegment.isEmpty()) {
                segments += mutableListOf(point)
                return@forEach
            }

            when (classifyPoint(currentSegment.last(), point)) {
                SanitizerAction.KEEP -> currentSegment += point
                SanitizerAction.START_NEW_SEGMENT -> segments += mutableListOf(point)
                SanitizerAction.DROP -> removedPointCount++
            }
        }

        val finalizedSegments = segments
            .map { segment -> segment.toList() }
            .filter { segment -> segment.isNotEmpty() }

        return SanitizedTrackPath(
            points = finalizedSegments.flatten(),
            segments = finalizedSegments,
            totalDistanceKm = finalizedSegments.sumOf(::calculateSegmentDistanceKm),
            removedPointCount = removedPointCount
        )
    }

    private fun orderPoints(points: List<TrackPoint>, sortByTimestamp: Boolean): List<TrackPoint> {
        if (!sortByTimestamp) return points.toList()

        val validTimestampCount = points.count { it.timestampMillis > 0L }
        if (validTimestampCount < 2) return points.toList()

        return points.sortedWith(
            compareBy<TrackPoint> { point ->
                if (point.timestampMillis > 0L) point.timestampMillis else Long.MAX_VALUE
            }.thenBy { point -> point.latitude }
                .thenBy { point -> point.longitude }
        )
    }

    private fun classifyPoint(previous: TrackPoint, candidate: TrackPoint): SanitizerAction {
        // Check if both points have WGS-84 coordinates for accurate distance calculation
        val hasWgs84Coords = previous.wgs84Latitude != null && previous.wgs84Longitude != null &&
            candidate.wgs84Latitude != null && candidate.wgs84Longitude != null

        val distanceMeters = distanceBetween(previous, candidate)
        val maxAccuracyMeters = max(
            previous.accuracyMeters ?: 0f,
            candidate.accuracyMeters ?: 0f
        )
        val poorAccuracy = (candidate.accuracyMeters ?: 0f) >= MAX_POOR_ACCURACY_METERS
        val tinyMoveThreshold = max(4f, maxAccuracyMeters * 0.22f)

        if (distanceMeters <= tinyMoveThreshold) {
            return SanitizerAction.KEEP
        }

        val hasTimestamps = previous.timestampMillis > 0L && candidate.timestampMillis > 0L
        if (!hasTimestamps) {
            if (poorAccuracy && distanceMeters >= 100f) {
                return SanitizerAction.DROP
            }
            if (distanceMeters >= MAX_NO_TIMESTAMP_SPLIT_DISTANCE_METERS) {
                return SanitizerAction.START_NEW_SEGMENT
            }
            return SanitizerAction.KEEP
        }

        val timeDeltaMillis = candidate.timestampMillis - previous.timestampMillis
        if (timeDeltaMillis <= 0L) {
            return SanitizerAction.DROP
        }

        val inferredSpeedMetersPerSecond = distanceMeters / max(timeDeltaMillis / 1000f, 1f)

        // For old data without WGS-84 coordinates, use more lenient thresholds
        // because GCJ-02 coordinates can cause artificial distance inflation
        val effectiveMaxJumpDistance = if (hasWgs84Coords) {
            MAX_JUMP_DISTANCE_METERS
        } else {
            MAX_JUMP_DISTANCE_METERS * 3  // More lenient for old data
        }
        val effectiveMaxJumpSpeed = if (hasWgs84Coords) {
            MAX_JUMP_SPEED_METERS_PER_SECOND
        } else {
            MAX_JUMP_SPEED_METERS_PER_SECOND * 3  // More lenient for old data
        }

        if (poorAccuracy &&
            inferredSpeedMetersPerSecond <= 2.5f &&
            distanceMeters <= max(35f, maxAccuracyMeters * 0.9f)
        ) {
            return SanitizerAction.DROP
        }

        if (distanceMeters >= max(effectiveMaxJumpDistance, maxAccuracyMeters * 2.8f) &&
            inferredSpeedMetersPerSecond >= effectiveMaxJumpSpeed
        ) {
            return SanitizerAction.START_NEW_SEGMENT
        }

        if (poorAccuracy && distanceMeters >= 200f) {
            return SanitizerAction.START_NEW_SEGMENT
        }

        return SanitizerAction.KEEP
    }

    private fun calculateSegmentDistanceKm(segment: List<TrackPoint>): Double {
        if (segment.size < 2) return 0.0
        return segment.zipWithNext().sumOf { (first, second) ->
            distanceBetween(first, second).toDouble() / 1000.0
        }
    }

    private fun distanceBetween(first: TrackPoint, second: TrackPoint): Float {
        return GeoMath.distanceMeters(first, second)
    }

    private fun TrackPoint.hasValidCoordinate(): Boolean {
        // Basic range check
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return false
        }
        // Check for zero coordinates (likely invalid)
        if (latitude == 0.0 && longitude == 0.0) {
            return false
        }
        // Check for WGS-84 coordinates if available
        wgs84Latitude?.let {
            if (it !in -90.0..90.0) return false
        }
        wgs84Longitude?.let {
            if (it !in -180.0..180.0) return false
        }
        return true
    }

    private enum class SanitizerAction {
        KEEP,
        START_NEW_SEGMENT,
        DROP
    }
}
