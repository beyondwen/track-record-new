package com.wenhao.record.data.tracking

import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.TrackRecordSource
import com.wenhao.record.map.GeoMath
import com.wenhao.record.tracking.analysis.SegmentCandidate
import com.wenhao.record.tracking.analysis.SegmentKind

class AnalysisHistoryProjector {
    fun project(
        segments: List<SegmentCandidate>,
        rawPoints: List<RawTrackPoint>,
    ): List<HistoryItem> {
        return segments
            .asSequence()
            .filter { it.kind == SegmentKind.DYNAMIC }
            .mapNotNull { segment ->
                val segmentPoints = rawPoints.filter { point ->
                    point.timestampMillis in segment.startTimestamp..segment.endTimestamp
                }
                if (segmentPoints.size < 2) return@mapNotNull null

                val distanceMeters = segmentPoints.zipWithNext { first, second ->
                    GeoMath.distanceMeters(
                        first.latitude,
                        first.longitude,
                        second.latitude,
                        second.longitude,
                    ).toDouble()
                }.sum()
                val durationSeconds =
                    ((segment.endTimestamp - segment.startTimestamp).coerceAtLeast(0L) / 1_000L).toInt()
                val averageSpeedKmh = when {
                    durationSeconds <= 0 -> 0.0
                    else -> (distanceMeters / 1_000.0) / (durationSeconds / 3600.0)
                }

                HistoryItem(
                    id = stableHistoryId(
                        startPointId = segmentPoints.first().pointId,
                        endPointId = segmentPoints.last().pointId,
                    ),
                    timestamp = segment.startTimestamp,
                    distanceKm = distanceMeters / 1_000.0,
                    durationSeconds = durationSeconds,
                    averageSpeedKmh = averageSpeedKmh,
                    points = segmentPoints.map { point ->
                        TrackPoint(
                            latitude = point.latitude,
                            longitude = point.longitude,
                            timestampMillis = point.timestampMillis,
                            accuracyMeters = point.accuracyMeters,
                            altitudeMeters = point.altitudeMeters,
                        )
                    },
                    startSource = TrackRecordSource.AUTO,
                    stopSource = TrackRecordSource.AUTO,
                )
            }
            .toList()
    }

    companion object {
        fun stableHistoryId(startPointId: Long, endPointId: Long): Long {
            return (startPointId shl 32) xor (endPointId and 0xFFFF_FFFFL)
        }
    }
}
