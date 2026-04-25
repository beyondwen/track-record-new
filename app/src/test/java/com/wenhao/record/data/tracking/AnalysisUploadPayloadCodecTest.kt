package com.wenhao.record.data.tracking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisUploadPayloadCodecTest {

    @Test
    fun `encode includes segments and nested stay clusters`() {
        val payload = AnalysisUploadPayloadCodec.encode(
            deviceId = "device-1",
            appVersion = "1.0.23",
            rows = listOf(analysisRow(segmentId = 101L))
        )

        val json = JSONObject(payload)

        assertEquals("device-1", json.getString("deviceId"))
        assertEquals("1.0.23", json.getString("appVersion"))
        assertEquals(1, json.getJSONArray("segments").length())

        val segment = json.getJSONArray("segments").getJSONObject(0)
        assertEquals(101L, segment.getLong("segmentId"))
        assertEquals("STATIC", segment.getString("segmentType"))
        assertEquals(1, segment.getJSONArray("stayClusters").length())

        val stay = segment.getJSONArray("stayClusters").getJSONObject(0)
        assertEquals(201L, stay.getLong("stayId"))
        assertTrue(stay.getDouble("centerLat") > 0.0)
    }

    @Test
    fun `encode maps oversized stay id to worker safe integer`() {
        val payload = AnalysisUploadPayloadCodec.encode(
            deviceId = "device-1",
            appVersion = "1.0.23",
            rows = listOf(
                analysisRow(
                    segmentId = 8_800_387_993_599L,
                    stayId = 8_800_414_393_092_970_029L,
                )
            ),
        )

        val stay = JSONObject(payload)
            .getJSONArray("segments")
            .getJSONObject(0)
            .getJSONArray("stayClusters")
            .getJSONObject(0)

        val encodedStayId = stay.getLong("stayId")
        assertTrue(encodedStayId > 0L)
        assertTrue(encodedStayId <= 9_007_199_254_740_991L)
    }

    private fun analysisRow(
        segmentId: Long,
        stayId: Long = 201L,
    ): AnalysisUploadRow {
        return AnalysisUploadRow(
            segmentId = segmentId,
            startPointId = 11L,
            endPointId = 19L,
            startTimestamp = 100_000L,
            endTimestamp = 160_000L,
            segmentType = "STATIC",
            confidence = 0.95,
            distanceMeters = 18.0,
            durationMillis = 60_000L,
            avgSpeedMetersPerSecond = 0.2,
            maxSpeedMetersPerSecond = 0.4,
            analysisVersion = 1,
            stayClusters = listOf(
                AnalysisStayClusterUploadRow(
                    stayId = stayId,
                    centerLat = 30.1,
                    centerLng = 120.1,
                    radiusMeters = 25.0,
                    arrivalTime = 100_000L,
                    departureTime = 160_000L,
                    confidence = 0.91,
                    analysisVersion = 1,
                )
            ),
        )
    }
}
