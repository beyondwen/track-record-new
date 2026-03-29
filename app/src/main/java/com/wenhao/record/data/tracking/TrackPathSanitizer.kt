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
    private const val MIN_SPIKE_EDGE_DISTANCE_METERS = 48f
    private const val MIN_SPIKE_EXCESS_DISTANCE_METERS = 72f
    private const val MAX_SPIKE_EDGE_DURATION_MILLIS = 75_000L
    private const val MAX_SPIKE_WINDOW_MILLIS = 120_000L
    private const val MIN_RENDERABLE_SEGMENT_DISTANCE_METERS = 40f
    private const val MIN_SIGNIFICANT_SEGMENT_DISTANCE_METERS = 78f

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
        val validPoints = orderedPoints.filter { point -> point.hasValidCoordinate() }
        val invalidPointCount = orderedPoints.size - validPoints.size
        val spikeCleaned = removeSpikeOutliers(validPoints)
        val segments = mutableListOf<MutableList<TrackPoint>>()
        var removedPointCount = invalidPointCount + spikeCleaned.removedPointCount

        spikeCleaned.points.forEach { point ->
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

    fun renderableSegments(segments: List<List<TrackPoint>>): List<List<TrackPoint>> {
        val normalizedSegments = segments
            .map { segment -> segment.toList() }
            .filter { segment -> segment.isNotEmpty() }
        if (normalizedSegments.isEmpty()) return emptyList()
        if (normalizedSegments.size == 1) {
            return normalizedSegments.filter(::isStandaloneSegmentRenderable)
        }

        val filteredSegments = normalizedSegments.filter(::isMultiSegmentRenderable)
        if (filteredSegments.isNotEmpty()) return filteredSegments

        return listOfNotNull(
            normalizedSegments.maxByOrNull(::segmentRenderScore)
                ?.takeIf(::isStandaloneSegmentRenderable)
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

    private fun removeSpikeOutliers(points: List<TrackPoint>): SpikeCleanupResult {
        if (points.size < 3) {
            return SpikeCleanupResult(points = points, removedPointCount = 0)
        }

        val cleanedPoints = mutableListOf<TrackPoint>()
        cleanedPoints += points.first()
        var removedPointCount = 0
        var index = 1

        while (index < points.lastIndex) {
            val previous = cleanedPoints.last()
            val current = points[index]
            val next = points[index + 1]

            if (shouldDropSpike(previous, current, next)) {
                removedPointCount++
                index++
                continue
            }

            cleanedPoints += current
            index++
        }

        cleanedPoints += points.last()
        return SpikeCleanupResult(
            points = cleanedPoints,
            removedPointCount = removedPointCount
        )
    }

    private fun shouldDropSpike(previous: TrackPoint, current: TrackPoint, next: TrackPoint): Boolean {
        val previousToCurrent = distanceBetween(previous, current)
        val currentToNext = distanceBetween(current, next)
        val previousToNext = distanceBetween(previous, next)
        val maxAccuracyMeters = maxOf(
            previous.accuracyMeters ?: 0f,
            current.accuracyMeters ?: 0f,
            next.accuracyMeters ?: 0f,
        )
        val edgeDistanceThreshold = max(MIN_SPIKE_EDGE_DISTANCE_METERS, maxAccuracyMeters * 1.45f)
        val bridgeDistanceThreshold = max(18f, maxAccuracyMeters * 0.7f)
        val detourExcess = previousToCurrent + currentToNext - previousToNext

        if (previousToCurrent < edgeDistanceThreshold || currentToNext < edgeDistanceThreshold) {
            return false
        }
        if (previousToNext > max(bridgeDistanceThreshold, minOf(previousToCurrent, currentToNext) * 0.34f)) {
            return false
        }
        if (detourExcess < max(MIN_SPIKE_EXCESS_DISTANCE_METERS, maxAccuracyMeters * 1.6f)) {
            return false
        }

        val hasTimestamps = previous.timestampMillis > 0L &&
            current.timestampMillis > 0L &&
            next.timestampMillis > 0L
        if (!hasTimestamps) {
            return true
        }

        val firstDeltaMillis = current.timestampMillis - previous.timestampMillis
        val secondDeltaMillis = next.timestampMillis - current.timestampMillis
        if (firstDeltaMillis <= 0L || secondDeltaMillis <= 0L) {
            return false
        }

        return firstDeltaMillis <= MAX_SPIKE_EDGE_DURATION_MILLIS &&
            secondDeltaMillis <= MAX_SPIKE_EDGE_DURATION_MILLIS &&
            firstDeltaMillis + secondDeltaMillis <= MAX_SPIKE_WINDOW_MILLIS
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

    private fun calculateSegmentDistanceMeters(segment: List<TrackPoint>): Float {
        if (segment.size < 2) return 0f
        return segment.zipWithNext().sumOf { (first, second) ->
            distanceBetween(first, second).toDouble()
        }.toFloat()
    }

    private fun isStandaloneSegmentRenderable(segment: List<TrackPoint>): Boolean {
        return segment.size > 1 || calculateSegmentDistanceMeters(segment) > 0f
    }

    private fun isMultiSegmentRenderable(segment: List<TrackPoint>): Boolean {
        if (segment.size >= 4) return true

        val distanceMeters = calculateSegmentDistanceMeters(segment)
        if (segment.size >= 3 && distanceMeters >= MIN_RENDERABLE_SEGMENT_DISTANCE_METERS) {
            return true
        }

        return distanceMeters >= MIN_SIGNIFICANT_SEGMENT_DISTANCE_METERS
    }

    private fun segmentRenderScore(segment: List<TrackPoint>): Float {
        return calculateSegmentDistanceMeters(segment) + segment.size * 14f
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

    private data class SpikeCleanupResult(
        val points: List<TrackPoint>,
        val removedPointCount: Int,
    )
}
