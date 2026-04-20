package com.wenhao.record.tracking

internal fun ignoredLocationDecision(
    phase: TrackingPhase,
    accuracyMeters: Float?,
    speedMetersPerSecond: Float?,
): String? {
    val roundedAccuracy = accuracyMeters?.toInt()
    return when {
        phase in setOf(TrackingPhase.IDLE, TrackingPhase.SUSPECT_MOVING) && roundedAccuracy == null ->
            "已忽略：低频待命缺少精度信息"

        phase in setOf(TrackingPhase.IDLE, TrackingPhase.SUSPECT_MOVING) &&
            accuracyMeters != null &&
            accuracyMeters > 80f ->
            "已忽略：${phaseLabelForDiagnostics(phase)}精度 ${roundedAccuracy}m > 80m"

        phase in setOf(TrackingPhase.ACTIVE, TrackingPhase.SUSPECT_STOPPING) && accuracyMeters != null && accuracyMeters > 35f ->
            "已忽略：${phaseLabelForDiagnostics(phase)}精度 ${roundedAccuracy}m > 35m"

        phase in setOf(TrackingPhase.ACTIVE, TrackingPhase.SUSPECT_STOPPING) &&
            speedMetersPerSecond != null &&
            speedMetersPerSecond > 45f ->
            "已忽略：${phaseLabelForDiagnostics(phase)}速度 ${speedMetersPerSecond.toInt()}m/s > 45m/s"

        else -> null
    }
}

internal fun acceptedLocationDecision(
    phase: TrackingPhase,
    acceptedPointCount: Int,
    accuracyMeters: Float?,
): String {
    return buildString {
        append("已接收：")
        append(phaseLabelForDiagnostics(phase))
        append("，第 ")
        append(acceptedPointCount)
        append(" 个点")
        if (accuracyMeters != null) {
            append("，精度 ")
            append(accuracyMeters.toInt())
            append("m")
        }
    }
}

internal fun phaseLabelForDiagnostics(phase: TrackingPhase): String {
    return when (phase) {
        TrackingPhase.IDLE -> "低频待命"
        TrackingPhase.SUSPECT_MOVING -> "疑似移动"
        TrackingPhase.ACTIVE -> "活跃采样"
        TrackingPhase.SUSPECT_STOPPING -> "停止观察"
    }
}
