package com.wenhao.record.tracking.decision

import com.wenhao.record.tracking.pipeline.FeatureVector

class DecisionRuntimeCoordinator(
    private val engine: TrackingDecisionEngine,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    private val onFrame: (DecisionFrame) -> Unit,
) {
    fun onVector(vector: FeatureVector, nowMillis: Long) {
        val frame = engine.evaluate(vector, nowMillis)
        onFrame(frame)
        when (frame.finalDecision) {
            FinalDecision.START -> onStart()
            FinalDecision.STOP -> onStop()
            FinalDecision.HOLD -> Unit
        }
    }
}
