package com.wenhao.record.tracking.decision

import com.wenhao.record.tracking.model.StartDecisionModel
import com.wenhao.record.tracking.model.StopDecisionModel
import com.wenhao.record.tracking.pipeline.FeatureVector

data class DecisionFrame(
    val vector: FeatureVector,
    val startScore: Double,
    val stopScore: Double,
    val finalDecision: FinalDecision,
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
        val finalDecision = smoother.consume(
            startScore = startScore,
            stopScore = stopScore,
            nowMillis = nowMillis,
            isRecording = vector.isRecording,
        )
        return DecisionFrame(
            vector = vector,
            startScore = startScore,
            stopScore = stopScore,
            finalDecision = finalDecision,
        )
    }
}
