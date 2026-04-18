package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.IOException

class HistoryUploadServiceTest {

    @Test
    fun `upload posts histories to worker and parses accepted ids`() {
        val service = HistoryUploadService(
            requestExecutor = { request ->
                assertEquals("https://worker.example.com/histories/batch", request.url)
                assertEquals("POST", request.method)
                assertEquals("Bearer token-123", request.headers["Authorization"])
                assertEquals("application/json", request.headers["Content-Type"])
                val payload = JSONObject(request.body)
                assertEquals("device-1", payload.getString("deviceId"))
                assertEquals("1.0.22", payload.getString("appVersion"))

                TrainingSampleUploadResponse(
                    statusCode = 200,
                    body = """{"ok":true,"insertedCount":1,"dedupedCount":0,"acceptedHistoryIds":[7,"bad",null,0]}"""
                )
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.22",
            deviceId = "device-1",
            rows = listOf(historyRow(historyId = 7L)),
        )

        assertTrue(result is HistoryUploadResult.Success)
        val success = result as HistoryUploadResult.Success
        assertEquals(listOf(7L), success.acceptedHistoryIds)
        assertEquals(1, success.insertedCount)
        assertEquals(0, success.dedupedCount)
    }

    @Test
    fun `upload returns auth failure message for unauthorized response`() {
        val service = HistoryUploadService(
            requestExecutor = {
                TrainingSampleUploadResponse(statusCode = 401, body = """{"ok":false,"message":"expired"}""")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.22",
            deviceId = "device-1",
            rows = listOf(historyRow(historyId = 7L)),
        )

        assertTrue(result is HistoryUploadResult.Failure)
        assertEquals("鉴权失败，请检查上传令牌", (result as HistoryUploadResult.Failure).message)
    }

    @Test
    fun `upload forwards failure response message`() {
        val service = HistoryUploadService(
            requestExecutor = {
                TrainingSampleUploadResponse(statusCode = 400, body = """{"ok":false,"message":"bad history payload"}""")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.22",
            deviceId = "device-1",
            rows = listOf(historyRow(historyId = 7L)),
        )

        assertTrue(result is HistoryUploadResult.Failure)
        assertEquals("bad history payload", (result as HistoryUploadResult.Failure).message)
    }

    @Test
    fun `upload returns failure result on network exception`() {
        val service = HistoryUploadService(
            requestExecutor = {
                throw IOException("timeout")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.22",
            deviceId = "device-1",
            rows = listOf(historyRow(historyId = 7L)),
        )

        assertTrue(result is HistoryUploadResult.Failure)
        assertEquals("上传失败，请检查网络后重试", (result as HistoryUploadResult.Failure).message)
    }

    private fun validConfig(): TrainingSampleUploadConfig {
        return TrainingSampleUploadConfig(
            workerBaseUrl = "https://worker.example.com",
            uploadToken = "token-123",
        )
    }

    private fun historyRow(historyId: Long): HistoryUploadRow {
        return HistoryUploadRow(
            historyId = historyId,
            timestampMillis = 123_000L,
            distanceKm = 1.23,
            durationSeconds = 456,
            averageSpeedKmh = 9.7,
            title = "通勤",
            startSource = "MANUAL",
            stopSource = "MANUAL",
            manualStartAt = 100_000L,
            manualStopAt = 180_000L,
            points = listOf(
                HistoryUploadPointRow(
                    latitude = 30.1,
                    longitude = 120.1,
                    timestampMillis = 123_456L,
                    accuracyMeters = 6.0,
                    altitudeMeters = 12.5,
                    wgs84Latitude = 30.09,
                    wgs84Longitude = 120.09,
                )
            ),
        )
    }
}
