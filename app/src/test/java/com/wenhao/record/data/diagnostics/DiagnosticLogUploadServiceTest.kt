package com.wenhao.record.data.diagnostics

import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.UploadHttpResponse
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticLogUploadServiceTest {

    @Test
    fun `upload posts diagnostic logs to worker and parses counts`() {
        val service = DiagnosticLogUploadService(
            requestExecutor = { request ->
                assertEquals("https://worker.example.com/diagnostics/logs/batch", request.url)
                assertEquals("POST", request.method)
                assertEquals("Bearer token-123", request.headers["Authorization"])
                val payload = JSONObject(request.body.orEmpty())
                assertEquals("device-1", payload.getString("deviceId"))
                assertEquals(1, payload.getJSONArray("logs").length())
                UploadHttpResponse(200, """{"ok":true,"insertedCount":1,"dedupedCount":0}""")
            }
        )

        val result = service.upload(
            config = TrainingSampleUploadConfig("https://worker.example.com", "token-123"),
            appVersion = "1.0.23",
            deviceId = "device-1",
            logs = listOf(logEntry()),
        )

        assertTrue(result is DiagnosticLogUploadResult.Success)
        assertEquals(1, result.insertedCount)
        assertEquals(0, result.dedupedCount)
    }

    private fun logEntry(): DiagnosticLogEntry {
        return DiagnosticLogEntry(
            logId = "log-1",
            occurredAt = 1_700_000_000_000L,
            type = DiagnosticLogType.PERF_WARN,
            severity = DiagnosticLogSeverity.WARN,
            source = "RawPointUploadWorker",
            message = "raw upload slow",
            fingerprint = "raw-upload-slow",
            payloadJson = """{"durationMs":2800}""",
        )
    }
}
