package com.wenhao.record.tracking

import kotlin.math.roundToInt

data class MotionSignals(
    val stepDelta: Int,
    val effectiveDistanceMeters: Float,
    val reportedSpeedMetersPerSecond: Float,
    val inferredSpeedMetersPerSecond: Float,
    val insideAnchor: Boolean,
    val sameAnchorWifi: Boolean,
    val poorAccuracy: Boolean
)

data class MotionScoreSnapshot(
    val score: Int,
    val movingLikely: Boolean,
    val stronglyMoving: Boolean,
    val summary: String
)

class MotionConfidenceEngine {

    companion object {
        private const val ACCELERATION_MOVING_THRESHOLD = 0.85f
        private const val ACCELERATION_STRONG_THRESHOLD = 1.35f
        private const val ACCELERATION_RECENT_WINDOW_MS = 12_000L
        private const val SIGNIFICANT_MOTION_WINDOW_MS = 20_000L
        private const val SCORE_DECAY_WINDOW_MS = 18_000L
        private const val MAX_SCORE = 12
    }

    private var score = 0
    private var lastScoreUpdatedAt = 0L
    private var lastAccelerationVariance = 0f
    private var lastAccelerationMovementAt = 0L
    private var lastSignificantMotionAt = 0L
    private var lastStepMovementAt = 0L
    private var currentStepCount = 0f
    private var hasStepCount = false
    private var idleBaselineStepCount = 0f

    fun onIdleEntered() {
        score = 0
        idleBaselineStepCount = currentStepCount
        lastScoreUpdatedAt = 0L
    }

    fun onActiveEntered() {
        score = maxOf(score, 7)
    }

    fun resetAll() {
        score = 0
        lastScoreUpdatedAt = 0L
        lastAccelerationVariance = 0f
        lastAccelerationMovementAt = 0L
        lastSignificantMotionAt = 0L
        lastStepMovementAt = 0L
        if (hasStepCount) {
            idleBaselineStepCount = currentStepCount
        }
    }

    fun noteAccelerationVariance(variance: Float, timestampMillis: Long) {
        if (variance >= ACCELERATION_MOVING_THRESHOLD) {
            lastAccelerationVariance = variance
            lastAccelerationMovementAt = timestampMillis
        }
    }

    fun noteStepCount(totalSteps: Float, timestampMillis: Long): Int {
        if (!hasStepCount) {
            hasStepCount = true
            currentStepCount = totalSteps
            idleBaselineStepCount = totalSteps
            return 0
        }
        if (totalSteps > currentStepCount) {
            lastStepMovementAt = timestampMillis
        }
        currentStepCount = totalSteps
        return currentStepDelta()
    }

    fun noteSignificantMotion(timestampMillis: Long) {
        lastSignificantMotionAt = timestampMillis
    }

    fun currentStepDelta(): Int {
        if (!hasStepCount) return 0
        return (currentStepCount - idleBaselineStepCount).roundToInt().coerceAtLeast(0)
    }

    fun recentAccelerationVariance(): Float = lastAccelerationVariance

    fun hasRecentAccelerationMotion(nowMillis: Long): Boolean {
        return nowMillis - lastAccelerationMovementAt <= ACCELERATION_RECENT_WINDOW_MS
    }

    fun hasRecentSignificantMotion(nowMillis: Long): Boolean {
        return nowMillis - lastSignificantMotionAt <= SIGNIFICANT_MOTION_WINDOW_MS
    }

    fun hasRecentStepMovement(nowMillis: Long): Boolean {
        return nowMillis - lastStepMovementAt <= SIGNIFICANT_MOTION_WINDOW_MS
    }

    fun evaluate(nowMillis: Long, signals: MotionSignals): MotionScoreSnapshot {
        if (lastScoreUpdatedAt > 0L) {
            val decaySteps = ((nowMillis - lastScoreUpdatedAt) / SCORE_DECAY_WINDOW_MS).toInt()
            if (decaySteps > 0) {
                score = (score - decaySteps).coerceAtLeast(0)
            }
        }
        lastScoreUpdatedAt = nowMillis

        val reasons = mutableListOf<String>()
        var delta = 0

        if (hasRecentSignificantMotion(nowMillis)) {
            delta += 3
            reasons += "显著运动"
        }

        if (hasRecentAccelerationMotion(nowMillis)) {
            delta += if (lastAccelerationVariance >= ACCELERATION_STRONG_THRESHOLD) 3 else 2
            reasons += "加速度波动"
        }

        when {
            signals.stepDelta >= 8 -> {
                delta += 3
                reasons += "步数持续增加"
            }

            signals.stepDelta >= 3 -> {
                delta += 2
                reasons += "步数变化"
            }

            signals.stepDelta > 0 -> {
                delta += 1
                reasons += "有步数变化"
            }
        }
        if (hasRecentStepMovement(nowMillis) && signals.stepDelta > 0) {
            delta += 1
        }

        when {
            signals.effectiveDistanceMeters >= 75f -> {
                delta += 3
                reasons += "位移明显"
            }

            signals.effectiveDistanceMeters >= 35f -> {
                delta += 2
                reasons += "位移增长"
            }
        }

        val speedEvidence = maxOf(
            signals.reportedSpeedMetersPerSecond,
            signals.inferredSpeedMetersPerSecond
        )
        when {
            speedEvidence >= 2.2f -> {
                delta += 3
                reasons += "速度明显"
            }

            speedEvidence >= 1.2f -> {
                delta += 2
                reasons += "速度偏高"
            }
        }

        var penalties = 0
        if (signals.insideAnchor) {
            penalties += 1
            reasons += "常驻地保守"
        }
        if (signals.sameAnchorWifi) {
            penalties += 2
            reasons += "同一 Wi-Fi"
        }
        if (signals.poorAccuracy && signals.stepDelta == 0 && !hasRecentAccelerationMotion(nowMillis)) {
            penalties += 1
            reasons += "定位质量差"
        }

        score = (score + delta - penalties).coerceIn(0, MAX_SCORE)

        if (score <= 1 && signals.stepDelta == 0 && signals.effectiveDistanceMeters < 12f) {
            idleBaselineStepCount = currentStepCount
        }

        val summary = if (reasons.isEmpty()) {
            "分数 $score"
        } else {
            "分数 $score: ${reasons.joinToString(" / ")}"
        }
        return MotionScoreSnapshot(
            score = score,
            movingLikely = score >= 4,
            stronglyMoving = score >= 7,
            summary = summary
        )
    }
}
