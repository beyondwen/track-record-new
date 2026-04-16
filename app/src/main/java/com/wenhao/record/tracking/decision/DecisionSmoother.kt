package com.wenhao.record.tracking.decision

enum class FinalDecision {
    START,
    STOP,
    HOLD,
}

class DecisionSmoother(
    private val startTriggerCount: Int,
    private val stopTriggerCount: Int,
    private val startThreshold: Double,
    private val stopThreshold: Double,
    private val startProtectionMillis: Long,
    private val minimumRecordingMillis: Long,
) {
    private var startHits = 0
    private var stopHits = 0
    private var lastStartedAt = Long.MIN_VALUE

    fun consume(
        startScore: Double,
        stopScore: Double,
        nowMillis: Long,
        isRecording: Boolean,
    ): FinalDecision {
        if (!isRecording) {
            startHits = if (startScore >= startThreshold) startHits + 1 else 0
            stopHits = 0
            return if (startHits >= startTriggerCount) {
                startHits = 0
                lastStartedAt = nowMillis
                FinalDecision.START
            } else {
                FinalDecision.HOLD
            }
        }

        if (lastStartedAt == Long.MIN_VALUE) {
            lastStartedAt = nowMillis
        }
        if (nowMillis - lastStartedAt < startProtectionMillis ||
            nowMillis - lastStartedAt < minimumRecordingMillis
        ) {
            stopHits = 0
            return FinalDecision.HOLD
        }

        startHits = 0
        stopHits = if (stopScore >= stopThreshold) stopHits + 1 else 0
        return if (stopHits >= stopTriggerCount) {
            stopHits = 0
            FinalDecision.STOP
        } else {
            FinalDecision.HOLD
        }
    }
}
