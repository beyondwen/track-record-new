package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.UploadHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryUploadServiceTest {

    @Test
    fun `upload posts local histories to worker and parses accepted ids`() {
        val service = HistoryUploadService(
            requestExecutor = { request ->
                assertEquals("POST", request.method)
                assertEquals(
                    "https://worker.example.com/processed-histories/batch",
                    request.url,
                )
                assertEquals("Bearer token-123", request.headers["Authorization"])
                assertEquals("application/json", request.headers["Content-Type"])
                UploadHttpResponse(
                    statusCode = 200,
                    body = """{"ok":true,"insertedCount":1,"dedupedCount":0,"acceptedHistoryIds":[11]}""",
                )
            },
        )

        val result = service.upload(
            config = TrainingSampleUploadConfig(
                workerBaseUrl = "https://worker.example.com",
                uploadToken = "token-123",
            ),
            appVersion = "1.0.23",
            deviceId = "device-1",
            rows = listOf(
                HistoryUploadRow(
                    historyId = 11L,
                    timestampMillis = 1713456010000,
                    distanceKm = 1.23,
                    durationSeconds = 456,
                    averageSpeedKmh = 9.7,
                    title = "本地完整轨迹",
                    startSource = "AUTO",
                    stopSource = "AUTO",
                    manualStartAt = null,
                    manualStopAt = null,
                    points = emptyList(),
                )
            ),
        )

        assertTrue(result is HistoryUploadResult.Success)
        val success = result as HistoryUploadResult.Success
        assertEquals(listOf(11L), success.acceptedHistoryIds)
        assertEquals(1, success.insertedCount)
    }
}
