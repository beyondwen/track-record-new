package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.AnalysisHistoryProjector
import com.wenhao.record.data.tracking.RawTrackPoint

class HistoryProjectionRecovery(
    private val projector: AnalysisHistoryProjector = AnalysisHistoryProjector(),
) {

    fun rebuildProjectedItems(
        existingHistories: List<HistoryItem>,
        rawPoints: List<RawTrackPoint>,
    ): List<HistoryItem> {
        if (existingHistories.isNotEmpty()) return emptyList()
        if (rawPoints.size < 2) return emptyList()

        val orderedPoints = rawPoints.sortedBy { it.timestampMillis }
        return projector.project(
            segments = emptyList(),
            rawPoints = orderedPoints,
        )
    }
}
