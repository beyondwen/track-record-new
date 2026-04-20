package com.wenhao.record.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTrackingServicePhasePolicyTest {

    @Test
    fun `promotes to active only when motion and displacement signals both pass`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.ACTIVE,
            policy.nextPhase(
                current = TrackingPhase.IDLE,
                motionType = "WALKING",
                motionConfidence = 0.75f,
                netDistanceMeters = 55f,
                inferredSpeedMetersPerSecond = 1.2f,
                stillDurationMillis = 0L,
            ),
        )
    }

    @Test
    fun `enters suspect when only one side of the dual signal is present`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.SUSPECT_MOVING,
            policy.nextPhase(
                current = TrackingPhase.IDLE,
                motionType = "WALKING",
                motionConfidence = 0.76f,
                netDistanceMeters = 20f,
                inferredSpeedMetersPerSecond = 0.4f,
                stillDurationMillis = 0L,
            ),
        )
        assertEquals(
            TrackingPhase.SUSPECT_MOVING,
            policy.nextPhase(
                current = TrackingPhase.IDLE,
                motionType = "STILL",
                motionConfidence = 0.9f,
                netDistanceMeters = 80f,
                inferredSpeedMetersPerSecond = 1.2f,
                stillDurationMillis = 0L,
            ),
        )
    }

    @Test
    fun `downshifts active only after sustained still evidence`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.SUSPECT_STOPPING,
            policy.nextPhase(
                current = TrackingPhase.ACTIVE,
                motionType = "STILL",
                motionConfidence = 0.92f,
                netDistanceMeters = 18f,
                inferredSpeedMetersPerSecond = 0.12f,
                stillDurationMillis = 6 * 60_000L,
            ),
        )
        assertEquals(
            TrackingPhase.ACTIVE,
            policy.nextPhase(
                current = TrackingPhase.ACTIVE,
                motionType = "STILL",
                motionConfidence = 0.92f,
                netDistanceMeters = 18f,
                inferredSpeedMetersPerSecond = 0.12f,
                stillDurationMillis = 2 * 60_000L,
            ),
        )
        assertEquals(
            TrackingPhase.IDLE,
            policy.nextPhase(
                current = TrackingPhase.SUSPECT_STOPPING,
                motionType = "STILL",
                motionConfidence = 0.92f,
                netDistanceMeters = 12f,
                inferredSpeedMetersPerSecond = 0.08f,
                stillDurationMillis = 10 * 60_000L,
            ),
        )
    }
}
