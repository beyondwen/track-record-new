package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.GeoMath

data class TrackFixEvaluation(
    val consecutiveGoodFixes: Int,
    val isReadyToRecord: Boolean,
    val acceptedAsGoodFix: Boolean,
)

class TrackFixQualityGate(
    private val requiredConsecutiveGoodFixes: Int,
    private val maxAcceptedAccuracyMeters: Float,
    private val maxFixAgeMillis: Long,
    private val minMeaningfulDistanceMeters: Float,
) {
    private var consecutiveGoodFixes = 0

    fun reset() {
        consecutiveGoodFixes = 0
    }

    fun noteFix(
        point: TrackPoint,
        nowMillis: Long,
        previousCandidate: TrackPoint?,
    ): TrackFixEvaluation {
        val isFresh = point.timestampMillis > 0L && nowMillis - point.timestampMillis <= maxFixAgeMillis
        val isAccurate = (point.accuracyMeters ?: Float.MAX_VALUE) <= maxAcceptedAccuracyMeters
        val hasMeaningfulMove = previousCandidate == null ||
            GeoMath.distanceMeters(previousCandidate, point) >= minMeaningfulDistanceMeters

        val accepted = isFresh && isAccurate && hasMeaningfulMove
        consecutiveGoodFixes = if (accepted) consecutiveGoodFixes + 1 else 0

        return TrackFixEvaluation(
            consecutiveGoodFixes = consecutiveGoodFixes,
            isReadyToRecord = consecutiveGoodFixes >= requiredConsecutiveGoodFixes,
            acceptedAsGoodFix = accepted,
        )
    }
}
