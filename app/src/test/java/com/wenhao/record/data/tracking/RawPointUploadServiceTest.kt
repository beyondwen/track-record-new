package com.wenhao.record.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.IOException

class RawPointUploadServiceTest {

    @Test
    fun `upload posts raw points to worker and parses accepted max point id`() {
        val service = RawPointUploadService(
            requestExecutor = { request ->
                assertEquals("https://worker.example.com/raw-points/batch", request.url)
                assertEquals("POST", request.method)
                assertEquals("Bearer token-123", request.headers["Authorization"])
                assertEquals("application/json", request.headers["Content-Type"])
                val payload = JSONObject(request.body)
                assertEquals("device-1", payload.getString("deviceId"))
                assertEquals("1.0.23", payload.getString("appVersion"))

                TrainingSampleUploadResponse(
                    statusCode = 200,
                    body = """{"ok":true,"insertedCount":2,"dedupedCount":1,"acceptedMaxPointId":52}"""
                )
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.23",
            deviceId = "device-1",
            rows = listOf(rawPointRow(pointId = 52L)),
        )

        assertTrue(result is RawPointUploadResult.Success)
        val success = result as RawPointUploadResult.Success
        assertEquals(52L, success.acceptedMaxPointId)
        assertEquals(2, success.insertedCount)
        assertEquals(1, success.dedupedCount)
    }

    @Test
    fun `upload returns auth failure message for unauthorized response`() {
        val service = RawPointUploadService(
            requestExecutor = {
                TrainingSampleUploadResponse(statusCode = 401, body = """{"ok":false,"message":"expired"}""")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.23",
            deviceId = "device-1",
            rows = listOf(rawPointRow(pointId = 52L)),
        )

        assertTrue(result is RawPointUploadResult.Failure)
        assertEquals("鉴权失败，请检查上传令牌", (result as RawPointUploadResult.Failure).message)
    }

    @Test
    fun `upload returns failure result on network exception`() {
        val service = RawPointUploadService(
            requestExecutor = {
                throw IOException("timeout")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.23",
            deviceId = "device-1",
            rows = listOf(rawPointRow(pointId = 52L)),
        )

        assertTrue(result is RawPointUploadResult.Failure)
        assertEquals("上传失败，请检查网络后重试", (result as RawPointUploadResult.Failure).message)
    }

    private fun validConfig(): TrainingSampleUploadConfig {
        return TrainingSampleUploadConfig(
            workerBaseUrl = "https://worker.example.com",
            uploadToken = "token-123",
        )
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
