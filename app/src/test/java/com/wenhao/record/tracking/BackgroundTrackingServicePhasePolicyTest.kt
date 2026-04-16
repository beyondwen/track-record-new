package com.wenhao.record.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTrackingServicePhasePolicyTest {

    @Test
    fun `schedules finalization only while suspect stopping`() {
        assertEquals(
            BackgroundTrackingService.Companion.SessionFinalizationAction.CANCEL,
            BackgroundTrackingService.sessionFinalizationActionForPhase(TrackingPhase.IDLE),
        )
        assertEquals(
            BackgroundTrackingService.Companion.SessionFinalizationAction.CANCEL,
            BackgroundTrackingService.sessionFinalizationActionForPhase(TrackingPhase.SUSPECT_MOVING),
        )
        assertEquals(
            BackgroundTrackingService.Companion.SessionFinalizationAction.CANCEL,
            BackgroundTrackingService.sessionFinalizationActionForPhase(TrackingPhase.ACTIVE),
        )
        assertEquals(
            BackgroundTrackingService.Companion.SessionFinalizationAction.SCHEDULE,
            BackgroundTrackingService.sessionFinalizationActionForPhase(TrackingPhase.SUSPECT_STOPPING),
        )
    }
}
