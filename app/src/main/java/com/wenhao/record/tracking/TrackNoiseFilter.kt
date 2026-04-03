package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.GeoMath
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
    private const val MAX_STATIONARY_JITTER_METERS = 25f
    private const val MAX_POOR_ACCURACY_METERS = 35f
    private const val MAX_POOR_ACCURACY_INITIAL_METERS = 35f
    private const val MAX_EXTREME_ACCURACY_METERS = 120f
    private const val MAX_ACCEPTED_SPEED_METERS_PER_SECOND = 45f
    private const val LOW_SPEED_METERS_PER_SECOND = 2.0f
    private const val MAX_JUMP_DISTANCE_METERS = 180f
    private const val MAX_JUMP_SPEED_METERS_PER_SECOND = 55f
    private const val MIN_JUMP_TIME_DELTA_MS = 2_000L
    private const val MAX_JUMP_TIME_DELTA_MS = 20_000L
    private const val MAX_ACTIVE_LOCATION_AGE_MS = 20_000L
    private const val MAX_SHARP_TURN_SPEED_METERS_PER_SECOND = 12.0f
    private const val MAX_SHARP_TURN_WINDOW_MS = 30_000L
    private const val MIN_SHARP_TURN_EDGE_METERS = 5f
    private const val MAX_SHARP_TURN_EDGE_METERS = 150f
    private const val MAX_SHARP_TURN_BRIDGE_METERS = 80f
    private const val MAX_SHARP_TURN_ANGLE_DEGREES = 85.0

    private fun TrackPoint.hasValidCoordinate(): Boolean {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        return true
    }

    fun evaluate(
        previousPoint: TrackPoint?,
        lastPoint: TrackPoint?,
        sample: TrackNoiseSample,
    ): TrackNoiseResult {
        val candidatePoint = sample.point

        // Validate candidate point coordinates
        if (!candidatePoint.hasValidCoordinate()) {
            return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
        }

        val candidateAccuracy = candidatePoint.accuracyMeters ?: Float.MAX_VALUE
        val candidateSpeed = sample.speedMetersPerSecond
        if (candidateSpeed > MAX_ACCEPTED_SPEED_METERS_PER_SECOND) {
            return TrackNoiseResult(TrackNoiseAction.DROP_JUMP)
        }
        if (candidateAccuracy > MAX_POOR_ACCURACY_METERS) {
            return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
        }

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
        if (previousPoint != null &&
            shouldDropSharpTurnDrift(
                previousPoint = previousPoint,
                lastPoint = lastPoint,
                candidatePoint = candidatePoint,
                candidateSpeed = candidateSpeed,
            )
        ) {
            return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT, distanceMeters = distanceMeters)
        }
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

    private fun shouldDropSharpTurnDrift(
        previousPoint: TrackPoint,
        lastPoint: TrackPoint,
        candidatePoint: TrackPoint,
        candidateSpeed: Float,
    ): Boolean {
        if (candidateSpeed > MAX_SHARP_TURN_SPEED_METERS_PER_SECOND) return false

        val previousEdgeMeters = GeoMath.distanceMeters(previousPoint, lastPoint)
        val candidateEdgeMeters = GeoMath.distanceMeters(lastPoint, candidatePoint)
        if (previousEdgeMeters !in MIN_SHARP_TURN_EDGE_METERS..MAX_SHARP_TURN_EDGE_METERS ||
            candidateEdgeMeters !in MIN_SHARP_TURN_EDGE_METERS..MAX_SHARP_TURN_EDGE_METERS
        ) {
            return false
        }

        val firstDeltaMillis = lastPoint.timestampMillis - previousPoint.timestampMillis
        val secondDeltaMillis = candidatePoint.timestampMillis - lastPoint.timestampMillis
        if (firstDeltaMillis <= 0L ||
            secondDeltaMillis <= 0L ||
            firstDeltaMillis > MAX_SHARP_TURN_WINDOW_MS ||
            secondDeltaMillis > MAX_SHARP_TURN_WINDOW_MS
        ) {
            return false
        }

        val bridgeMeters = GeoMath.distanceMeters(previousPoint, candidatePoint)
        if (bridgeMeters > MAX_SHARP_TURN_BRIDGE_METERS) return false

        val turnAngle = interiorAngleDegrees(previousPoint, lastPoint, candidatePoint)
        return turnAngle <= MAX_SHARP_TURN_ANGLE_DEGREES
    }

    private fun interiorAngleDegrees(
        previousPoint: TrackPoint,
        middlePoint: TrackPoint,
        nextPoint: TrackPoint,
    ): Double {
        val before = planarVectorMeters(middlePoint, previousPoint)
        val after = planarVectorMeters(middlePoint, nextPoint)
        val beforeLength = sqrt(before.first * before.first + before.second * before.second)
        val afterLength = sqrt(after.first * after.first + after.second * after.second)
        if (beforeLength <= 0.0 || afterLength <= 0.0) return 180.0

        val cosineValue = ((before.first * after.first) + (before.second * after.second)) /
            (beforeLength * afterLength)
        return Math.toDegrees(acos(cosineValue.coerceIn(-1.0, 1.0)))
    }

    private fun planarVectorMeters(origin: TrackPoint, target: TrackPoint): Pair<Double, Double> {
        val originLatitude = Math.toRadians(origin.getLatitudeForDistance())
        val originLongitude = Math.toRadians(origin.getLongitudeForDistance())
        val targetLatitude = Math.toRadians(target.getLatitudeForDistance())
        val targetLongitude = Math.toRadians(target.getLongitudeForDistance())
        val latitudeDelta = targetLatitude - originLatitude
        val longitudeDelta = targetLongitude - originLongitude
        val meanLatitude = (originLatitude + targetLatitude) / 2.0
        val metersPerRadian = 6_371_000.0
        val xMeters = longitudeDelta * cos(meanLatitude) * metersPerRadian
        val yMeters = latitudeDelta * metersPerRadian
        return xMeters to yMeters
    }
}
