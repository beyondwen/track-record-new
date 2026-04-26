package com.wenhao.record.ui.map

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackPolylineBuilderTest {

    @Test
    fun `buildCompact keeps more vertices when point budget increases`() {
        val detailedSegment = List(240) { index ->
            val offset = if (index % 2 == 0) 0.00018 else -0.00018
            TrackPoint(
                latitude = 30.0 + index * 0.00004,
                longitude = 120.0 + offset,
                timestampMillis = 1_713_420_000_000L + index * 1_000L,
                altitudeMeters = 20.0 + (index % 6),
            )
        }

        val compactLow = TrackPolylineBuilder.buildCompact(
            segments = listOf(detailedSegment),
            idPrefix = "history-low",
            width = 6.6,
            maxPointsPerSegment = 90,
            altitudeBuckets = 4,
        )
        val compactHigh = TrackPolylineBuilder.buildCompact(
            segments = listOf(detailedSegment),
            idPrefix = "history-high",
            width = 6.6,
            maxPointsPerSegment = HISTORY_POLYLINE_MAX_POINTS_PER_SEGMENT,
            altitudeBuckets = HISTORY_POLYLINE_ALTITUDE_BUCKETS,
        )

        val lowVertexCount = compactLow.sumOf { it.points.size }
        val highVertexCount = compactHigh.sumOf { it.points.size }

        assertTrue(highVertexCount > lowVertexCount)
        assertEquals(detailedSegment.first().toGeoCoordinate(), compactHigh.first().points.first())
        assertEquals(detailedSegment.last().toGeoCoordinate(), compactHigh.last().points.last())
    }
}
