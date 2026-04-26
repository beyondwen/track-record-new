package com.wenhao.record.data.history

import com.wenhao.record.data.local.history.HistoryDao
import com.wenhao.record.data.local.history.HistoryPointEntity
import com.wenhao.record.data.local.history.HistoryRecordEntity
import com.wenhao.record.data.tracking.TrackPoint

class LocalHistoryRepository(
    private val dao: HistoryDao,
) {
    suspend fun loadDailyItems(): List<HistoryDayItem> {
        return HistoryDayAggregator.aggregate(
            dao.getHistoryRecords().map { record ->
                record.toHistoryItem(points = emptyList())
            }
        )
    }

    suspend fun loadDailySummaries(): List<HistoryDaySummaryItem> {
        return loadDailyItems().map { item ->
            item.toSummaryItem()
        }
    }

    suspend fun loadDayDetail(dayStartMillis: Long): HistoryDayItem? {
        val items = dao.getHistoryRecordsByDay(dayStartMillis).map { record ->
            record.toHistoryItem(
                points = dao.getHistoryPoints(record.historyId).map { point -> point.toTrackPoint() }
            )
        }
        return HistoryDayAggregator.aggregate(items).firstOrNull()
    }

    private fun HistoryRecordEntity.toHistoryItem(points: List<TrackPoint>): HistoryItem {
        return HistoryItem(
            id = historyId,
            timestamp = timestamp,
            distanceKm = distanceKm,
            durationSeconds = durationSeconds,
            averageSpeedKmh = averageSpeedKmh,
            title = title,
            points = points,
            startSource = TrackRecordSource.fromStorage(startSource),
            stopSource = TrackRecordSource.fromStorage(stopSource),
            manualStartAt = manualStartAt,
            manualStopAt = manualStopAt,
        )
    }

    private fun HistoryPointEntity.toTrackPoint(): TrackPoint {
        return TrackPoint(
            latitude = latitude,
            longitude = longitude,
            timestampMillis = timestampMillis,
            accuracyMeters = accuracyMeters,
            altitudeMeters = altitudeMeters,
            wgs84Latitude = wgs84Latitude,
            wgs84Longitude = wgs84Longitude,
        )
    }
}
