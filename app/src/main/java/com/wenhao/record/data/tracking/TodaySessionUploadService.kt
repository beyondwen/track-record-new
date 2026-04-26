package com.wenhao.record.data.tracking

import com.wenhao.record.data.history.executeWithHttpUrlConnection
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class TodaySessionUploadService(
    private val requestExecutor: (
        TrainingSampleUploadConfig,
        String,
        String,
        TodaySessionMetaUploadRow,
    ) -> UploadHttpResponse = { config, appVersion, deviceId, row ->
        val request = UploadHttpRequest(
            url = "${config.workerBaseUrl.trim().trimEnd('/')}/today-sessions/batch",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer ${config.uploadToken}",
                "Content-Type" to "application/json",
            ),
            body = JSONObject().apply {
                put("deviceId", deviceId)
                put("appVersion", appVersion)
                put(
                    "sessions",
                    JSONArray().put(
                        JSONObject().apply {
                            put("sessionId", row.sessionId)
                            put("dayStartMillis", row.dayStartMillis)
                            put("status", row.status)
                            put("startedAt", row.startedAt)
                            put("lastPointAt", row.lastPointAt)
                            put("endedAt", row.endedAt)
                            put("phase", row.phase)
                            put("updatedAt", row.updatedAt)
                        }
                    )
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
        session: TodaySessionMetaUploadRow,
    ): UploadHttpResponse {
        return try {
            requestExecutor(config, appVersion, deviceId, session)
        } catch (_: IOException) {
            UploadHttpResponse(500, "{\"ok\":false,\"message\":\"network\"}")
        } catch (_: Exception) {
            UploadHttpResponse(500, "{\"ok\":false,\"message\":\"error\"}")
        }
    }
}

data class TodaySessionMetaUploadRow(
    val sessionId: String,
    val dayStartMillis: Long,
    val status: String,
    val startedAt: Long,
    val lastPointAt: Long?,
    val endedAt: Long?,
    val phase: String?,
    val updatedAt: Long,
)
