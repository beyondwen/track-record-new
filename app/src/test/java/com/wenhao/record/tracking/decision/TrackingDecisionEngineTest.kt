package com.wenhao.record.tracking.decision

import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.tracking.model.LinearModelConfig
import com.wenhao.record.tracking.model.StartDecisionModel
import com.wenhao.record.tracking.model.StopDecisionModel
import com.wenhao.record.tracking.pipeline.FeatureVector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingDecisionEngineTest {

    @Test
    fun `engine returns hold before smoother threshold is satisfied`() {
        val config = LinearModelConfig(
            bias = -2.0,
            featureOrder = listOf("speed_avg_30s", "steps_30s"),
            weights = listOf(0.3, 0.05),
            means = listOf(0.0, 0.0),
            scales = listOf(1.0, 1.0),
        )
        val vector = FeatureVector(
            timestampMillis = 15_000L,
            features = mapOf(
                "speed_avg_30s" to 1.8,
                "steps_30s" to 12.0,
            ),
            isRecording = false,
            phase = TrackingPhase.SUSPECT_MOVING,
        )
        val engine = TrackingDecisionEngine(
            startModel = StartDecisionModel(config),
            stopModel = StopDecisionModel(config),
            smoother = DecisionSmoother(
                startTriggerCount = 2,
                stopTriggerCount = 4,
                startThreshold = 0.8,
                stopThreshold = 0.9,
                startProtectionMillis = 180_000L,
                minimumRecordingMillis = 120_000L,
            ),
        )

        val frame = engine.evaluate(vector, nowMillis = 15_000L)

        assertEquals(FinalDecision.HOLD, frame.finalDecision)
    }

    @Test
    fun `engine blocks start when gate rejects event`() {
        val config = LinearModelConfig(
            bias = 3.0,
            featureOrder = listOf("speed_avg_30s"),
            weights = listOf(0.0),
            means = listOf(0.0),
            scales = listOf(1.0),
        )
        val engine = TrackingDecisionEngine(
            startModel = StartDecisionModel(config),
            stopModel = StopDecisionModel(config),
            smoother = DecisionSmoother(
                startTriggerCount = 1,
                stopTriggerCount = 4,
                startThreshold = 0.5,
                stopThreshold = 0.9,
                startProtectionMillis = 180_000L,
                minimumRecordingMillis = 120_000L,
            ),
        )
        val vector = FeatureVector(
            timestampMillis = 30_000L,
            features = mapOf("speed_avg_30s" to 1.2),
            isRecording = false,
            phase = TrackingPhase.SUSPECT_MOVING,
            gateInput = DecisionGateInput(
                gpsSampleCount30s = 0.0,
                gpsAccuracyAvg30s = 0.0,
                motionEvidence30s = false,
                insideFrequentPlace = false,
                isRecording = false,
                startScore = 0.0,
                stopScore = 0.0,
                recordingDurationSeconds = 0.0,
                stopObservationPassed = false,
            ),
        )

        val frame = engine.evaluate(vector, nowMillis = 30_000L)

        assertEquals(FinalDecision.HOLD, frame.finalDecision)
        assertFalse(frame.gateResult.startEligible)
        assertEquals(DecisionGateBlockReason.GPS_MISSING, frame.gateResult.startBlockedReason)
    }

    @Test
    fun `engine allows stop but blocks stop feedback when gps is poor`() {
        val startConfig = LinearModelConfig(
            bias = -3.0,
            featureOrder = listOf("speed_avg_30s"),
            weights = listOf(0.0),
            means = listOf(0.0),
            scales = listOf(1.0),
        )
        val stopConfig = LinearModelConfig(
            bias = 3.0,
            featureOrder = listOf("speed_avg_30s"),
            weights = listOf(0.0),
            means = listOf(0.0),
            scales = listOf(1.0),
        )
        val engine = TrackingDecisionEngine(
            startModel = StartDecisionModel(startConfig),
            stopModel = StopDecisionModel(stopConfig),
            smoother = DecisionSmoother(
                startTriggerCount = 2,
                stopTriggerCount = 1,
                startThreshold = 0.9,
                stopThreshold = 0.9,
                startProtectionMillis = 0L,
                minimumRecordingMillis = 0L,
            ),
        )
        val vector = FeatureVector(
            timestampMillis = 240_000L,
            features = mapOf("speed_avg_30s" to 0.1),
            isRecording = true,
            phase = TrackingPhase.SUSPECT_STOPPING,
            gateInput = DecisionGateInput(
                gpsSampleCount30s = 3.0,
                gpsAccuracyAvg30s = 60.0,
                motionEvidence30s = true,
                insideFrequentPlace = true,
                isRecording = true,
                startScore = 0.0,
                stopScore = 0.0,
                recordingDurationSeconds = 240.0,
                stopObservationPassed = true,
            ),
        )

        val frame = engine.evaluate(vector, nowMillis = 240_000L)

        assertEquals(FinalDecision.STOP, frame.finalDecision)
        assertTrue(frame.gateResult.stopEligible)
        assertFalse(frame.gateResult.stopFeedbackEligible)
    }
}
