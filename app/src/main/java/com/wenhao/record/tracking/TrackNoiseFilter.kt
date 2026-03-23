package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.GeoMath
import kotlin.math.max
import kotlin.math.min

internal enum class TrackNoiseAction {
    ACCEPT,
    MERGE_STILL,
    DROP_DRIFT,
    DROP_JUMP
}

internal data class TrackNoiseSample(
    val point: TrackPoint,
    val speedMetersPerSecond: Float,
    val locationAgeMs: Long
)

internal data class TrackNoiseResult(
    val action: TrackNoiseAction,
    val distanceMeters: Float = 0f,
    val acceptedPoint: TrackPoint? = null
)

internal object TrackNoiseFilter {
    private const val MIN_ACCEPTED_POINT_DISTANCE_METERS = 5f
    private const val MAX_STATIONARY_JITTER_METERS = 9f
    private const val MAX_POOR_ACCURACY_METERS = 45f
    private const val MAX_POOR_ACCURACY_INITIAL_METERS = 70f
    private const val MAX_EXTREME_ACCURACY_METERS = 120f
    private const val LOW_SPEED_METERS_PER_SECOND = 1.5f
    private const val MAX_JUMP_DISTANCE_METERS = 180f
    private const val MAX_JUMP_SPEED_METERS_PER_SECOND = 55f
    private const val MIN_JUMP_TIME_DELTA_MS = 2_000L
    private const val MAX_JUMP_TIME_DELTA_MS = 20_000L
    private const val MAX_ACTIVE_LOCATION_AGE_MS = 20_000L

    fun evaluate(lastPoint: TrackPoint?, sample: TrackNoiseSample): TrackNoiseResult {
        val candidatePoint = sample.point
        val candidateAccuracy = candidatePoint.accuracyMeters ?: Float.MAX_VALUE
        val candidateSpeed = sample.speedMetersPerSecond

        if (lastPoint == null) {
            if (sample.locationAgeMs > MAX_ACTIVE_LOCATION_AGE_MS) {
                return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
            }
            if (candidateAccuracy >= MAX_EXTREME_ACCURACY_METERS &&
                candidateSpeed <= LOW_SPEED_METERS_PER_SECOND + 1.0f
            ) {
                return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
            }
            return if (candidateAccuracy > MAX_POOR_ACCURACY_INITIAL_METERS &&
                candidateSpeed <= LOW_SPEED_METERS_PER_SECOND
            ) {
                TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
            } else {
                TrackNoiseResult(TrackNoiseAction.ACCEPT, acceptedPoint = candidatePoint)
            }
        }

        if (sample.locationAgeMs > MAX_ACTIVE_LOCATION_AGE_MS ||
            candidatePoint.timestampMillis <= lastPoint.timestampMillis
        ) {
            return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
        }

        if (candidateAccuracy >= MAX_EXTREME_ACCURACY_METERS &&
            candidateSpeed <= LOW_SPEED_METERS_PER_SECOND + 1.5f
        ) {
            return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
        }

        val distanceMeters = GeoMath.distanceMeters(lastPoint, candidatePoint)
        val jitterThreshold = max(
            MIN_ACCEPTED_POINT_DISTANCE_METERS,
            min(MAX_STATIONARY_JITTER_METERS, candidateAccuracy * 0.28f)
        )
        if (distanceMeters <= jitterThreshold && candidateSpeed <= LOW_SPEED_METERS_PER_SECOND) {
            return TrackNoiseResult(TrackNoiseAction.MERGE_STILL, distanceMeters = distanceMeters)
        }

        val driftDistanceThreshold = max(20f, candidateAccuracy * 0.7f)
        if (candidateAccuracy >= MAX_POOR_ACCURACY_METERS &&
            candidateSpeed <= LOW_SPEED_METERS_PER_SECOND + 0.7f &&
            distanceMeters <= driftDistanceThreshold
        ) {
            return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT, distanceMeters = distanceMeters)
        }

        val timeDeltaMillis = candidatePoint.timestampMillis - lastPoint.timestampMillis
        if (timeDeltaMillis in MIN_JUMP_TIME_DELTA_MS..MAX_JUMP_TIME_DELTA_MS) {
            val inferredSpeed = distanceMeters / (timeDeltaMillis / 1000f)
            val reportedSpeedAllowance = max(18f, candidateSpeed * 3.2f + 12f)
            if (distanceMeters >= max(MAX_JUMP_DISTANCE_METERS, candidateAccuracy * 2.5f) &&
                inferredSpeed >= MAX_JUMP_SPEED_METERS_PER_SECOND &&
                inferredSpeed > reportedSpeedAllowance
            ) {
                return TrackNoiseResult(TrackNoiseAction.DROP_JUMP, distanceMeters = distanceMeters)
            }
        }

        if (distanceMeters < MIN_ACCEPTED_POINT_DISTANCE_METERS) {
            return TrackNoiseResult(TrackNoiseAction.MERGE_STILL, distanceMeters = distanceMeters)
        }

        return TrackNoiseResult(
            action = TrackNoiseAction.ACCEPT,
            distanceMeters = distanceMeters,
            acceptedPoint = candidatePoint
        )
    }
}
