package com.wenhao.record.data.tracking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawPointUploadPayloadCodecTest {

    @Test
    fun `encode includes device app version and points array`() {
        val payload = RawPointUploadPayloadCodec.encode(
            deviceId = "device-1",
            appVersion = "1.0.23",
            rows = listOf(rawPointRow(pointId = 11L))
        )

        val json = JSONObject(payload)

        assertEquals("device-1", json.getString("deviceId"))
        assertEquals("1.0.23", json.getString("appVersion"))
        assertEquals(1, json.getJSONArray("points").length())

        val point = json.getJSONArray("points").getJSONObject(0)
        assertEquals(11L, point.getLong("pointId"))
        assertEquals(123_000L, point.getLong("timestampMillis"))
        assertEquals("gps", point.getString("provider"))
        assertEquals("LOCATION_MANAGER", point.getString("sourceType"))
        assertEquals("IDLE", point.getString("samplingTier"))
        assertTrue(point.isNull("bearingDegrees").not())
    }

    private fun rawPointRow(pointId: Long): RawPointUploadRow {
        return RawPointUploadRow(
            pointId = pointId,
            timestampMillis = 123_000L,
            latitude = 30.1,
            longitude = 120.1,
            accuracyMeters = 6.5,
            altitudeMeters = 12.0,
            speedMetersPerSecond = 1.8,
            bearingDegrees = 90.0,
            provider = "gps",
            sourceType = "LOCATION_MANAGER",
            isMock = false,
            wifiFingerprintDigest = "wifi",
            activityType = "WALKING",
            activityConfidence = 0.91,
            samplingTier = "IDLE",
        )
    }
}
