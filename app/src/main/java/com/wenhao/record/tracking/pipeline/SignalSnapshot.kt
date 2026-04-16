package com.wenhao.record.tracking.pipeline

import com.wenhao.record.tracking.TrackingPhase

data class SignalSnapshot(
    val timestampMillis: Long,
    val phase: TrackingPhase,
    val isRecording: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val stepDelta: Int,
    val accelerationMagnitude: Float?,
    val wifiChanged: Boolean,
    val insideFrequentPlace: Boolean,
    val candidateStateDurationMillis: Long,
    val protectionRemainingMillis: Long,
)
