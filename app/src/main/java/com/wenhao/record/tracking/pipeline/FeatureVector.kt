package com.wenhao.record.tracking.pipeline

import com.wenhao.record.tracking.decision.DecisionGateInput
import com.wenhao.record.tracking.TrackingPhase

data class FeatureVector(
    val timestampMillis: Long,
    val features: Map<String, Double>,
    val isRecording: Boolean,
    val phase: TrackingPhase,
    val gateInput: DecisionGateInput = DecisionGateInput(
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
