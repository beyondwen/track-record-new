package com.wenhao.record.tracking.analysis

import com.wenhao.record.map.GeoMath

class PointSequenceCleaner(
    private val maxAcceptedAccuracyMeters: Float = 100f,
    private val maxReasonableSpeedMetersPerSecond: Float = 55f,
    private val shortJumpDistanceMeters: Float = 180f,
    private val shortJumpReturnRadiusMeters: Float = 45f,
    private val shortJumpWindowMillis: Long = 3 * 60_000L,
    private val poorAccuracyForJumpMeters: Float = 40f,
) {
    fun clean(points: List<AnalyzedPoint>): List<AnalyzedPoint> {
        if (points.isEmpty()) return emptyList()

        val baseline = removeObviousOutliers(points)
        if (baseline.size < 3) return baseline

        val withoutShortJumps = baseline.filterIndexed { index, point ->
            !isShortJump(
                previous = baseline.getOrNull(index - 1),
                current = point,
                next = baseline.getOrNull(index + 1),
            )
        }

        return withoutShortJumps
            .distinctBy { Triple(it.timestampMillis, it.latitude, it.longitude) }
    }

    private fun removeObviousOutliers(points: List<AnalyzedPoint>): List<AnalyzedPoint> {
        val accepted = mutableListOf<AnalyzedPoint>()
        for (point in points) {
            val lastAccepted = accepted.lastOrNull()
            when {
                lastAccepted != null && point.timestampMillis < lastAccepted.timestampMillis -> Unit
                isDuplicateOf(lastAccepted, point) -> Unit
                point.accuracyMeters != null && point.accuracyMeters > maxAcceptedAccuracyMeters -> Unit
                isImpossibleSpeedPoint(lastAccepted, point) -> Unit
                else -> accepted += point
            }
        }
        return accepted
    }

    private fun isDuplicateOf(previous: AnalyzedPoint?, current: AnalyzedPoint): Boolean {
        return previous != null &&
            previous.timestampMillis == current.timestampMillis &&
            previous.latitude == current.latitude &&
            previous.longitude == current.longitude
    }

    private fun isImpossibleSpeedPoint(previous: AnalyzedPoint?, current: AnalyzedPoint): Boolean {
        if (previous == null) return false

        val durationMillis = current.timestampMillis - previous.timestampMillis
        if (durationMillis <= 0L) return true

        val distanceMeters = GeoMath.distanceMeters(
            previous.latitude,
            previous.longitude,
            current.latitude,
            current.longitude,
        )
        val inferredSpeed = distanceMeters / (durationMillis / 1_000f)
        return inferredSpeed > maxReasonableSpeedMetersPerSecond
    }

    private fun isShortJump(
        previous: AnalyzedPoint?,
        current: AnalyzedPoint,
        next: AnalyzedPoint?,
    ): Boolean {
        if (previous == null || next == null) return false

        val prevToCurrent = GeoMath.distanceMeters(
            previous.latitude,
            previous.longitude,
            current.latitude,
            current.longitude,
        )
        val currentToNext = GeoMath.distanceMeters(
            current.latitude,
            current.longitude,
            next.latitude,
            next.longitude,
        )
        val prevToNext = GeoMath.distanceMeters(
            previous.latitude,
            previous.longitude,
            next.latitude,
            next.longitude,
        )
        val durationMillis = next.timestampMillis - previous.timestampMillis
        val currentAccuracy = current.accuracyMeters ?: 0f
        val neighborhoodAccuracy = maxOf(
            previous.accuracyMeters ?: 0f,
            next.accuracyMeters ?: 0f,
        )

        return prevToCurrent >= shortJumpDistanceMeters &&
            currentToNext >= shortJumpDistanceMeters &&
            prevToNext <= shortJumpReturnRadiusMeters &&
            durationMillis in 1..shortJumpWindowMillis &&
            currentAccuracy >= maxOf(poorAccuracyForJumpMeters, neighborhoodAccuracy)
    }
}
