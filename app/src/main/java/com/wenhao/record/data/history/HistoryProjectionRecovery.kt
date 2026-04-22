package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.AnalysisHistoryProjector
import com.wenhao.record.data.tracking.RawTrackPoint
import com.wenhao.record.tracking.analysis.AnalyzedPoint
import com.wenhao.record.tracking.analysis.TrackAnalysisResult
import com.wenhao.record.tracking.analysis.TrackAnalysisRunner

class HistoryProjectionRecovery(
    private val analysisRunner: (List<AnalyzedPoint>) -> TrackAnalysisResult = { points ->
        TrackAnalysisRunner().analyze(points = points, previousContext = null)
    },
    private val projector: AnalysisHistoryProjector = AnalysisHistoryProjector(),
) {

    fun rebuildProjectedItems(
        existingHistories: List<HistoryItem>,
        rawPoints: List<RawTrackPoint>,
    ): List<HistoryItem> {
        if (existingHistories.isNotEmpty()) return emptyList()
        if (rawPoints.size < 3) return emptyList()

        val orderedPoints = rawPoints.sortedBy { it.timestampMillis }
        val analysis = analysisRunner(orderedPoints.map { point -> point.toAnalyzedPoint() })
        return projector.project(
            segments = analysis.segments,
            rawPoints = orderedPoints,
        )
    }

    private fun RawTrackPoint.toAnalyzedPoint(): AnalyzedPoint {
        return AnalyzedPoint(
            timestampMillis = timestampMillis,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            speedMetersPerSecond = speedMetersPerSecond,
            activityType = activityType,
            activityConfidence = activityConfidence,
            wifiFingerprintDigest = wifiFingerprintDigest,
        )
    }
}
