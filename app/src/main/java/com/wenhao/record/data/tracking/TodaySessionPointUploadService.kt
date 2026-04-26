package com.wenhao.record.data.tracking

import com.wenhao.record.data.history.HistoryDayAggregator
import com.wenhao.record.data.history.executeWithHttpUrlConnection
import com.wenhao.record.data.local.stream.TodaySessionPointEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class TodaySessionPointUploadService(
    private val requestExecutor: (
        TrainingSampleUploadConfig,
        String,
        String,
        List<TodaySessionPointEntity>,
    ) -> UploadHttpResponse = { config, appVersion, deviceId, points ->
        val request = UploadHttpRequest(
            url = "${config.workerBaseUrl.trim().trimEnd('/')}/today-session-points/batch",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer ${config.uploadToken}",
                "Content-Type" to "application/json",
            ),
            body = JSONObject().apply {
                put("deviceId", deviceId)
                put("appVersion", appVersion)
                put(
                    "points",
                    JSONArray().apply {
                        points.forEach { point ->
                            put(
                                JSONObject().apply {
                                    put("sessionId", point.sessionId)
                                    put("pointId", point.pointId)
                                    put("dayStartMillis", HistoryDayAggregator.startOfDay(point.timestampMillis))
                                    put("timestampMillis", point.timestampMillis)
                                    put("latitude", point.latitude)
                                    put("longitude", point.longitude)
                                    put("accuracyMeters", point.accuracyMeters)
                                    put("altitudeMeters", point.altitudeMeters)
                                    put("speedMetersPerSecond", point.speedMetersPerSecond)
                                    put("provider", point.provider)
                                    put("samplingTier", point.samplingTier)
                                    put("updatedAt", point.updatedAt)
                                }
                            )
                        }
                    }
                )
            }.toString(),
        )
        executeWithHttpUrlConnection(request)
    },
) {
    fun upload(
        config: TrainingSampleUploadConfig,
        appVersion: String,
        deviceId: String,
        points: List<TodaySessionPointEntity>,
    ): UploadHttpResponse {
        return try {
            requestExecutor(config, appVersion, deviceId, points)
        } catch (_: IOException) {
            UploadHttpResponse(500, "{\"ok\":false,\"message\":\"network\"}")
        } catch (_: Exception) {
            UploadHttpResponse(500, "{\"ok\":false,\"message\":\"error\"}")
        }
    }
}
