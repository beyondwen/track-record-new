package com.wenhao.record.data.tracking

import com.wenhao.record.data.history.executeWithHttpUrlConnection
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

sealed interface RemoteTodaySessionReadResult {
    data class Success(
        val snapshot: RemoteTodaySessionSnapshot,
    ) : RemoteTodaySessionReadResult

    data class Failure(
        val message: String,
    ) : RemoteTodaySessionReadResult
}

data class RemoteTodaySessionSnapshot(
    val session: RemoteTodaySessionRecord?,
    val points: List<RemoteTodaySessionPoint>,
)

data class RemoteTodaySessionRecord(
    val sessionId: String,
    val dayStartMillis: Long,
    val status: String,
    val startedAt: Long,
    val lastPointAt: Long?,
    val endedAt: Long?,
    val phase: String,
    val updatedAt: Long,
)

data class RemoteTodaySessionPoint(
    val sessionId: String,
    val pointId: Long,
    val dayStartMillis: Long,
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Double?,
    val provider: String,
    val samplingTier: String,
    val updatedAt: Long,
)

class TodaySessionRemoteReadService(
    private val requestExecutor: UploadHttpRequestExecutor = ::executeWithHttpUrlConnection,
) {
    fun loadOpenSession(
        config: TrainingSampleUploadConfig,
        deviceId: String,
    ): RemoteTodaySessionReadResult {
        return try {
            val request = UploadHttpRequest(
                url = buildString {
                    append(config.workerBaseUrl.trim().trimEnd('/'))
                    append("/today-sessions/open?deviceId=")
                    append(deviceId.encodeUrlQuery())
                },
                method = "GET",
                headers = mapOf(
                    "Authorization" to "Bearer ${config.uploadToken}",
                    "Accept" to "application/json",
                ),
            )
            parseResponse(requestExecutor.invoke(request))
        } catch (_: IOException) {
            RemoteTodaySessionReadResult.Failure(REMOTE_TODAY_SESSION_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            RemoteTodaySessionReadResult.Failure(REMOTE_TODAY_SESSION_GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun parseResponse(response: UploadHttpResponse): RemoteTodaySessionReadResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return RemoteTodaySessionReadResult.Failure(REMOTE_TODAY_SESSION_AUTH_FAILURE_MESSAGE)
        }
        if (response.statusCode !in 200..299) {
            return RemoteTodaySessionReadResult.Failure(
                parseMessage(response.body) ?: REMOTE_TODAY_SESSION_GENERIC_FAILURE_MESSAGE,
            )
        }

        val json = parseJson(response.body)
            ?: return RemoteTodaySessionReadResult.Failure(REMOTE_TODAY_SESSION_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return RemoteTodaySessionReadResult.Failure(
                parseMessage(json) ?: REMOTE_TODAY_SESSION_GENERIC_FAILURE_MESSAGE,
            )
        }

        return RemoteTodaySessionReadResult.Success(
            snapshot = RemoteTodaySessionSnapshot(
                session = parseSession(json.optJSONObject("session")),
                points = parsePoints(json.optJSONArray("points")),
            ),
        )
    }

    private fun parseSession(item: JSONObject?): RemoteTodaySessionRecord? {
        item ?: return null
        val sessionId = item.optNullableString("sessionId") ?: return null
        val dayStartMillis = item.optLongOrNull("dayStartMillis") ?: return null
        val status = item.optNullableString("status") ?: return null
        val startedAt = item.optLongOrNull("startedAt") ?: return null
        val phase = item.optNullableString("phase") ?: return null
        val updatedAt = item.optLongOrNull("updatedAt") ?: return null
        return RemoteTodaySessionRecord(
            sessionId = sessionId,
            dayStartMillis = dayStartMillis,
            status = status,
            startedAt = startedAt,
            lastPointAt = item.optLongOrNull("lastPointAt"),
            endedAt = item.optLongOrNull("endedAt"),
            phase = phase,
            updatedAt = updatedAt,
        )
    }

    private fun parsePoints(items: JSONArray?): List<RemoteTodaySessionPoint> {
        if (items == null) return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val sessionId = item.optNullableString("sessionId") ?: continue
                val pointId = item.optLongOrNull("pointId") ?: continue
                val dayStartMillis = item.optLongOrNull("dayStartMillis") ?: continue
                val timestampMillis = item.optLongOrNull("timestampMillis") ?: continue
                val latitude = item.optFiniteDouble("latitude") ?: continue
                val longitude = item.optFiniteDouble("longitude") ?: continue
                val provider = item.optNullableString("provider") ?: continue
                val samplingTier = item.optNullableString("samplingTier") ?: continue
                val updatedAt = item.optLongOrNull("updatedAt") ?: continue
                add(
                    RemoteTodaySessionPoint(
                        sessionId = sessionId,
                        pointId = pointId,
                        dayStartMillis = dayStartMillis,
                        timestampMillis = timestampMillis,
                        latitude = latitude,
                        longitude = longitude,
                        accuracyMeters = item.optFiniteDouble("accuracyMeters"),
                        altitudeMeters = item.optFiniteDouble("altitudeMeters"),
                        speedMetersPerSecond = item.optFiniteDouble("speedMetersPerSecond"),
                        provider = provider,
                        samplingTier = samplingTier,
                        updatedAt = updatedAt,
                    ),
                )
            }
        }
    }

    private fun parseMessage(body: String): String? {
        return try {
            parseJson(body)?.let(::parseMessage)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMessage(json: JSONObject): String? {
        val value = json.opt("message")
        return (value as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun parseJson(body: String): JSONObject? {
        if (body.isBlank()) return null
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun String.encodeUrlQuery(): String {
        return URLEncoder.encode(this, Charsets.UTF_8.name())
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return if (has(key) && !isNull(key)) {
            optString(key).trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        return if (has(key) && !isNull(key)) {
            when (val value = opt(key)) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
        } else {
            null
        }
    }

    private fun JSONObject.optFiniteDouble(key: String): Double? {
        return if (has(key) && !isNull(key)) {
            when (val value = opt(key)) {
                is Number -> value.toDouble().takeIf { it.isFinite() }
                is String -> value.toDoubleOrNull()?.takeIf { it.isFinite() }
                else -> null
            }
        } else {
            null
        }
    }
}

private const val REMOTE_TODAY_SESSION_AUTH_FAILURE_MESSAGE = "鉴权失败，请检查上传令牌"
private const val REMOTE_TODAY_SESSION_NETWORK_FAILURE_MESSAGE = "读取今日会话失败，请检查网络后重试"
private const val REMOTE_TODAY_SESSION_GENERIC_FAILURE_MESSAGE = "读取今日会话失败，请稍后重试"
