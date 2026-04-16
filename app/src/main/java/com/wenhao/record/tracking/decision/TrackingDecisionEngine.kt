package com.wenhao.record.tracking.decision

import com.wenhao.record.tracking.model.StartDecisionModel
import com.wenhao.record.tracking.model.StopDecisionModel
import com.wenhao.record.tracking.pipeline.FeatureVector

data class DecisionFrame(
    val vector: FeatureVector,
    val startScore: Double,
    val stopScore: Double,
    val finalDecision: FinalDecision,
    val gateResult: DecisionGateResult = DecisionGateResult.allowAll(),
)

class TrackingDecisionEngine(
    private val startModel: StartDecisionModel,
    private val stopModel: StopDecisionModel,
    private val smoother: DecisionSmoother,
) {
    fun evaluate(
        vector: FeatureVector,
        nowMillis: Long,
    ): DecisionFrame {
        val startScore = startModel.score(vector)
        val stopScore = stopModel.score(vector)
        val gateResult = DecisionGateEvaluator.evaluate(
            vector.gateInput.copy(
                isRecording = vector.isRecording,
                startScore = startScore,
                stopScore = stopScore,
            )
        )
        val rawDecision = smoother.consume(
            startScore = startScore,
            stopScore = stopScore,
            nowMillis = nowMillis,
            isRecording = vector.isRecording,
        )
        val finalDecision = when (rawDecision) {
            FinalDecision.START -> if (gateResult.startEligible) FinalDecision.START else FinalDecision.HOLD
            FinalDecision.STOP -> if (gateResult.stopEligible) FinalDecision.STOP else FinalDecision.HOLD
            FinalDecision.HOLD -> FinalDecision.HOLD
        }
        return DecisionFrame(
            vector = vector,
            startScore = startScore,
            stopScore = stopScore,
            finalDecision = finalDecision,
            gateResult = gateResult,
        )
    }
}
