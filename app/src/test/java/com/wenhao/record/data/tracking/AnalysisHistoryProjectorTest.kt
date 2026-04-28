package com.wenhao.record.data.tracking

import com.wenhao.record.tracking.analysis.SegmentCandidate
import com.wenhao.record.tracking.analysis.SegmentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisHistoryProjectorTest {

    @Test
    fun `project maps dynamic segment to stable history item`() {
        val projector = AnalysisHistoryProjector()
        val rawPoints = demoSegmentPoints()
        val segment = SegmentCandidate(
            kind = SegmentKind.DYNAMIC,
            startTimestamp = 1_000L,
            endTimestamp = 61_000L,
            pointCount = rawPoints.size,
        )

        val projected = projector.project(
            segments = listOf(segment),
            rawPoints = rawPoints,
        )

        assertEquals(1, projected.size)
        assertEquals(
            AnalysisHistoryProjector.stableHistoryId(
                startPointId = rawPoints.first().pointId,
                endPointId = rawPoints.last().pointId,
            ),
            projected.single().id,
        )
        assertTrue(projected.single().distanceKm in 1.24..1.27)
        assertEquals(rawPoints.size, projected.single().points.size)
    }

    @Test
    fun `project keeps raw points as wgs84 coordinates for history points`() {
        val projector = AnalysisHistoryProjector()
        val rawPoints = demoSegmentPoints()
        val segment = SegmentCandidate(
            kind = SegmentKind.DYNAMIC,
            startTimestamp = rawPoints.first().timestampMillis,
            endTimestamp = rawPoints.last().timestampMillis,
            pointCount = rawPoints.size,
        )

        val projectedPoint = projector.project(
            segments = listOf(segment),
            rawPoints = rawPoints,
        ).single().points.first()

        assertEquals(rawPoints.first().latitude, projectedPoint.wgs84Latitude ?: Double.NaN, 1e-6)
        assertEquals(rawPoints.first().longitude, projectedPoint.wgs84Longitude ?: Double.NaN, 1e-6)
    }

    @Test
    fun `project keeps full raw session even when segment only covers prefix`() {
        val projector = AnalysisHistoryProjector()
        val rawPoints = demoSegmentPoints()
        val prefixOnlySegment = SegmentCandidate(
            kind = SegmentKind.DYNAMIC,
            startTimestamp = rawPoints.first().timestampMillis,
            endTimestamp = rawPoints[1].timestampMillis,
            pointCount = 2,
        )

        val projected = projector.project(
            segments = listOf(prefixOnlySegment),
            rawPoints = rawPoints,
        )

        assertEquals(1, projected.size)
        assertEquals(rawPoints.size, projected.single().points.size)
        assertTrue(projected.single().distanceKm > 1.24)
    }

    private fun demoSegmentPoints(): List<RawTrackPoint> {
        return listOf(
            rawPoint(pointId = 101L, timestampMillis = 1_000L, latitude = 30.00000, longitude = 120.00000),
            rawPoint(pointId = 102L, timestampMillis = 21_000L, latitude = 30.00375, longitude = 120.00000),
            rawPoint(pointId = 103L, timestampMillis = 41_000L, latitude = 30.00750, longitude = 120.00000),
            rawPoint(pointId = 104L, timestampMillis = 61_000L, latitude = 30.01125, longitude = 120.00000),
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
            speedMetersPerSecond = null,
            bearingDegrees = null,
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
