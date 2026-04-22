package com.wenhao.record.tracking.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackAnalysisRunnerOutlierTest {

    @Test
    fun `analyze keeps stationary stay static when window contains poor accuracy jump`() {
        val runner = TrackAnalysisRunner()

        val result = runner.analyze(
            points = listOf(
                analyzedPoint(
                    timestampMillis = 0L,
                    latitude = 30.0,
                    longitude = 120.0,
                    accuracyMeters = 8f,
                    speedMetersPerSecond = 0.1f,
                    activityType = "STILL",
                    activityConfidence = 0.92f,
                    wifiFingerprintDigest = "home",
                ),
                analyzedPoint(
                    timestampMillis = 300_000L,
                    latitude = 30.00001,
                    longitude = 120.00002,
                    accuracyMeters = 8f,
                    speedMetersPerSecond = 0.1f,
                    activityType = "STILL",
                    activityConfidence = 0.9f,
                    wifiFingerprintDigest = "home",
                ),
                analyzedPoint(
                    timestampMillis = 600_000L,
                    latitude = 30.0026,
                    longitude = 120.0026,
                    accuracyMeters = 120f,
                    speedMetersPerSecond = 6.5f,
                    activityType = "WALKING",
                    activityConfidence = 0.51f,
                    wifiFingerprintDigest = "unknown",
                ),
                analyzedPoint(
                    timestampMillis = 900_000L,
                    latitude = 30.00002,
                    longitude = 120.00001,
                    accuracyMeters = 9f,
                    speedMetersPerSecond = 0.0f,
                    activityType = "STILL",
                    activityConfidence = 0.93f,
                    wifiFingerprintDigest = "home",
                ),
                analyzedPoint(
                    timestampMillis = 1_200_000L,
                    latitude = 30.00001,
                    longitude = 120.00000,
                    accuracyMeters = 8f,
                    speedMetersPerSecond = 0.1f,
                    activityType = "STILL",
                    activityConfidence = 0.91f,
                    wifiFingerprintDigest = "home",
                ),
            )
        )

        assertEquals(listOf(SegmentKind.STATIC), result.segments.map { it.kind })
        assertEquals(1, result.stayClusters.size)
        assertTrue(result.scoredPoints.all { it.kind != SegmentKind.DYNAMIC })
    }
}
