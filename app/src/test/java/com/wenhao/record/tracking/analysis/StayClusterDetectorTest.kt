package com.wenhao.record.tracking.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StayClusterDetectorTest {

    @Test
    fun `detect creates stay cluster from stable static segment`() {
        val detector = StayClusterDetector()
        val points = listOf(
            analyzedPoint(timestampMillis = 0L, latitude = 30.0, longitude = 120.0),
            analyzedPoint(timestampMillis = 300_000L, latitude = 30.00002, longitude = 120.00001),
            analyzedPoint(timestampMillis = 600_000L, latitude = 30.00001, longitude = 120.00002),
        )
        val segments = listOf(
            SegmentCandidate(
                kind = SegmentKind.STATIC,
                startTimestamp = 0L,
                endTimestamp = 600_000L,
                pointCount = 3,
            ),
        )

        val clusters = detector.detect(points = points, segments = segments)

        assertEquals(1, clusters.size)
        assertTrue(clusters.single().radiusMeters <= 25f)
        assertTrue(clusters.single().confidence >= 0.7f)
    }

    @Test
    fun `detect ignores short static pause`() {
        val detector = StayClusterDetector()
        val points = listOf(
            analyzedPoint(timestampMillis = 0L, latitude = 30.0, longitude = 120.0),
            analyzedPoint(timestampMillis = 60_000L, latitude = 30.00001, longitude = 120.00001),
        )
        val segments = listOf(
            SegmentCandidate(
                kind = SegmentKind.STATIC,
                startTimestamp = 0L,
                endTimestamp = 60_000L,
                pointCount = 2,
            ),
        )

        val clusters = detector.detect(points = points, segments = segments)

        assertTrue(clusters.isEmpty())
    }
}
