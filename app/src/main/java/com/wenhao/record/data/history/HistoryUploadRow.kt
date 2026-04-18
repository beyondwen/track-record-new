package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrackPoint

data class HistoryUploadRow(
    val historyId: Long,
    val timestampMillis: Long,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averageSpeedKmh: Double,
    val title: String?,
    val startSource: String?,
    val stopSource: String?,
    val manualStartAt: Long?,
    val manualStopAt: Long?,
    val points: List<HistoryUploadPointRow>,
) {
    companion object {
        fun from(item: HistoryItem): HistoryUploadRow {
            return HistoryUploadRow(
                historyId = item.id,
                timestampMillis = item.timestamp,
                distanceKm = item.distanceKm,
                durationSeconds = item.durationSeconds,
                averageSpeedKmh = item.averageSpeedKmh,
                title = item.title,
                startSource = item.startSource.name,
                stopSource = item.stopSource.name,
                manualStartAt = item.manualStartAt,
                manualStopAt = item.manualStopAt,
                points = item.points.map(HistoryUploadPointRow::from),
            )
        }
    }
}

data class HistoryUploadPointRow(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Double?,
    val altitudeMeters: Double?,
    val wgs84Latitude: Double?,
    val wgs84Longitude: Double?,
) {
    companion object {
        fun from(point: TrackPoint): HistoryUploadPointRow {
            return HistoryUploadPointRow(
                latitude = point.latitude,
                longitude = point.longitude,
                timestampMillis = point.timestampMillis,
                accuracyMeters = point.accuracyMeters?.toDouble(),
                altitudeMeters = point.altitudeMeters,
                wgs84Latitude = point.wgs84Latitude,
                wgs84Longitude = point.wgs84Longitude,
            )
        }
    }
}
