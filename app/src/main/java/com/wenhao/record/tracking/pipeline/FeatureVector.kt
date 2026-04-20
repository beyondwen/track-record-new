package com.wenhao.record.tracking.pipeline

import com.wenhao.record.tracking.TrackingPhase

data class FeatureVector(
    val timestampMillis: Long,
    val features: Map<String, Double>,
    val isRecording: Boolean,
    val phase: TrackingPhase,
)
