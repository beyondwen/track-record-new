package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.SamplingTier

class BackgroundTrackingServicePhasePolicy(
    private val promoteMotionConfidenceThreshold: Float = 0.8f,
    private val promoteDistanceThresholdMeters: Float = 90f,
    private val promoteSpeedThresholdMetersPerSecond: Float = 1.5f,
    private val suspectDistanceThresholdMeters: Float = 45f,
    private val suspectSpeedThresholdMetersPerSecond: Float = 0.8f,
    private val downshiftDistanceThresholdMeters: Float = 25f,
    private val downshiftSpeedThresholdMetersPerSecond: Float = 0.35f,
    private val activeToStoppingStillDurationMillis: Long = 5 * 60_000L,
    private val stoppingToIdleStillDurationMillis: Long = 9 * 60_000L,
) {
    fun nextPhase(
        current: TrackingPhase,
        motionType: String?,
        motionConfidence: Float,
        netDistanceMeters: Float,
        inferredSpeedMetersPerSecond: Float,
        stillDurationMillis: Long,
    ): TrackingPhase {
        val movementSignalStrong = isMovementSignalStrong(
            motionType = motionType,
            motionConfidence = motionConfidence,
        )
        val displacementSignalStrong =
            netDistanceMeters >= promoteDistanceThresholdMeters &&
                inferredSpeedMetersPerSecond >= promoteSpeedThresholdMetersPerSecond
        val displacementSignalWeak =
            netDistanceMeters >= suspectDistanceThresholdMeters ||
                inferredSpeedMetersPerSecond >= suspectSpeedThresholdMetersPerSecond
        val sustainedStill = isStill(
            motionType = motionType,
            motionConfidence = motionConfidence,
            netDistanceMeters = netDistanceMeters,
            inferredSpeedMetersPerSecond = inferredSpeedMetersPerSecond,
        )

        return when (current) {
            TrackingPhase.IDLE -> when {
                movementSignalStrong && displacementSignalStrong -> TrackingPhase.ACTIVE
                movementSignalStrong || displacementSignalWeak -> TrackingPhase.SUSPECT_MOVING
                else -> TrackingPhase.IDLE
            }

            TrackingPhase.SUSPECT_MOVING -> when {
                movementSignalStrong && displacementSignalStrong -> TrackingPhase.ACTIVE
                !movementSignalStrong && !displacementSignalWeak -> TrackingPhase.IDLE
                else -> TrackingPhase.SUSPECT_MOVING
            }

            TrackingPhase.ACTIVE -> when {
                sustainedStill && stillDurationMillis >= activeToStoppingStillDurationMillis ->
                    TrackingPhase.SUSPECT_STOPPING
                else -> TrackingPhase.ACTIVE
            }

            TrackingPhase.SUSPECT_STOPPING -> when {
                movementSignalStrong && displacementSignalWeak -> TrackingPhase.ACTIVE
                sustainedStill && stillDurationMillis >= stoppingToIdleStillDurationMillis ->
                    TrackingPhase.IDLE
                else -> TrackingPhase.SUSPECT_STOPPING
            }
        }
    }

    fun samplingTierFor(phase: TrackingPhase): SamplingTier {
        return when (phase) {
            TrackingPhase.IDLE -> SamplingTier.IDLE
            TrackingPhase.SUSPECT_MOVING,
            TrackingPhase.SUSPECT_STOPPING -> SamplingTier.SUSPECT
            TrackingPhase.ACTIVE -> SamplingTier.ACTIVE
        }
    }

    private fun isMovementSignalStrong(
        motionType: String?,
        motionConfidence: Float,
    ): Boolean {
        return motionType in MOVING_ACTIVITY_TYPES && motionConfidence >= promoteMotionConfidenceThreshold
    }

    private fun isStill(
        motionType: String?,
        motionConfidence: Float,
        netDistanceMeters: Float,
        inferredSpeedMetersPerSecond: Float,
    ): Boolean {
        return motionType == STILL_ACTIVITY &&
            motionConfidence >= promoteMotionConfidenceThreshold &&
            netDistanceMeters <= downshiftDistanceThresholdMeters &&
            inferredSpeedMetersPerSecond <= downshiftSpeedThresholdMetersPerSecond
    }

    private companion object {
        private const val STILL_ACTIVITY = "STILL"
        private val MOVING_ACTIVITY_TYPES = setOf("WALKING", "ON_BICYCLE", "IN_VEHICLE")
    }
}
