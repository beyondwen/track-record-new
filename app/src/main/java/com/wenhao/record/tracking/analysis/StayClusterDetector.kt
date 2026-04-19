package com.wenhao.record.tracking.analysis

import com.wenhao.record.map.GeoMath

class StayClusterDetector(
    private val minStayDurationMillis: Long = 5 * 60_000L,
    private val minStayPointCount: Int = 3,
) {
    fun detect(
        points: List<AnalyzedPoint>,
        segments: List<SegmentCandidate>,
    ): List<StayClusterCandidate> {
        return segments
            .asSequence()
            .filter { it.kind == SegmentKind.STATIC }
            .filter { it.durationMillis >= minStayDurationMillis }
            .filter { it.pointCount >= minStayPointCount }
            .mapNotNull { segment ->
                val segmentPoints = points.filter { point ->
                    point.timestampMillis in segment.startTimestamp..segment.endTimestamp
                }
                if (segmentPoints.size < minStayPointCount) return@mapNotNull null

                val centerLat = segmentPoints.map { it.latitude }.average()
                val centerLng = segmentPoints.map { it.longitude }.average()
                val radiusMeters = segmentPoints.maxOfOrNull { point ->
                    GeoMath.distanceMeters(centerLat, centerLng, point.latitude, point.longitude)
                } ?: 0f
                val confidence = computeConfidence(
                    durationMillis = segment.durationMillis,
                    pointCount = segmentPoints.size,
                    radiusMeters = radiusMeters,
                )

                StayClusterCandidate(
                    centerLat = centerLat,
                    centerLng = centerLng,
                    radiusMeters = radiusMeters,
                    arrivalTime = segment.startTimestamp,
                    departureTime = segment.endTimestamp,
                    confidence = confidence,
                )
            }
            .toList()
    }

    private fun computeConfidence(
        durationMillis: Long,
        pointCount: Int,
        radiusMeters: Float,
    ): Float {
        val durationScore = (durationMillis / (10f * 60_000f)).coerceIn(0f, 1f)
        val pointScore = (pointCount / 5f).coerceIn(0f, 1f)
        val radiusScore = when {
            radiusMeters <= 20f -> 1f
            radiusMeters <= 35f -> 0.8f
            radiusMeters <= 50f -> 0.6f
            else -> 0.35f
        }
        return ((durationScore * 0.45f) + (pointScore * 0.2f) + (radiusScore * 0.35f))
            .coerceIn(0f, 1f)
    }
}
