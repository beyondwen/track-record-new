package com.wenhao.record.data.tracking

import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.TrackRecordSource
import com.wenhao.record.map.GeoMath

class AnalysisHistoryProjector {
    fun project(rawPoints: List<RawTrackPoint>): List<HistoryItem> {
        return projectWholeTrack(
            rawPoints = rawPoints,
            historyId = rawPoints
                .sortedBy { point -> point.timestampMillis }
                .takeIf { it.size >= 2 }
                ?.let { points ->
                    stableHistoryId(
                        startPointId = points.first().pointId,
                        endPointId = points.last().pointId,
                    )
                } ?: return emptyList(),
            startSource = TrackRecordSource.AUTO,
            stopSource = TrackRecordSource.AUTO,
            manualStartAt = null,
            manualStopAt = null,
        )
    }

    fun projectSession(
        sessionId: String,
        rawPoints: List<RawTrackPoint>,
        manualStartAt: Long?,
        manualStopAt: Long?,
    ): List<HistoryItem> {
        val stopSource = if (manualStopAt != null) {
            TrackRecordSource.MANUAL
        } else {
            TrackRecordSource.UNKNOWN
        }
        return projectWholeTrack(
            rawPoints = rawPoints,
            historyId = stableSessionHistoryId(sessionId),
            startSource = TrackRecordSource.MANUAL,
            stopSource = stopSource,
            manualStartAt = manualStartAt,
            manualStopAt = manualStopAt,
        )
    }

    private fun projectWholeTrack(
        rawPoints: List<RawTrackPoint>,
        historyId: Long,
        startSource: TrackRecordSource,
        stopSource: TrackRecordSource,
        manualStartAt: Long?,
        manualStopAt: Long?,
    ): List<HistoryItem> {
        val orderedPoints = rawPoints.sortedBy { point -> point.timestampMillis }
        if (orderedPoints.size < 2) return emptyList()

        val distanceMeters = orderedPoints.zipWithNext { first, second ->
            GeoMath.distanceMeters(
                first.latitude,
                first.longitude,
                second.latitude,
                second.longitude,
            ).toDouble()
        }.sum()
        val durationSeconds =
            ((orderedPoints.last().timestampMillis - orderedPoints.first().timestampMillis).coerceAtLeast(0L) / 1_000L).toInt()
        val averageSpeedKmh = when {
            durationSeconds <= 0 -> 0.0
            else -> (distanceMeters / 1_000.0) / (durationSeconds / 3600.0)
        }

        return listOf(
            HistoryItem(
                id = historyId,
                timestamp = orderedPoints.first().timestampMillis,
                distanceKm = distanceMeters / 1_000.0,
                durationSeconds = durationSeconds,
                averageSpeedKmh = averageSpeedKmh,
                points = orderedPoints.map { point ->
                    TrackPoint(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        timestampMillis = point.timestampMillis,
                        accuracyMeters = point.accuracyMeters,
                        altitudeMeters = point.altitudeMeters,
                        wgs84Latitude = point.latitude,
                        wgs84Longitude = point.longitude,
                    )
                },
                startSource = startSource,
                stopSource = stopSource,
                manualStartAt = manualStartAt,
                manualStopAt = manualStopAt,
            )
        )
    }

    companion object {
        fun stableHistoryId(startPointId: Long, endPointId: Long): Long {
            return (startPointId shl 32) xor (endPointId and 0xFFFF_FFFFL)
        }

        fun stableSessionHistoryId(sessionId: String): Long {
            val folded = sessionId.fold(1469598103934665603L) { acc, ch ->
                (acc xor ch.code.toLong()) * 1099511628211L
            }
            return (folded and Long.MAX_VALUE).takeIf { it != 0L } ?: 1L
        }
    }
}
