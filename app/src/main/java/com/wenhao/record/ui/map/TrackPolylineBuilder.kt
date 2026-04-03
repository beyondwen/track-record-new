package com.wenhao.record.ui.map

import androidx.compose.ui.graphics.toArgb
import com.wenhao.record.data.tracking.TrackPoint
import kotlin.math.roundToInt

internal object TrackPolylineBuilder {
    fun build(
        segments: List<List<TrackPoint>>,
        idPrefix: String,
        width: Double,
        fallbackColorArgb: Int = 0xFF5B8DEF.toInt(),
    ): List<TrackMapPolyline> {
        val altitudeRange = TrackAltitudePalette.altitudeRange(segments.flatten())
        return segments.flatMapIndexed { segmentIndex, segment ->
            if (segment.size < 2) {
                emptyList()
            } else {
                val polylines = ArrayList<TrackMapPolyline>(segment.size - 1)
                for (pairIndex in 0 until segment.size - 1) {
                    val start = segment[pairIndex]
                    val end = segment[pairIndex + 1]
                    val averageAltitude = averageAltitude(start, end)
                    polylines.add(
                        TrackMapPolyline(
                            id = "$idPrefix-$segmentIndex-$pairIndex",
                            points = listOf(start.toGeoCoordinate(), end.toGeoCoordinate()),
                            colorArgb = TrackAltitudePalette
                                .colorForAltitude(averageAltitude, altitudeRange)
                                .toArgbOrFallback(fallbackColorArgb),
                            width = width,
                        )
                    )
                }
                polylines
            }
        }
    }

    fun buildCompact(
        segments: List<List<TrackPoint>>,
        idPrefix: String,
        width: Double,
        fallbackColorArgb: Int = 0xFF5B8DEF.toInt(),
        maxPointsPerSegment: Int = 180,
        altitudeBuckets: Int = 7,
    ): List<TrackMapPolyline> {
        val altitudeRange = TrackAltitudePalette.altitudeRange(segments.flatten())
        return segments.flatMapIndexed { segmentIndex, segment ->
            buildCompactSegment(
                segment = segment.downsample(maxPointsPerSegment),
                segmentIndex = segmentIndex,
                idPrefix = idPrefix,
                width = width,
                altitudeRange = altitudeRange,
                fallbackColorArgb = fallbackColorArgb,
                altitudeBuckets = altitudeBuckets,
            )
        }
    }

    private fun buildCompactSegment(
        segment: List<TrackPoint>,
        segmentIndex: Int,
        idPrefix: String,
        width: Double,
        altitudeRange: ClosedFloatingPointRange<Double>?,
        fallbackColorArgb: Int,
        altitudeBuckets: Int,
    ): List<TrackMapPolyline> {
        if (segment.size < 2) return emptyList()

        val bucketCount = altitudeBuckets.coerceAtLeast(2)
        val polylines = mutableListOf<TrackMapPolyline>()
        var currentBucket = -1
        var currentPoints = mutableListOf<com.wenhao.record.map.GeoCoordinate>()
        var altitudeSum = 0.0
        var altitudeCount = 0
        var polylineIndex = 0

        for (i in 0 until segment.size - 1) {
            val start = segment[i]
            val end = segment[i + 1]
            val pairAverageAltitude = averageAltitude(start, end)
            val bucket = altitudeBucketFor(pairAverageAltitude, altitudeRange, bucketCount)
            val startCoordinate = start.toGeoCoordinate()
            val endCoordinate = end.toGeoCoordinate()

            if (currentBucket == -1) {
                currentBucket = bucket
                currentPoints.add(startCoordinate)
                currentPoints.add(endCoordinate)
                if (pairAverageAltitude != null) {
                    altitudeSum += pairAverageAltitude
                    altitudeCount += 1
                }
                continue
            }

            if (bucket == currentBucket) {
                currentPoints.add(endCoordinate)
                if (pairAverageAltitude != null) {
                    altitudeSum += pairAverageAltitude
                    altitudeCount += 1
                }
            } else {
                polylines += TrackMapPolyline(
                    id = "$idPrefix-$segmentIndex-$polylineIndex",
                    points = currentPoints.toList(),
                    colorArgb = TrackAltitudePalette
                        .colorForAltitude(
                            altitudeMeters = altitudeSum.takeIf { altitudeCount > 0 }?.div(altitudeCount),
                            altitudeRange = altitudeRange,
                        )
                        .toArgbOrFallback(fallbackColorArgb),
                    width = width,
                )
                polylineIndex += 1
                currentBucket = bucket
                currentPoints = mutableListOf(startCoordinate, endCoordinate)
                altitudeSum = pairAverageAltitude ?: 0.0
                altitudeCount = if (pairAverageAltitude != null) 1 else 0
            }
        }

        if (currentPoints.size >= 2) {
            polylines += TrackMapPolyline(
                id = "$idPrefix-$segmentIndex-$polylineIndex",
                points = currentPoints.toList(),
                colorArgb = TrackAltitudePalette
                    .colorForAltitude(
                        altitudeMeters = altitudeSum.takeIf { altitudeCount > 0 }?.div(altitudeCount),
                        altitudeRange = altitudeRange,
                    )
                    .toArgbOrFallback(fallbackColorArgb),
                width = width,
            )
        }

        return polylines
    }

    private fun altitudeBucketFor(
        altitudeMeters: Double?,
        altitudeRange: ClosedFloatingPointRange<Double>?,
        altitudeBuckets: Int,
    ): Int {
        altitudeMeters ?: return -1
        altitudeRange ?: return -1
        val start = altitudeRange.start
        val end = altitudeRange.endInclusive
        if (end - start < 0.1) return altitudeBuckets / 2
        val normalized = ((altitudeMeters - start) / (end - start)).coerceIn(0.0, 1.0)
        return (normalized * (altitudeBuckets - 1)).roundToInt().coerceIn(0, altitudeBuckets - 1)
    }

    private fun averageAltitude(start: TrackPoint, end: TrackPoint): Double? {
        return listOfNotNull(start.altitudeMeters, end.altitudeMeters)
            .average()
            .takeUnless { it.isNaN() }
    }

    private fun List<TrackPoint>.downsample(maxPoints: Int): List<TrackPoint> {
        if (size <= maxPoints || maxPoints < 3) return this

        val stride = (lastIndex).toDouble() / (maxPoints - 1).toDouble()
        val sampled = ArrayList<TrackPoint>(maxPoints)
        var previousIndex = -1

        repeat(maxPoints) { sampleIndex ->
            val sourceIndex = when (sampleIndex) {
                0 -> 0
                maxPoints - 1 -> lastIndex
                else -> (sampleIndex * stride).roundToInt().coerceIn(1, lastIndex - 1)
            }
            if (sourceIndex != previousIndex) {
                sampled += this[sourceIndex]
                previousIndex = sourceIndex
            }
        }

        if (sampled.lastOrNull() != last()) {
            sampled += last()
        }
        return sampled
    }

    private fun androidx.compose.ui.graphics.Color.toArgbOrFallback(fallback: Int): Int {
        return runCatching { toArgb() }.getOrDefault(fallback)
    }
}
