package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.RawTrackPoint
import com.wenhao.record.data.tracking.SamplingTier
import com.wenhao.record.tracking.analysis.SegmentCandidate
import com.wenhao.record.tracking.analysis.SegmentKind
import com.wenhao.record.tracking.analysis.TrackAnalysisResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryProjectionRecoveryTest {

    @Test
    fun `rebuild projected items skips recovery when local history already exists`() {
        val recovery = HistoryProjectionRecovery(
            analysisRunner = {
                error("analysis should not run when history already exists")
            },
        )

        val rebuilt = recovery.rebuildProjectedItems(
            existingHistories = listOf(
                HistoryItem(
                    id = 7L,
                    timestamp = 1_000L,
                    distanceKm = 1.2,
                    durationSeconds = 600,
                    averageSpeedKmh = 7.2,
                    points = emptyList(),
                )
            ),
            rawPoints = sampleRawPoints(),
        )

        assertTrue(rebuilt.isEmpty())
    }

    @Test
    fun `rebuild projected items projects dynamic movement from raw points`() {
        val recovery = HistoryProjectionRecovery(
            analysisRunner = { points ->
                TrackAnalysisResult(
                    scoredPoints = emptyList(),
                    segments = listOf(
                        SegmentCandidate(
                            kind = SegmentKind.DYNAMIC,
                            startTimestamp = points.first().timestampMillis,
                            endTimestamp = points.last().timestampMillis,
                            pointCount = points.size,
                        )
                    ),
                    stayClusters = emptyList(),
                )
            },
        )

        val rebuilt = recovery.rebuildProjectedItems(
            existingHistories = emptyList(),
            rawPoints = sampleRawPoints(),
        )

        assertEquals(1, rebuilt.size)
        assertEquals(3, rebuilt.single().points.size)
        assertTrue(rebuilt.single().distanceKm > 0.2)
    }

    private fun sampleRawPoints(): List<RawTrackPoint> {
        return listOf(
            rawPoint(pointId = 21L, timestampMillis = 1_000L, latitude = 30.6500, longitude = 104.0500),
            rawPoint(pointId = 22L, timestampMillis = 61_000L, latitude = 30.6512, longitude = 104.0508),
            rawPoint(pointId = 23L, timestampMillis = 121_000L, latitude = 30.6524, longitude = 104.0515),
        )
    }

    private fun rawPoint(
        pointId: Long,
        timestampMillis: Long,
        latitude: Double,
        longitude: Double,
    ): RawTrackPoint {
        return RawTrackPoint(
            pointId = pointId,
            timestampMillis = timestampMillis,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = 8f,
            altitudeMeters = null,
            speedMetersPerSecond = 1.6f,
            bearingDegrees = 90f,
            provider = "gps",
            sourceType = "LOCATION",
            isMock = false,
            wifiFingerprintDigest = null,
            activityType = "WALKING",
            activityConfidence = 0.9f,
            samplingTier = SamplingTier.ACTIVE,
        )
    }
}
