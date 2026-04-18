package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrackPoint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryUploadPayloadCodecTest {

    @Test
    fun `encode includes device app and point payload`() {
        val payload = JSONObject(
            HistoryUploadPayloadCodec.encode(
                deviceId = "device-1",
                appVersion = "1.0.22",
                rows = listOf(
                    HistoryUploadRow(
                        historyId = 7L,
                        timestampMillis = 1_717_171_717_000L,
                        distanceKm = 12.34,
                        durationSeconds = 890,
                        averageSpeedKmh = 49.9,
                        title = "通勤",
                        startSource = "MANUAL",
                        stopSource = "MODEL",
                        manualStartAt = 1_717_171_700_000L,
                        manualStopAt = null,
                        points = listOf(
                            HistoryUploadPointRow(
                                latitude = 30.1,
                                longitude = 120.1,
                                timestampMillis = 1_000L,
                                accuracyMeters = 8.5,
                                altitudeMeters = 16.2,
                                wgs84Latitude = 30.09,
                                wgs84Longitude = 120.09,
                            )
                        ),
                    )
                ),
            )
        )

        assertEquals("device-1", payload.getString("deviceId"))
        assertEquals("1.0.22", payload.getString("appVersion"))
        val histories = payload.getJSONArray("histories")
        assertEquals(1, histories.length())
        val row = histories.getJSONObject(0)
        assertEquals(7L, row.getLong("historyId"))
        assertEquals(1_717_171_717_000L, row.getLong("timestampMillis"))
        assertEquals(12.34, row.getDouble("distanceKm"), 0.0001)
        assertEquals(890, row.getInt("durationSeconds"))
        assertEquals(49.9, row.getDouble("averageSpeedKmh"), 0.0001)
        assertEquals("通勤", row.getString("title"))
        assertEquals("MANUAL", row.getString("startSource"))
        assertEquals("MODEL", row.getString("stopSource"))
        assertEquals(1_717_171_700_000L, row.getLong("manualStartAt"))
        assertEquals(JSONObject.NULL, row.get("manualStopAt"))
        val points = row.getJSONArray("points")
        assertEquals(1, points.length())
        val point = points.getJSONObject(0)
        assertEquals(30.1, point.getDouble("latitude"), 0.0001)
        assertEquals(120.1, point.getDouble("longitude"), 0.0001)
        assertEquals(1_000L, point.getLong("timestampMillis"))
        assertEquals(8.5, point.getDouble("accuracyMeters"), 0.0001)
        assertEquals(16.2, point.getDouble("altitudeMeters"), 0.0001)
        assertEquals(30.09, point.getDouble("wgs84Latitude"), 0.0001)
        assertEquals(120.09, point.getDouble("wgs84Longitude"), 0.0001)
    }

    @Test
    fun `from history item keeps nullable point fields as null`() {
        val row = HistoryUploadRow.from(
            HistoryItem(
                id = 9L,
                timestamp = 2_000L,
                distanceKm = 1.2,
                durationSeconds = 300,
                averageSpeedKmh = 14.4,
                points = listOf(
                    TrackPoint(
                        latitude = 30.2,
                        longitude = 120.2,
                        timestampMillis = 4_000L,
                    )
                ),
            )
        )

        assertEquals(9L, row.historyId)
        assertEquals(1, row.points.size)
        assertNull(row.points.first().accuracyMeters)
        assertNull(row.points.first().altitudeMeters)
        assertNull(row.points.first().wgs84Latitude)
        assertNull(row.points.first().wgs84Longitude)
        val encodedPoint = JSONArray(
            JSONObject(
                HistoryUploadPayloadCodec.encode(
                    deviceId = "device-1",
                    appVersion = "1.0.22",
                    rows = listOf(row),
                )
            ).getJSONArray("histories")
                .getJSONObject(0)
                .getJSONArray("points")
                .toString()
        ).getJSONObject(0)
        assertEquals(JSONObject.NULL, encodedPoint.get("accuracyMeters"))
        assertEquals(JSONObject.NULL, encodedPoint.get("altitudeMeters"))
        assertEquals(JSONObject.NULL, encodedPoint.get("wgs84Latitude"))
        assertEquals(JSONObject.NULL, encodedPoint.get("wgs84Longitude"))
    }
}
