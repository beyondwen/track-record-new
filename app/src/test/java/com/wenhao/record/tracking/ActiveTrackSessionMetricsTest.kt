package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActiveTrackSessionMetricsTest {

    @Test
    fun `adds distance incrementally for simple append`() {
        val previousPoints = listOf(
            TrackPoint(30.0, 120.0, timestampMillis = 1_000L),
            TrackPoint(30.0003, 120.0003, timestampMillis = 6_000L)
        )
        val updatedPoints = previousPoints + TrackPoint(
            latitude = 30.0006,
            longitude = 120.0006,
            timestampMillis = 11_000L
        )

        val updatedDistanceKm = ActiveTrackSessionMetrics.updatedDistanceKm(
            previousDistanceKm = ActiveTrackSessionMetrics.calculateDistanceKm(previousPoints),
            previousPoints = previousPoints,
            updatedPoints = updatedPoints
        )

        assertTrue(updatedDistanceKm > ActiveTrackSessionMetrics.calculateDistanceKm(previousPoints))
    }

    @Test
    fun `ignores tiny jitter when recomputing distance`() {
        val points = listOf(
            TrackPoint(30.0, 120.0, timestampMillis = 1_000L),
            TrackPoint(30.0000005, 120.0000005, timestampMillis = 6_000L)
        )

        val distanceKm = ActiveTrackSessionMetrics.calculateDistanceKm(points)

        assertEquals(0.0, distanceKm)
    }

    @Test
    fun `recomputes distance when points are rewritten`() {
        val previousPoints = listOf(
            TrackPoint(30.0, 120.0, timestampMillis = 1_000L),
            TrackPoint(30.0003, 120.0003, timestampMillis = 6_000L),
            TrackPoint(30.0006, 120.0006, timestampMillis = 11_000L)
        )
        val rewrittenPoints = listOf(
            previousPoints.first(),
            previousPoints.last()
        )

        val updatedDistanceKm = ActiveTrackSessionMetrics.updatedDistanceKm(
            previousDistanceKm = ActiveTrackSessionMetrics.calculateDistanceKm(previousPoints),
            previousPoints = previousPoints,
            updatedPoints = rewrittenPoints
        )

        assertEquals(
            ActiveTrackSessionMetrics.calculateDistanceKm(rewrittenPoints),
            updatedDistanceKm
        )
    }
}
