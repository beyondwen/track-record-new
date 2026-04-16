package com.wenhao.record.tracking.decision

import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.tracking.model.LinearModelConfig
import com.wenhao.record.tracking.model.StartDecisionModel
import com.wenhao.record.tracking.model.StopDecisionModel
import com.wenhao.record.tracking.pipeline.FeatureVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionRuntimeCoordinatorTest {

    @Test
    fun `emits start action when engine returns start`() {
        var started = false
        var stopped = false
        var captured: DecisionFrame? = null
        val config = LinearModelConfig(
            bias = 3.0,
            featureOrder = listOf("speed_avg_30s"),
            weights = listOf(0.0),
            means = listOf(0.0),
            scales = listOf(1.0),
        )
        val coordinator = DecisionRuntimeCoordinator(
            engine = TrackingDecisionEngine(
                startModel = StartDecisionModel(config),
                stopModel = StopDecisionModel(config),
                smoother = DecisionSmoother(
                    startTriggerCount = 1,
                    stopTriggerCount = 4,
                    startThreshold = 0.5,
                    stopThreshold = 0.95,
                    startProtectionMillis = 180_000L,
                    minimumRecordingMillis = 120_000L,
                ),
            ),
            onStart = { started = true },
            onStop = { stopped = true },
            onFrame = { frame -> captured = frame },
        )

        coordinator.onVector(
            FeatureVector(
                timestampMillis = 30_000L,
                features = mapOf("speed_avg_30s" to 1.2),
                isRecording = false,
                phase = TrackingPhase.SUSPECT_MOVING,
                gateInput = DecisionGateInput(
                    gpsSampleCount30s = 3.0,
                    gpsAccuracyAvg30s = 12.0,
                    motionEvidence30s = true,
                    insideFrequentPlace = false,
                    isRecording = false,
                    startScore = 0.0,
                    stopScore = 0.0,
                    recordingDurationSeconds = 0.0,
                    stopObservationPassed = false,
                ),
            ),
            nowMillis = 30_000L,
        )

        assertTrue(started)
        assertFalse(stopped)
        assertEquals(FinalDecision.START, captured!!.finalDecision)
    }

    @Test
    fun `does not emit start action when gate downgrades to hold`() {
        var started = false
        var stopped = false
        var captured: DecisionFrame? = null
        val config = LinearModelConfig(
            bias = 3.0,
            featureOrder = listOf("speed_avg_30s"),
            weights = listOf(0.0),
            means = listOf(0.0),
            scales = listOf(1.0),
        )
        val coordinator = DecisionRuntimeCoordinator(
            engine = TrackingDecisionEngine(
                startModel = StartDecisionModel(config),
                stopModel = StopDecisionModel(config),
                smoother = DecisionSmoother(
                    startTriggerCount = 1,
                    stopTriggerCount = 4,
                    startThreshold = 0.5,
                    stopThreshold = 0.95,
                    startProtectionMillis = 180_000L,
                    minimumRecordingMillis = 120_000L,
                ),
            ),
            onStart = { started = true },
            onStop = { stopped = true },
            onFrame = { frame -> captured = frame },
        )

        coordinator.onVector(
            FeatureVector(
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
            ),
            nowMillis = 30_000L,
        )

        assertFalse(started)
        assertFalse(stopped)
        assertEquals(FinalDecision.HOLD, captured!!.finalDecision)
    }
}
