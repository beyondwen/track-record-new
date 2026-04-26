package com.wenhao.record.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTrackingServicePhasePolicyTest {

    @Test
    fun `enters suspect moving when low power signals indicate movement`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.SUSPECT_MOVING,
            policy.nextPhase(
                current = TrackingPhase.IDLE,
                lowPowerSignals = TrackingLowPowerSignalsSnapshot(
                    hasFreshLocation = true,
                    hasMeaningfulDisplacement = true,
                    shouldEnterSuspectMoving = true,
                ),
                hasEnoughGoodFixesToRecord = false,
                signalLost = false,
                prolongedStill = false,
            ),
        )
    }

    @Test
    fun `promotes to active only after continuous good fixes are ready`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.ACTIVE,
            policy.nextPhase(
                current = TrackingPhase.SUSPECT_MOVING,
                lowPowerSignals = TrackingLowPowerSignalsSnapshot(true, true, true),
                hasEnoughGoodFixesToRecord = true,
                signalLost = false,
                prolongedStill = false,
            ),
        )
    }

    @Test
    fun `keeps active when signal is temporarily lost but still not proven still`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.ACTIVE,
            policy.nextPhase(
                current = TrackingPhase.ACTIVE,
                lowPowerSignals = TrackingLowPowerSignalsSnapshot(false, false, false),
                hasEnoughGoodFixesToRecord = false,
                signalLost = true,
                prolongedStill = false,
            ),
        )
    }

    @Test
    fun `downshifts only after prolonged still evidence`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.SUSPECT_STOPPING,
            policy.nextPhase(
                current = TrackingPhase.ACTIVE,
                lowPowerSignals = TrackingLowPowerSignalsSnapshot(false, false, false),
                hasEnoughGoodFixesToRecord = false,
                signalLost = false,
                prolongedStill = true,
            ),
        )
    }
}
