package com.wenhao.record.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySessionRemoteReadServiceTest {

    @Test
    fun `loadOpenSession requests open endpoint and parses snapshot`() {
        val service = TodaySessionRemoteReadService(
            requestExecutor = { request ->
                assertEquals("GET", request.method)
                assertEquals(
                    "https://worker.example.com/today-sessions/open?deviceId=device-1",
                    request.url,
                )
                assertEquals("Bearer token-123", request.headers["Authorization"])
                UploadHttpResponse(
                    statusCode = 200,
                    body = """
                        {"ok":true,
                          "session":{
                            "sessionId":"session_1",
                            "dayStartMillis":1714300800000,
                            "status":"ACTIVE",
                            "startedAt":1714300900000,
                            "lastPointAt":1714300910000,
                            "endedAt":null,
                            "phase":"ACTIVE",
                            "updatedAt":1714300910000
                          },
                          "points":[
                            {
                              "sessionId":"session_1",
                              "pointId":18,
                              "dayStartMillis":1714300800000,
                              "timestampMillis":1714300910000,
                              "latitude":30.1,
                              "longitude":120.1,
                              "accuracyMeters":8.0,
                              "altitudeMeters":12.0,
                              "speedMetersPerSecond":1.2,
                              "provider":"gps",
                              "samplingTier":"ACTIVE",
                              "updatedAt":1714300910000
                            }
                          ]}
                    """.trimIndent(),
                )
            },
        )

        val result = service.loadOpenSession(
            config = TrainingSampleUploadConfig(
                workerBaseUrl = "https://worker.example.com",
                uploadToken = "token-123",
            ),
            deviceId = "device-1",
        )

        assertTrue(result is RemoteTodaySessionReadResult.Success)
        val success = result as RemoteTodaySessionReadResult.Success
        assertEquals("session_1", success.snapshot.session?.sessionId)
        assertEquals(listOf(18L), success.snapshot.points.map { it.pointId })
    }
}
