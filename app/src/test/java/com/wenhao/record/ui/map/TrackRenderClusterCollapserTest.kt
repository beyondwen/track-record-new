package com.wenhao.record.ui.map

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackRenderClusterCollapserTest {

    @Test
    fun `collapses dense scribble run into a center point`() {
        val segment = listOf(
            TrackPoint(30.00000, 120.00000, timestampMillis = 1_000L, accuracyMeters = 6f),
            TrackPoint(30.00008, 120.00009, timestampMillis = 2_000L, accuracyMeters = 6f),
            TrackPoint(30.00015, 120.00002, timestampMillis = 3_000L, accuracyMeters = 6f),
            TrackPoint(30.00005, 120.00016, timestampMillis = 4_000L, accuracyMeters = 6f),
            TrackPoint(30.00018, 120.00014, timestampMillis = 5_000L, accuracyMeters = 6f),
            TrackPoint(30.00006, 120.00004, timestampMillis = 6_000L, accuracyMeters = 6f),
            TrackPoint(30.00045, 120.00042, timestampMillis = 7_000L, accuracyMeters = 6f),
        )

        val collapsed = TrackRenderClusterCollapser.collapse(segment)

        assertEquals(1, collapsed.clusterCenters.size)
        assertTrue(collapsed.points.size < segment.size)
        assertEquals(2, collapsed.points.size)
    }

    @Test
    fun `keeps a short orderly segment untouched`() {
        val segment = listOf(
            TrackPoint(30.00000, 120.00000, timestampMillis = 1_000L, accuracyMeters = 6f),
            TrackPoint(30.00004, 120.00004, timestampMillis = 2_000L, accuracyMeters = 6f),
            TrackPoint(30.00008, 120.00008, timestampMillis = 3_000L, accuracyMeters = 6f),
            TrackPoint(30.00012, 120.00012, timestampMillis = 4_000L, accuracyMeters = 6f),
        )

        val collapsed = TrackRenderClusterCollapser.collapse(segment)

        assertTrue(collapsed.clusterCenters.isEmpty())
        assertEquals(segment, collapsed.points)
    }
}
