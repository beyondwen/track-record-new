package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.UploadHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteHistoryReadServiceTest {

    @Test
    fun `loadDays requests summary endpoint with timezone offset and parses days`() {
        val service = RemoteHistoryReadService(
            requestExecutor = { request ->
                assertEquals("GET", request.method)
                assertEquals(
                    "https://worker.example.com/history-days?deviceId=device-1&utcOffsetMinutes=480",
                    request.url,
                )
                assertEquals("Bearer token-123", request.headers["Authorization"])
                UploadHttpResponse(
                    statusCode = 200,
                    body = """
                        {"ok":true,"days":[
                          {
                            "dayStartMillis":1713974400000,
                            "latestTimestamp":1714020000000,
                            "sessionCount":2,
                            "totalDistanceKm":12.3,
                            "totalDurationSeconds":3600,
                            "averageSpeedKmh":12.3,
                            "sourceIds":[8,7]
                          }
                        ]}
                    """.trimIndent(),
                )
            },
        )

        val result = service.loadDays(
            config = validConfig(),
            deviceId = "device-1",
            utcOffsetMinutes = 480,
        )

        assertTrue(result is RemoteHistoryDaySummaryReadResult.Success)
        val success = result as RemoteHistoryDaySummaryReadResult.Success
        assertEquals(listOf(1713974400000L), success.items.map { it.dayStartMillis })
        assertEquals(12.3, success.items.first().totalDistanceKm, 0.0001)
    }

    @Test
    fun `loadAll requests histories endpoint and parses items`() {
        val service = RemoteHistoryReadService(
            requestExecutor = { request ->
                assertEquals("GET", request.method)
                assertEquals(
                    "https://worker.example.com/processed-histories?deviceId=device-1",
                    request.url,
                )
                assertEquals("Bearer token-123", request.headers["Authorization"])
                UploadHttpResponse(
                    statusCode = 200,
                    body = """
                        {"ok":true,"histories":[
                          {
                            "historyId":7,
                            "timestampMillis":1713456010000,
                            "distanceKm":1.23,
                            "durationSeconds":456,
                            "averageSpeedKmh":9.7,
                            "title":"通勤",
                            "startSource":"MANUAL",
                            "stopSource":"AUTO",
                            "manualStartAt":1713456000000,
                            "manualStopAt":1713456005000,
                            "points":[
                              {
                                "latitude":30.1,
                                "longitude":120.1,
                                "timestampMillis":1713456010000,
                                "accuracyMeters":5.0,
                                "altitudeMeters":12.5,
                                "wgs84Latitude":30.09,
                                "wgs84Longitude":120.09
                              }
                            ]
                          }
                        ]}
                    """.trimIndent(),
                )
            },
        )

        val result = service.loadAll(
            config = validConfig(),
            deviceId = "device-1",
        )

        assertTrue(result is RemoteHistoryReadResult.Success)
        val success = result as RemoteHistoryReadResult.Success
        assertEquals(listOf(7L), success.histories.map { it.id })
        assertEquals("通勤", success.histories.first().title)
        assertEquals(1, success.histories.first().points.size)
    }

    @Test
    fun `loadByDay requests day endpoint and parses items`() {
        val service = RemoteHistoryReadService(
            requestExecutor = { request ->
                assertEquals("GET", request.method)
                assertEquals(
                    "https://worker.example.com/processed-histories/day?deviceId=device-1&dayStartMillis=1713398400000",
                    request.url,
                )
                UploadHttpResponse(
                    statusCode = 200,
                    body = """{"ok":true,"histories":[{"historyId":9,"timestampMillis":1713402000000,"distanceKm":3.2,"durationSeconds":600,"averageSpeedKmh":19.2,"points":[]}]}""",
                )
            },
        )

        val result = service.loadByDay(
            config = validConfig(),
            deviceId = "device-1",
            dayStartMillis = 1713398400000,
        )

        assertTrue(result is RemoteHistoryReadResult.Success)
        assertEquals(listOf(9L), (result as RemoteHistoryReadResult.Success).histories.map { it.id })
    }

    @Test
    fun `deleteByDay requests delete endpoint and returns success`() {
        val service = RemoteHistoryReadService(
            requestExecutor = { request ->
                assertEquals("DELETE", request.method)
                assertEquals(
                    "https://worker.example.com/processed-histories/day?deviceId=device-1&dayStartMillis=1713398400000",
                    request.url,
                )
                assertEquals("Bearer token-123", request.headers["Authorization"])
                UploadHttpResponse(
                    statusCode = 200,
                    body = """{"ok":true,"message":"deleted"}""",
                )
            },
        )

        val result = service.deleteByDay(
            config = validConfig(),
            deviceId = "device-1",
            dayStartMillis = 1713398400000,
        )

        assertTrue(result is RemoteHistoryMutationResult.Success)
    }

    @Test
    fun `loadAll returns failure on network exception`() {
        val service = RemoteHistoryReadService(
            requestExecutor = {
                throw java.io.IOException("timeout")
            },
        )

        val result = service.loadAll(
            config = validConfig(),
            deviceId = "device-1",
        )

        assertTrue(result is RemoteHistoryReadResult.Failure)
        assertEquals("读取历史失败，请检查网络后重试", (result as RemoteHistoryReadResult.Failure).message)
    }

    private fun validConfig(): TrainingSampleUploadConfig {
        return TrainingSampleUploadConfig(
            workerBaseUrl = "https://worker.example.com",
            uploadToken = "token-123",
        )
    }
}
