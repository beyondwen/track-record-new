package com.wenhao.record.tracking.decision

data class DecisionGateInput(
    val gpsSampleCount30s: Double,
    val gpsAccuracyAvg30s: Double,
    val motionEvidence30s: Boolean,
    val insideFrequentPlace: Boolean,
    val isRecording: Boolean,
    val startScore: Double,
    val stopScore: Double,
    val recordingDurationSeconds: Double,
    val stopObservationPassed: Boolean,
)

data class DecisionGateResult(
    val startEligible: Boolean,
    val stopEligible: Boolean,
    val startFeedbackEligible: Boolean,
    val stopFeedbackEligible: Boolean,
    val startBlockedReason: DecisionGateBlockReason?,
    val stopBlockedReason: DecisionGateBlockReason?,
    val feedbackBlockedReason: DecisionGateBlockReason?,
) {
    companion object {
        fun allowAll(): DecisionGateResult {
            return DecisionGateResult(
                startEligible = true,
                stopEligible = true,
                startFeedbackEligible = true,
                stopFeedbackEligible = true,
                startBlockedReason = null,
                stopBlockedReason = null,
                feedbackBlockedReason = null,
            )
        }
    }
}
