package com.wenhao.record.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AnalysisUploadServiceTest {

    @Test
    fun `upload posts segments to analysis batch endpoint`() {
        val service = AnalysisUploadService(
            requestExecutor = { request ->
                assertEquals("https://worker.example.com/analysis/batch", request.url)
                assertEquals("POST", request.method)
                assertTrue(request.body.orEmpty().contains("\"segments\""))
                UploadHttpResponse(
                    statusCode = 200,
                    body = """{"ok":true,"insertedCount":2,"dedupedCount":1,"acceptedMaxSegmentId":52}"""
                )
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.23",
            deviceId = "device-1",
            rows = listOf(analysisRow(segmentId = 52L)),
        )

        assertTrue(result is AnalysisUploadResult.Success)
        val success = result as AnalysisUploadResult.Success
        assertEquals(52L, success.acceptedMaxSegmentId)
        assertEquals(2, success.insertedCount)
        assertEquals(1, success.dedupedCount)
    }

    @Test
    fun `upload returns auth failure message for unauthorized response`() {
        val service = AnalysisUploadService(
            requestExecutor = {
                UploadHttpResponse(statusCode = 403, body = """{"ok":false,"message":"forbidden"}""")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.23",
            deviceId = "device-1",
            rows = listOf(analysisRow(segmentId = 52L)),
        )

        assertTrue(result is AnalysisUploadResult.Failure)
        assertEquals("鉴权失败，请检查上传令牌", (result as AnalysisUploadResult.Failure).message)
    }

    @Test
    fun `upload returns failure result on network exception`() {
        val service = AnalysisUploadService(
            requestExecutor = {
                throw IOException("timeout")
            }
        )

        val result = service.upload(
            config = validConfig(),
            appVersion = "1.0.23",
            deviceId = "device-1",
            rows = listOf(analysisRow(segmentId = 52L)),
        )

        assertTrue(result is AnalysisUploadResult.Failure)
        assertEquals("上传失败，请检查网络后重试", (result as AnalysisUploadResult.Failure).message)
    }

    private fun validConfig(): TrainingSampleUploadConfig {
        return TrainingSampleUploadConfig(
            workerBaseUrl = "https://worker.example.com",
            uploadToken = "token-123",
        )
    }

    private fun analysisRow(segmentId: Long): AnalysisUploadRow {
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
                    stayId = 201L,
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
