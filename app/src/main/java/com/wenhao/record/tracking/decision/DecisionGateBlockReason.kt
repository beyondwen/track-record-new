package com.wenhao.record.tracking.decision

enum class DecisionGateBlockReason {
    GPS_MISSING,
    GPS_POOR_ACCURACY,
    MOTION_MISSING,
    INSIDE_FREQUENT_PLACE,
    STOP_LOW_CONFIDENCE,
    FEEDBACK_BLOCKED_LOW_QUALITY,
}
