package com.wenhao.record.tracking.decision

import org.junit.Assert.assertEquals
import org.junit.Test

class DecisionSmootherTest {

    @Test
    fun `start requires consecutive high scores`() {
        val smoother = DecisionSmoother(
            startTriggerCount = 2,
            stopTriggerCount = 4,
            startThreshold = 0.8,
            stopThreshold = 0.9,
            startProtectionMillis = 180_000L,
            minimumRecordingMillis = 120_000L,
        )

        assertEquals(
            FinalDecision.HOLD,
            smoother.consume(
                startScore = 0.82,
                stopScore = 0.10,
                nowMillis = 10_000L,
                isRecording = false,
            ),
        )
        assertEquals(
            FinalDecision.START,
            smoother.consume(
                startScore = 0.84,
                stopScore = 0.12,
                nowMillis = 25_000L,
                isRecording = false,
            ),
        )
    }
}
