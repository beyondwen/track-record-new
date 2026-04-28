package com.wenhao.record.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteRawPointReadServiceTest {

    @Test
    fun `loadDays requests raw point days endpoint and parses days`() {
        val service = RemoteRawPointReadService(
            requestExecutor = { request ->
                assertEquals("GET", request.method)
                assertEquals(
                    "https://worker.example.com/raw-points/days?deviceId=device-1&utcOffsetMinutes=480",
                    request.url,
                )
                UploadHttpResponse(
                    statusCode = 200,
                    body = """
                        {"ok":true,"days":[
                          {
                            "dayStartMillis":1713398400000,
                            "firstPointAt":1713398460000,
                            "lastPointAt":1713402000000,
                            "pointCount":123,
                            "maxPointId":456,
                            "totalDistanceKm":12.5,
                            "totalDurationSeconds":3540,
                            "averageSpeedKmh":12.7
                          }
                        ]}
                    """.trimIndent(),
                )
            },
        )

        val result = service.loadDays(
            config = TrainingSampleUploadConfig(
                workerBaseUrl = "https://worker.example.com",
                uploadToken = "token-123",
            ),
            deviceId = "device-1",
            utcOffsetMinutes = 480,
        )

        assertTrue(result is RemoteRawPointDaySummaryReadResult.Success)
        val success = result as RemoteRawPointDaySummaryReadResult.Success
        assertEquals(listOf(1713398400000L), success.days.map { it.dayStartMillis })
        assertEquals(1713398460000L, success.days.first().firstPointAt)
        assertEquals(1713402000000L, success.days.first().lastPointAt)
        assertEquals(456L, success.days.first().maxPointId)
        assertEquals(12.5, success.days.first().totalDistanceKm, 0.0)
        assertEquals(3540, success.days.first().totalDurationSeconds)
        assertEquals(12.7, success.days.first().averageSpeedKmh, 0.0)
    }

    @Test
    fun `loadByDay requests raw point day endpoint and parses points`() {
        val service = RemoteRawPointReadService(
            requestExecutor = { request ->
                assertEquals("GET", request.method)
                assertEquals(
                    "https://worker.example.com/raw-points/day?deviceId=device-1&dayStartMillis=1713398400000",
                    request.url,
                )
                assertEquals("Bearer token-123", request.headers["Authorization"])
                UploadHttpResponse(
                    statusCode = 200,
                    body = """
                        {"ok":true,"points":[
                          {
                            "pointId":18,
                            "timestampMillis":1713398460000,
                            "latitude":30.1,
                            "longitude":120.1,
                            "accuracyMeters":5.0,
                            "altitudeMeters":12.5,
                            "speedMetersPerSecond":1.2,
                            "bearingDegrees":90.0,
                            "provider":"gps",
                            "sourceType":"LOCATION_MANAGER",
                            "isMock":false,
                            "wifiFingerprintDigest":"wifi",
                            "activityType":"WALKING",
                            "activityConfidence":0.9,
                            "samplingTier":"ACTIVE"
                          }
                        ]}
                    """.trimIndent(),
                )
            },
        )

        val result = service.loadByDay(
            config = TrainingSampleUploadConfig(
                workerBaseUrl = "https://worker.example.com",
                uploadToken = "token-123",
            ),
            deviceId = "device-1",
            dayStartMillis = 1713398400000,
        )

        assertTrue(result is RemoteRawPointReadResult.Success)
        val success = result as RemoteRawPointReadResult.Success
        assertEquals(listOf(18L), success.points.map { it.pointId })
        assertEquals(SamplingTier.ACTIVE, success.points.first().samplingTier)
    }
}
