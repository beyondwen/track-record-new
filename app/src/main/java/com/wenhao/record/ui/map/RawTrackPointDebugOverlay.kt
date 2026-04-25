package com.wenhao.record.ui.map

import com.wenhao.record.data.tracking.RawTrackPoint
import com.wenhao.record.map.GeoCoordinate

internal object RawTrackPointDebugOverlay {
    const val MAX_DEBUG_POINTS = 500

    fun build(rawPoints: List<RawTrackPoint>): List<TrackMapHeatPoint> {
        return rawPoints
            .sortedBy { point -> point.timestampMillis }
            .takeLast(MAX_DEBUG_POINTS)
            .map { point ->
                TrackMapHeatPoint(
                    id = "raw-${point.pointId}",
                    coordinate = GeoCoordinate(point.latitude, point.longitude),
                    intensity = 0.72,
                    radius = 4.8,
                )
            }
    }
}
