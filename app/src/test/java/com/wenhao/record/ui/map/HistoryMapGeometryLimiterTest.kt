package com.wenhao.record.ui.map

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryMapGeometryLimiterTest {

    @Test
    fun `limits large remote history geometry before rendering`() {
        val segments = List(60) { segmentIndex ->
            List(200) { pointIndex ->
                TrackPoint(
                    latitude = 30.0 + segmentIndex * 0.001 + pointIndex * 0.00001,
                    longitude = 120.0 + segmentIndex * 0.001 + pointIndex * 0.00001,
                    timestampMillis = 1_713_420_000_000L + segmentIndex * 300_000L + pointIndex * 1_000L,
                )
            }
        }

        val limited = HistoryMapGeometryLimiter.limitSegments(segments)

        assertTrue(limited.size <= HistoryMapGeometryLimiter.MAX_RENDER_SEGMENTS)
        assertTrue(limited.sumOf { it.size } <= HistoryMapGeometryLimiter.MAX_RENDER_POINTS)
        assertEquals(segments.first().first(), limited.first().first())
        assertEquals(segments.last().last(), limited.last().last())
    }

    @Test
    fun `limits cluster markers to a small stable set`() {
        val markers = List(300) { index ->
            TrackMapMarker(
                id = "marker-$index",
                coordinate = com.wenhao.record.map.GeoCoordinate(
                    latitude = 30.0 + index * 0.0001,
                    longitude = 120.0 + index * 0.0001,
                ),
                kind = TrackMapMarkerKind.CENTER,
            )
        }

        val limited = HistoryMapGeometryLimiter.limitMarkers(markers)

        assertTrue(limited.size <= HistoryMapGeometryLimiter.MAX_CLUSTER_MARKERS)
        assertEquals(markers.first(), limited.first())
        assertEquals(markers.last(), limited.last())
    }
}
