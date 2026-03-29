package com.wenhao.record.data.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackPathSanitizerTest {

    @Test
    fun `drops out of order timestamp point`() {
        val points = listOf(
            TrackPoint(30.0, 120.0, timestampMillis = 1_000L, accuracyMeters = 8f),
            TrackPoint(30.0003, 120.0003, timestampMillis = 2_000L, accuracyMeters = 8f),
            TrackPoint(30.0001, 120.0001, timestampMillis = 1_500L, accuracyMeters = 8f)
        )

        val sanitized = TrackPathSanitizer.sanitize(points, sortByTimestamp = false)

        assertEquals(2, sanitized.points.size)
        assertEquals(1, sanitized.removedPointCount)
        assertTrue(sanitized.totalDistanceKm > 0.0)
    }

    @Test
    fun `splits extreme jump into separate segments`() {
        val points = listOf(
            TrackPoint(30.0, 120.0, timestampMillis = 1_000L, accuracyMeters = 6f),
            TrackPoint(30.0005, 120.0005, timestampMillis = 11_000L, accuracyMeters = 6f),
            TrackPoint(31.0, 121.0, timestampMillis = 21_000L, accuracyMeters = 6f)
        )

        val sanitized = TrackPathSanitizer.sanitize(points, sortByTimestamp = true)

        assertEquals(2, sanitized.segments.size)
        assertEquals(3, sanitized.points.size)
    }

    @Test
    fun `sorts by timestamp when requested`() {
        val points = listOf(
            TrackPoint(30.0, 120.0, timestampMillis = 3_000L, accuracyMeters = 6f),
            TrackPoint(30.0004, 120.0004, timestampMillis = 1_000L, accuracyMeters = 6f),
            TrackPoint(30.0008, 120.0008, timestampMillis = 2_000L, accuracyMeters = 6f)
        )

        val sanitized = TrackPathSanitizer.sanitize(points, sortByTimestamp = true)

        assertEquals(listOf(1_000L, 2_000L, 3_000L), sanitized.points.map { it.timestampMillis })
    }

    @Test
    fun `removes spike point that jumps out and snaps back`() {
        val points = listOf(
            TrackPoint(30.0, 120.0, timestampMillis = 1_000L, accuracyMeters = 6f),
            TrackPoint(30.0011, 120.0011, timestampMillis = 11_000L, accuracyMeters = 6f),
            TrackPoint(30.00005, 120.00005, timestampMillis = 21_000L, accuracyMeters = 6f)
        )

        val sanitized = TrackPathSanitizer.sanitize(points, sortByTimestamp = true)

        assertEquals(2, sanitized.points.size)
        assertEquals(listOf(1_000L, 21_000L), sanitized.points.map { it.timestampMillis })
        assertEquals(1, sanitized.removedPointCount)
    }

    @Test
    fun `drops tiny detached segment from renderable output when longer track exists`() {
        val primarySegment = listOf(
            TrackPoint(30.0, 120.0, timestampMillis = 1_000L, accuracyMeters = 6f),
            TrackPoint(30.0004, 120.0004, timestampMillis = 11_000L, accuracyMeters = 6f),
            TrackPoint(30.0008, 120.0008, timestampMillis = 21_000L, accuracyMeters = 6f)
        )
        val tinyDetachedSegment = listOf(
            TrackPoint(30.01, 120.01, timestampMillis = 31_000L, accuracyMeters = 6f),
            TrackPoint(30.01005, 120.01005, timestampMillis = 41_000L, accuracyMeters = 6f)
        )

        val renderable = TrackPathSanitizer.renderableSegments(
            listOf(primarySegment, tinyDetachedSegment)
        )

        assertEquals(1, renderable.size)
        assertEquals(primarySegment, renderable.single())
    }
}
