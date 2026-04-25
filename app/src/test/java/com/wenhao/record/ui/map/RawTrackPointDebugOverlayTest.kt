package com.wenhao.record.ui.map

import com.wenhao.record.data.tracking.RawTrackPoint
import com.wenhao.record.data.tracking.SamplingTier
import kotlin.test.Test
import kotlin.test.assertEquals

class RawTrackPointDebugOverlayTest {

    @Test
    fun `buildRawTrackDebugPoints keeps recent database points and caps count`() {
        val rawPoints = List(650) { index ->
            RawTrackPoint(
                pointId = index.toLong(),
                timestampMillis = 1_000L + index,
                latitude = 30.0 + index * 0.00001,
                longitude = 120.0 + index * 0.00001,
                accuracyMeters = 8f,
                altitudeMeters = null,
                speedMetersPerSecond = null,
                bearingDegrees = null,
                provider = "gps",
                sourceType = "background",
                isMock = false,
                wifiFingerprintDigest = null,
                activityType = null,
                activityConfidence = null,
                samplingTier = SamplingTier.ACTIVE,
            )
        }

        val debugPoints = RawTrackPointDebugOverlay.build(rawPoints)

        assertEquals(500, debugPoints.size)
        assertEquals("raw-150", debugPoints.first().id)
        assertEquals("raw-649", debugPoints.last().id)
    }
}
