package com.wenhao.record.data.history

import com.wenhao.record.data.local.stream.ContinuousTrackDao

class HistoryLocalSegmentCounter(
    private val dao: ContinuousTrackDao,
) {
    suspend fun countByDay(): Map<Long, Int> {
        return dao.loadAnalysisSegments(afterSegmentId = 0L, limit = Int.MAX_VALUE)
            .groupingBy { segment -> HistoryDayAggregator.startOfDay(segment.startTimestamp) }
            .eachCount()
    }
}
