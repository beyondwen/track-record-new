package com.wenhao.record.tracking.decision

object DecisionGateEvaluator {
    private const val MIN_GPS_SAMPLES_30S = 2.0
    private const val MAX_GOOD_ACCURACY_METERS = 35.0
    private const val MIN_RECORDING_SECONDS_FOR_STOP = 120.0

    fun evaluate(input: DecisionGateInput): DecisionGateResult {
        val gpsBlockedReason = gpsBlockedReason(input)
        val startBlockedReason = gpsBlockedReason
            ?: if (!input.motionEvidence30s) DecisionGateBlockReason.MOTION_MISSING else null
            ?: if (input.insideFrequentPlace) DecisionGateBlockReason.INSIDE_FREQUENT_PLACE else null

        val stopEligible = input.isRecording &&
            input.recordingDurationSeconds >= MIN_RECORDING_SECONDS_FOR_STOP &&
            input.stopObservationPassed
        val stopFeedbackEligible = stopEligible &&
            gpsBlockedReason == null &&
            input.motionEvidence30s

        val feedbackBlockedReason = when {
            input.isRecording && !stopFeedbackEligible -> DecisionGateBlockReason.FEEDBACK_BLOCKED_LOW_QUALITY
            !input.isRecording && startBlockedReason != null -> DecisionGateBlockReason.FEEDBACK_BLOCKED_LOW_QUALITY
            else -> null
        }

        return DecisionGateResult(
            startEligible = startBlockedReason == null,
            stopEligible = stopEligible,
            startFeedbackEligible = startBlockedReason == null,
            stopFeedbackEligible = stopFeedbackEligible,
            startBlockedReason = startBlockedReason,
            stopBlockedReason = if (stopEligible) null else DecisionGateBlockReason.STOP_LOW_CONFIDENCE,
            feedbackBlockedReason = feedbackBlockedReason,
        )
    }

    private fun gpsBlockedReason(input: DecisionGateInput): DecisionGateBlockReason? {
        return when {
            input.gpsSampleCount30s < MIN_GPS_SAMPLES_30S -> DecisionGateBlockReason.GPS_MISSING
            input.gpsAccuracyAvg30s <= 0.0 || input.gpsAccuracyAvg30s > MAX_GOOD_ACCURACY_METERS ->
                DecisionGateBlockReason.GPS_POOR_ACCURACY
            else -> null
        }
    }
}
