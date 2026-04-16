package com.wenhao.record.tracking.decision

import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.tracking.model.LinearModelConfig
import com.wenhao.record.tracking.model.StartDecisionModel
import com.wenhao.record.tracking.model.StopDecisionModel
import com.wenhao.record.tracking.pipeline.FeatureVector
import org.junit.Assert.assertEquals
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
}
