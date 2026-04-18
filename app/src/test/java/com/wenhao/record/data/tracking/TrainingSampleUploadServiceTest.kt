package com.wenhao.record.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.IOException

class TrainingSampleUploadServiceTest {

    @Test
    fun `upload parses accepted event ids from success response`() {
        val service = TrainingSampleUploadService(
            requestExecutor = { request ->
                assertEquals("https://worker.example.com/samples/batch", request.url)
                assertEquals("POST", request.method)
                assertEquals("Bearer token-123", request.headers["Authorization"])
                assertEquals("application/json", request.headers["Content-Type"])
                val payload = JSONObject(request.body)
                assertEquals("device-1", payload.getString("deviceId"))
                assertEquals("1.0.0", payload.getString("appVersion"))

                TrainingSampleUploadResponse(
                    statusCode = 200,
                    body = """{"ok":true,"insertedCount":1,"dedupedCount":0,"acceptedEventIds":[7,"bad",null,0]}"""
                )
            }
        )

        val result = service.upload(
            config = TrainingSampleUploadConfig(
                workerBaseUrl = "https://worker.example.com",
                uploadToken = "token-123",
            ),
            appVersion = "1.0.0",
            deviceId = "device-1",
            rows = listOf(sampleRow(eventId = 7L)),
        )

        assertTrue(result is TrainingSampleUploadResult.Success)
        val success = result as TrainingSampleUploadResult.Success
        assertEquals(listOf(7L), success.acceptedEventIds)
        assertEquals(1, success.insertedCount)
        assertEquals(0, success.dedupedCount)
    }

    @Test
    fun `upload returns auth failure message for unauthorized response`() {
        val service = TrainingSampleUploadService(
            requestExecutor = {
                TrainingSampleUploadResponse(statusCode = 401, body = """{"ok":false,"message":"token expired"}""")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.0",
            deviceId = "device-1",
            rows = listOf(sampleRow(eventId = 7L)),
        )

        assertTrue(result is TrainingSampleUploadResult.Failure)
        assertEquals("鉴权失败，请检查上传令牌", (result as TrainingSampleUploadResult.Failure).message)
    }

    @Test
    fun `upload returns auth failure message for forbidden response`() {
        val service = TrainingSampleUploadService(
            requestExecutor = {
                TrainingSampleUploadResponse(statusCode = 403, body = """{"ok":false,"message":"forbidden"}""")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.0",
            deviceId = "device-1",
            rows = listOf(sampleRow(eventId = 7L)),
        )

        assertTrue(result is TrainingSampleUploadResult.Failure)
        assertEquals("鉴权失败，请检查上传令牌", (result as TrainingSampleUploadResult.Failure).message)
    }

    @Test
    fun `upload forwards error message from failure response json`() {
        val service = TrainingSampleUploadService(
            requestExecutor = {
                TrainingSampleUploadResponse(statusCode = 400, body = """{"ok":false,"message":"bad payload"}""")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.0",
            deviceId = "device-1",
            rows = listOf(sampleRow(eventId = 7L)),
        )

        assertTrue(result is TrainingSampleUploadResult.Failure)
        assertEquals("bad payload", (result as TrainingSampleUploadResult.Failure).message)
    }

    @Test
    fun `upload returns failure when response ok flag is false with 2xx status`() {
        val service = TrainingSampleUploadService(
            requestExecutor = {
                TrainingSampleUploadResponse(statusCode = 200, body = """{"ok":false,"message":"server rejected"}""")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.0",
            deviceId = "device-1",
            rows = listOf(sampleRow(eventId = 7L)),
        )

        assertTrue(result is TrainingSampleUploadResult.Failure)
        assertEquals("server rejected", (result as TrainingSampleUploadResult.Failure).message)
    }

    @Test
    fun `upload returns generic failure when response body is empty with 2xx status`() {
        val service = TrainingSampleUploadService(
            requestExecutor = {
                TrainingSampleUploadResponse(statusCode = 200, body = "")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.0",
            deviceId = "device-1",
            rows = listOf(sampleRow(eventId = 7L)),
        )

        assertTrue(result is TrainingSampleUploadResult.Failure)
        assertEquals("上传失败，请稍后重试", (result as TrainingSampleUploadResult.Failure).message)
    }

    @Test
    fun `upload returns generic failure when response body is not json with 2xx status`() {
        val service = TrainingSampleUploadService(
            requestExecutor = {
                TrainingSampleUploadResponse(statusCode = 200, body = "not-json")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.0",
            deviceId = "device-1",
            rows = listOf(sampleRow(eventId = 7L)),
        )

        assertTrue(result is TrainingSampleUploadResult.Failure)
        assertEquals("上传失败，请稍后重试", (result as TrainingSampleUploadResult.Failure).message)
    }

    @Test
    fun `upload does not expose null message string to user`() {
        val service = TrainingSampleUploadService(
            requestExecutor = {
                TrainingSampleUploadResponse(statusCode = 400, body = """{"ok":false,"message":null}""")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.0",
            deviceId = "device-1",
            rows = listOf(sampleRow(eventId = 7L)),
        )

        assertTrue(result is TrainingSampleUploadResult.Failure)
        assertEquals("上传失败，请稍后重试", (result as TrainingSampleUploadResult.Failure).message)
    }

    @Test
    fun `upload returns failure result on network exception`() {
        val service = TrainingSampleUploadService(
            requestExecutor = {
                throw IOException("timeout")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.0",
            deviceId = "device-1",
            rows = listOf(sampleRow(eventId = 7L)),
        )

        assertTrue(result is TrainingSampleUploadResult.Failure)
        assertEquals("上传失败，请检查网络后重试", (result as TrainingSampleUploadResult.Failure).message)
    }

    private fun validConfig(): TrainingSampleUploadConfig {
        return TrainingSampleUploadConfig(
            workerBaseUrl = "https://worker.example.com",
            uploadToken = "token-123",
        )
    }

    private fun sampleRow(eventId: Long): TrainingSampleRow {
        return TrainingSampleRow(
            eventId = eventId,
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
            features = mapOf("steps_30s" to 4.0),
            feedbackLabel = "CORRECT",
            startSource = "MANUAL",
            stopSource = "MANUAL",
            manualStartAt = 100_000L,
            manualStopAt = 180_000L,
        )
    }
}
