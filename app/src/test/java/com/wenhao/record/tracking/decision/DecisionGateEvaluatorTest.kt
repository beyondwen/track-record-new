package com.wenhao.record.tracking.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionGateEvaluatorTest {

    @Test
    fun `start gate fails when gps is missing`() {
        val result = DecisionGateEvaluator.evaluate(
            DecisionGateInput(
                gpsSampleCount30s = 0.0,
                gpsAccuracyAvg30s = 0.0,
                motionEvidence30s = true,
                insideFrequentPlace = false,
                isRecording = false,
                startScore = 0.92,
                stopScore = 0.0,
                recordingDurationSeconds = 0.0,
                stopObservationPassed = false,
            )
        )

        assertFalse(result.startEligible)
        assertEquals(DecisionGateBlockReason.GPS_MISSING, result.startBlockedReason)
    }

    @Test
    fun `start gate fails when motion evidence is missing`() {
        val result = DecisionGateEvaluator.evaluate(
            DecisionGateInput(
                gpsSampleCount30s = 3.0,
                gpsAccuracyAvg30s = 12.0,
                motionEvidence30s = false,
                insideFrequentPlace = false,
                isRecording = false,
                startScore = 0.92,
                stopScore = 0.0,
                recordingDurationSeconds = 0.0,
                stopObservationPassed = false,
            )
        )

        assertFalse(result.startEligible)
        assertEquals(DecisionGateBlockReason.MOTION_MISSING, result.startBlockedReason)
    }

    @Test
    fun `start gate fails when inside frequent place`() {
        val result = DecisionGateEvaluator.evaluate(
            DecisionGateInput(
                gpsSampleCount30s = 3.0,
                gpsAccuracyAvg30s = 12.0,
                motionEvidence30s = true,
                insideFrequentPlace = true,
                isRecording = false,
                startScore = 0.92,
                stopScore = 0.0,
                recordingDurationSeconds = 0.0,
                stopObservationPassed = false,
            )
        )

        assertFalse(result.startEligible)
        assertEquals(DecisionGateBlockReason.INSIDE_FREQUENT_PLACE, result.startBlockedReason)
    }

    @Test
    fun `stop remains eligible but feedback is blocked when gps is poor`() {
        val result = DecisionGateEvaluator.evaluate(
            DecisionGateInput(
                gpsSampleCount30s = 3.0,
                gpsAccuracyAvg30s = 60.0,
                motionEvidence30s = true,
                insideFrequentPlace = true,
                isRecording = true,
                startScore = 0.1,
                stopScore = 0.95,
                recordingDurationSeconds = 240.0,
                stopObservationPassed = true,
            )
        )

        assertTrue(result.stopEligible)
        assertFalse(result.stopFeedbackEligible)
        assertEquals(DecisionGateBlockReason.FEEDBACK_BLOCKED_LOW_QUALITY, result.feedbackBlockedReason)
    }
}
