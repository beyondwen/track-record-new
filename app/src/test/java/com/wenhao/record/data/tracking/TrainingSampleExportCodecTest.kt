package com.wenhao.record.data.tracking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSampleExportCodecTest {

    @Test
    fun `encode rows as jsonl with raw evidence fields`() {
        val payload = TrainingSampleExportCodec.encodeJsonLines(
            listOf(
                TrainingSampleRow(
                    eventId = 7L,
                    timestampMillis = 123_000L,
                    phase = "SUSPECT_MOVING",
                    isRecording = false,
                    startScore = 0.82,
                    stopScore = 0.14,
                    finalDecision = "START",
                    features = mapOf(
                        "steps_30s" to 4.0,
                        "speed_avg_30s" to 1.6,
                    ),
                    feedbackLabel = "CORRECT",
                )
            )
        )

        val line = payload.trim()
        val json = JSONObject(line)

        assertEquals(7L, json.getLong("eventId"))
        assertEquals(123_000L, json.getLong("timestampMillis"))
        assertEquals("START", json.getString("finalDecision"))
        assertEquals(0.82, json.getDouble("startScore"), 0.0001)
        assertEquals(0.14, json.getDouble("stopScore"), 0.0001)
        assertEquals("CORRECT", json.getString("feedbackLabel"))
        assertTrue(json.getJSONObject("features").has("steps_30s"))
        assertEquals(4.0, json.getJSONObject("features").getDouble("steps_30s"), 0.0001)
    }
}
