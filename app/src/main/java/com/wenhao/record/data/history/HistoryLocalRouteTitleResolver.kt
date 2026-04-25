package com.wenhao.record.data.history

import android.content.Context
import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.RawLocationPointEntity
import com.wenhao.record.data.tracking.TrackPoint

class HistoryLocalRouteTitleResolver(
    private val context: Context,
    private val dao: ContinuousTrackDao,
) {
    suspend fun resolveByDay(): Map<Long, String> {
        return dao.loadRawPoints(afterPointId = 0L, limit = Int.MAX_VALUE)
            .groupBy { point -> HistoryDayAggregator.startOfDay(point.timestampMillis) }
            .mapNotNull { (dayStartMillis, points) ->
                val title = HistoryRouteTitleResolver.resolve(
                    context = context,
                    points = points.placeLookupCandidates().map { point -> point.toTrackPoint() },
                )
                title?.let { dayStartMillis to it }
            }
            .toMap()
    }

    private fun List<RawLocationPointEntity>.placeLookupCandidates(): List<RawLocationPointEntity> {
        val sorted = sortedBy { point -> point.timestampMillis }
        return listOfNotNull(
            sorted.lastOrNull(),
            sorted.firstOrNull(),
            sorted.getOrNull(sorted.size / 2),
        ).distinctBy { point ->
            "${point.latitude},${point.longitude}"
        }
    }

    private fun RawLocationPointEntity.toTrackPoint(): TrackPoint {
        return TrackPoint(
            latitude = latitude,
            longitude = longitude,
            timestampMillis = timestampMillis,
            accuracyMeters = accuracyMeters,
            altitudeMeters = altitudeMeters,
        )
    }
}
