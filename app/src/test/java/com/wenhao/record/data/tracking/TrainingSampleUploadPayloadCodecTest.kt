package com.wenhao.record.data.tracking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSampleUploadPayloadCodecTest {

    @Test
    fun `encode wraps rows with device and app metadata`() {
        val payload = TrainingSampleUploadPayloadCodec.encode(
            deviceId = "device-123",
            appVersion = "1.2.3",
            rows = listOf(
                TrainingSampleRow(
                    eventId = 7L,
                    recordId = 3L,
                    timestampMillis = 123_000L,
                    phase = "SUSPECT_MOVING",
                    isRecording = false,
                    startScore = 0.82,
                    stopScore = 0.14,
                    finalDecision = "START",
                    gpsQualityPass = true,
                    motionEvidencePass = true,
                    frequentPlaceClearPass = true,
                    feedbackEligible = true,
                    feedbackBlockedReason = null,
                    features = mapOf(
                        "steps_30s" to 4.0,
                        "speed_avg_30s" to 1.6,
                    ),
                    feedbackLabel = "CORRECT",
                    startSource = "MANUAL",
                    stopSource = "MANUAL",
                    manualStartAt = 100_000L,
                    manualStopAt = 180_000L,
                )
            )
        )

        val json = JSONObject(payload)

        assertEquals("device-123", json.getString("deviceId"))
        assertEquals("1.2.3", json.getString("appVersion"))
        assertEquals(1, json.getJSONArray("samples").length())

        val sample = json.getJSONArray("samples").getJSONObject(0)
        assertEquals(7L, sample.getLong("eventId"))
        assertEquals(3L, sample.getLong("recordId"))
        assertEquals(123_000L, sample.getLong("timestampMillis"))
        assertEquals("START", sample.getString("finalDecision"))
        assertEquals(0.82, sample.getDouble("startScore"), 0.0001)
        assertEquals(0.14, sample.getDouble("stopScore"), 0.0001)
        assertTrue(sample.getBoolean("gpsQualityPass"))
        assertTrue(sample.getBoolean("motionEvidencePass"))
        assertTrue(sample.getBoolean("feedbackEligible"))
        assertEquals("CORRECT", sample.getString("feedbackLabel"))
        assertEquals("MANUAL", sample.getString("startSource"))
        assertEquals("MANUAL", sample.getString("stopSource"))
        assertEquals(100_000L, sample.getLong("manualStartAt"))
        assertEquals(180_000L, sample.getLong("manualStopAt"))
        assertTrue(sample.getJSONObject("features").has("steps_30s"))
        assertEquals(4.0, sample.getJSONObject("features").getDouble("steps_30s"), 0.0001)
    }

    @Test
    fun `encode uses empty samples array for no rows`() {
        val payload = TrainingSampleUploadPayloadCodec.encode(
            deviceId = "device-123",
            appVersion = "1.2.3",
            rows = emptyList()
        )

        val json = JSONObject(payload)

        assertEquals("device-123", json.getString("deviceId"))
        assertEquals("1.2.3", json.getString("appVersion"))
        assertEquals(0, json.getJSONArray("samples").length())
    }

    @Test
    fun `encode preserves nullable keys as json null`() {
        val payload = TrainingSampleUploadPayloadCodec.encode(
            deviceId = "device-123",
            appVersion = "1.2.3",
            rows = listOf(
                TrainingSampleRow(
                    eventId = 9L,
                    recordId = null,
                    timestampMillis = 456_000L,
                    phase = "IDLE",
                    isRecording = true,
                    startScore = 0.5,
                    stopScore = 0.25,
                    finalDecision = "STOP",
                    gpsQualityPass = false,
                    motionEvidencePass = false,
                    frequentPlaceClearPass = false,
                    feedbackEligible = false,
                    feedbackBlockedReason = null,
                    features = emptyMap(),
                    feedbackLabel = null,
                    startSource = null,
                    stopSource = null,
                    manualStartAt = null,
                    manualStopAt = null,
                )
            )
        )

        val sample = JSONObject(payload).getJSONArray("samples").getJSONObject(0)

        assertTrue(sample.has("recordId"))
        assertTrue(sample.isNull("recordId"))
        assertTrue(sample.has("feedbackBlockedReason"))
        assertTrue(sample.isNull("feedbackBlockedReason"))
        assertTrue(sample.has("feedbackLabel"))
        assertTrue(sample.isNull("feedbackLabel"))
        assertTrue(sample.has("startSource"))
        assertTrue(sample.isNull("startSource"))
        assertTrue(sample.has("stopSource"))
        assertTrue(sample.isNull("stopSource"))
        assertTrue(sample.has("manualStartAt"))
        assertTrue(sample.isNull("manualStartAt"))
        assertTrue(sample.has("manualStopAt"))
        assertTrue(sample.isNull("manualStopAt"))
    }

    @Test
    fun `encode converts non finite floating point values to json null`() {
        val payload = TrainingSampleUploadPayloadCodec.encode(
            deviceId = "device-123",
            appVersion = "1.2.3",
            rows = listOf(
                TrainingSampleRow(
                    eventId = 11L,
                    recordId = 4L,
                    timestampMillis = 789_000L,
                    phase = "SUSPECT_MOVING",
                    isRecording = false,
                    startScore = Double.NaN,
                    stopScore = Double.POSITIVE_INFINITY,
                    finalDecision = "START",
                    gpsQualityPass = true,
                    motionEvidencePass = true,
                    frequentPlaceClearPass = true,
                    feedbackEligible = true,
                    feedbackBlockedReason = "blocked",
                    features = mapOf(
                        "finite" to 1.5,
                        "bad_negative" to Double.NEGATIVE_INFINITY,
                    ),
                    feedbackLabel = "CORRECT",
                    startSource = "MANUAL",
                    stopSource = "AUTO",
                    manualStartAt = 100L,
                    manualStopAt = 200L,
                )
            )
        )

        val sample = JSONObject(payload).getJSONArray("samples").getJSONObject(0)
        val features = sample.getJSONObject("features")

        assertTrue(sample.has("startScore"))
        assertTrue(sample.isNull("startScore"))
        assertTrue(sample.has("stopScore"))
        assertTrue(sample.isNull("stopScore"))
        assertTrue(features.has("bad_negative"))
        assertTrue(features.isNull("bad_negative"))
        assertEquals(1.5, features.getDouble("finite"), 0.0001)
    }
}
