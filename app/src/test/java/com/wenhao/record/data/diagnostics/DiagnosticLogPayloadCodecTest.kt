package com.wenhao.record.data.diagnostics

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DiagnosticLogPayloadCodecTest {

    @Test
    fun `encode drops precise coordinate fields from payload`() {
        val payload = DiagnosticLogPayloadCodec.encode(
            deviceId = "device-1",
            appVersion = "1.0.23",
            logs = listOf(
                DiagnosticLogEntry(
                    logId = "log-1",
                    occurredAt = 1_700_000_000_000L,
                    type = DiagnosticLogType.ERROR,
                    severity = DiagnosticLogSeverity.ERROR,
                    source = "RawPointUploadWorker",
                    message = "raw upload failed",
                    fingerprint = "raw-upload-failed",
                    payloadJson = """{"pointId":18,"latitude":30.1,"longitude":120.1,"durationMs":2800}""",
                )
            )
        )

        val logPayload = JSONObject(payload)
            .getJSONArray("logs")
            .getJSONObject(0)
            .getJSONObject("payload")

        assertEquals(18, logPayload.getInt("pointId"))
        assertEquals(2800, logPayload.getInt("durationMs"))
        assertFalse(logPayload.has("latitude"))
        assertFalse(logPayload.has("longitude"))
    }
}
