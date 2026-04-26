package com.wenhao.record.tracking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionConfidenceEngineTest {

    @Test
    fun `recent acceleration and step growth make movement likely`() {
        val engine = MotionConfidenceEngine()
        val now = 1_713_420_000_000L

        engine.noteAccelerationVariance(1.1f, now - 1_000L)
        engine.noteStepCount(10f, now - 3_000L)
        engine.noteStepCount(15f, now - 500L)

        val snapshot = engine.evaluate(
            nowMillis = now,
            signals = MotionSignals(
                stepDelta = engine.currentStepDelta(),
                effectiveDistanceMeters = 24f,
                reportedSpeedMetersPerSecond = 0.7f,
                inferredSpeedMetersPerSecond = 1.0f,
                insideAnchor = false,
                sameAnchorWifi = false,
                poorAccuracy = false,
            ),
        )

        assertTrue(snapshot.movingLikely)
        assertFalse(snapshot.summary.isBlank())
    }
}
