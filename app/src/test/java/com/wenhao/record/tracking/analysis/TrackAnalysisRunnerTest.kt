package com.wenhao.record.tracking.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackAnalysisRunnerTest {

    @Test
    fun `analyze produces dynamic segment and stay clusters for commute window`() {
        val runner = TrackAnalysisRunner()

        val result = runner.analyze(points = commuteWindow())

        assertTrue(result.segments.any { it.kind == SegmentKind.DYNAMIC })
        assertEquals(SegmentKind.STATIC, result.segments.first().kind)
        assertEquals(SegmentKind.STATIC, result.segments.last().kind)
        assertTrue(result.stayClusters.isNotEmpty())
    }

    @Test
    fun `classifier does not treat missing wifi fingerprint as stable wifi`() {
        val classifier = PointSignalClassifier()

        val score = classifier.classify(
            listOf(
                analyzedPoint(timestampMillis = 0L, latitude = 30.0, longitude = 120.0, speedMetersPerSecond = 0.1f),
                analyzedPoint(timestampMillis = 30_000L, latitude = 30.00001, longitude = 120.00001, speedMetersPerSecond = 0.1f),
                analyzedPoint(timestampMillis = 60_000L, latitude = 30.00002, longitude = 120.00002, speedMetersPerSecond = 0.1f),
            )
        )

        assertTrue(score.staticScore < 0.6f)
    }

    @Test
    fun `analyze splits static segments across long time gaps`() {
        val runner = TrackAnalysisRunner()

        val result = runner.analyze(
            points = listOf(
                analyzedPoint(timestampMillis = 0L, latitude = 30.0, longitude = 120.0, speedMetersPerSecond = 0.0f, activityType = "STILL", activityConfidence = 0.95f, wifiFingerprintDigest = "home"),
                analyzedPoint(timestampMillis = 60_000L, latitude = 30.00001, longitude = 120.00001, speedMetersPerSecond = 0.0f, activityType = "STILL", activityConfidence = 0.95f, wifiFingerprintDigest = "home"),
                analyzedPoint(timestampMillis = 120_000L, latitude = 30.00002, longitude = 120.00002, speedMetersPerSecond = 0.0f, activityType = "STILL", activityConfidence = 0.95f, wifiFingerprintDigest = "home"),
                analyzedPoint(timestampMillis = 2 * 60 * 60_000L, latitude = 30.01, longitude = 120.01, speedMetersPerSecond = 0.0f, activityType = "STILL", activityConfidence = 0.95f, wifiFingerprintDigest = "office"),
                analyzedPoint(timestampMillis = 2 * 60 * 60_000L + 60_000L, latitude = 30.01001, longitude = 120.01001, speedMetersPerSecond = 0.0f, activityType = "STILL", activityConfidence = 0.95f, wifiFingerprintDigest = "office"),
                analyzedPoint(timestampMillis = 2 * 60 * 60_000L + 120_000L, latitude = 30.01002, longitude = 120.01002, speedMetersPerSecond = 0.0f, activityType = "STILL", activityConfidence = 0.95f, wifiFingerprintDigest = "office"),
            )
        )

        assertEquals(2, result.segments.count { it.kind == SegmentKind.STATIC })
    }

    private fun commuteWindow(): List<AnalyzedPoint> {
        return listOf(
            analyzedPoint(
                timestampMillis = 0L,
                latitude = 30.00000,
                longitude = 120.00000,
                speedMetersPerSecond = 0.1f,
                activityType = "STILL",
                activityConfidence = 0.92f,
                wifiFingerprintDigest = "home-wifi",
            ),
            analyzedPoint(
                timestampMillis = 300_000L,
                latitude = 30.00002,
                longitude = 120.00001,
                speedMetersPerSecond = 0.0f,
                activityType = "STILL",
                activityConfidence = 0.91f,
                wifiFingerprintDigest = "home-wifi",
            ),
            analyzedPoint(
                timestampMillis = 600_000L,
                latitude = 30.00001,
                longitude = 120.00002,
                speedMetersPerSecond = 0.1f,
                activityType = "STILL",
                activityConfidence = 0.9f,
                wifiFingerprintDigest = "home-wifi",
            ),
            analyzedPoint(
                timestampMillis = 900_000L,
                latitude = 30.0020,
                longitude = 120.0020,
                speedMetersPerSecond = 2.1f,
                activityType = "WALKING",
                activityConfidence = 0.9f,
                wifiFingerprintDigest = "street-a",
            ),
            analyzedPoint(
                timestampMillis = 1_200_000L,
                latitude = 30.0040,
                longitude = 120.0040,
                speedMetersPerSecond = 2.4f,
                activityType = "WALKING",
                activityConfidence = 0.92f,
                wifiFingerprintDigest = "street-b",
            ),
            analyzedPoint(
                timestampMillis = 1_500_000L,
                latitude = 30.0060,
                longitude = 120.0060,
                speedMetersPerSecond = 2.3f,
                activityType = "WALKING",
                activityConfidence = 0.9f,
                wifiFingerprintDigest = "street-c",
            ),
            analyzedPoint(
                timestampMillis = 1_800_000L,
                latitude = 30.00602,
                longitude = 120.00602,
                speedMetersPerSecond = 0.1f,
                activityType = "STILL",
                activityConfidence = 0.92f,
                wifiFingerprintDigest = "office-wifi",
            ),
            analyzedPoint(
                timestampMillis = 2_100_000L,
                latitude = 30.00601,
                longitude = 120.00600,
                speedMetersPerSecond = 0.0f,
                activityType = "STILL",
                activityConfidence = 0.93f,
                wifiFingerprintDigest = "office-wifi",
            ),
            analyzedPoint(
                timestampMillis = 2_400_000L,
                latitude = 30.00600,
                longitude = 120.00601,
                speedMetersPerSecond = 0.1f,
                activityType = "STILL",
                activityConfidence = 0.91f,
                wifiFingerprintDigest = "office-wifi",
            ),
        )
    }
}

fun analyzedPoint(
    timestampMillis: Long,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float? = 8f,
    speedMetersPerSecond: Float? = null,
    activityType: String? = null,
    activityConfidence: Float? = null,
    wifiFingerprintDigest: String? = null,
): AnalyzedPoint {
    return AnalyzedPoint(
        timestampMillis = timestampMillis,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        activityType = activityType,
        activityConfidence = activityConfidence,
        wifiFingerprintDigest = wifiFingerprintDigest,
    )
}
