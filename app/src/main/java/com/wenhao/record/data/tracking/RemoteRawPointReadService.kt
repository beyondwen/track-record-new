package com.wenhao.record.data.tracking

import com.wenhao.record.data.history.executeWithHttpUrlConnection
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

sealed interface RemoteRawPointReadResult {
    data class Success(
        val points: List<RawTrackPoint>,
    ) : RemoteRawPointReadResult

    data class Failure(
        val message: String,
    ) : RemoteRawPointReadResult
}

data class RemoteRawPointDaySummary(
    val dayStartMillis: Long,
    val pointCount: Int,
    val maxPointId: Long,
)

sealed interface RemoteRawPointDaySummaryReadResult {
    data class Success(
        val days: List<RemoteRawPointDaySummary>,
    ) : RemoteRawPointDaySummaryReadResult

    data class Failure(
        val message: String,
    ) : RemoteRawPointDaySummaryReadResult
}

class RemoteRawPointReadService(
    private val requestExecutor: UploadHttpRequestExecutor = ::executeWithHttpUrlConnection,
) {
    fun loadDays(
        config: TrainingSampleUploadConfig,
        deviceId: String,
        utcOffsetMinutes: Int,
    ): RemoteRawPointDaySummaryReadResult {
        return executeDaySummaryRead(
            url = buildString {
                append(config.workerBaseUrl.trim().trimEnd('/'))
                append("/raw-points/days?deviceId=")
                append(deviceId.encodeUrlQuery())
                append("&utcOffsetMinutes=")
                append(utcOffsetMinutes)
            },
            token = config.uploadToken,
        )
    }

    fun loadByDay(
        config: TrainingSampleUploadConfig,
        deviceId: String,
        dayStartMillis: Long,
    ): RemoteRawPointReadResult {
        return executeRead(
            url = buildString {
                append(config.workerBaseUrl.trim().trimEnd('/'))
                append("/raw-points/day?deviceId=")
                append(deviceId.encodeUrlQuery())
                append("&dayStartMillis=")
                append(dayStartMillis)
            },
            token = config.uploadToken,
        )
    }

    private fun executeRead(
        url: String,
        token: String,
    ): RemoteRawPointReadResult {
        return try {
            val request = UploadHttpRequest(
                url = url,
                method = "GET",
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Accept" to "application/json",
                ),
            )
            parseResponse(requestExecutor.invoke(request))
        } catch (_: IOException) {
            RemoteRawPointReadResult.Failure(REMOTE_RAW_POINT_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            RemoteRawPointReadResult.Failure(REMOTE_RAW_POINT_GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun executeDaySummaryRead(
        url: String,
        token: String,
    ): RemoteRawPointDaySummaryReadResult {
        return try {
            val request = UploadHttpRequest(
                url = url,
                method = "GET",
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Accept" to "application/json",
                ),
            )
            parseDaySummaryResponse(requestExecutor.invoke(request))
        } catch (_: IOException) {
            RemoteRawPointDaySummaryReadResult.Failure(REMOTE_RAW_POINT_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            RemoteRawPointDaySummaryReadResult.Failure(REMOTE_RAW_POINT_GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun parseResponse(response: UploadHttpResponse): RemoteRawPointReadResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return RemoteRawPointReadResult.Failure(REMOTE_RAW_POINT_AUTH_FAILURE_MESSAGE)
        }

        if (response.statusCode !in 200..299) {
            return RemoteRawPointReadResult.Failure(
                parseMessage(response.body) ?: REMOTE_RAW_POINT_GENERIC_FAILURE_MESSAGE
            )
        }

        val json = parseJson(response.body)
            ?: return RemoteRawPointReadResult.Failure(REMOTE_RAW_POINT_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return RemoteRawPointReadResult.Failure(
                parseMessage(json) ?: REMOTE_RAW_POINT_GENERIC_FAILURE_MESSAGE
            )
        }

        return RemoteRawPointReadResult.Success(
            points = parsePoints(json.optJSONArray("points"))
        )
    }

    private fun parseDaySummaryResponse(response: UploadHttpResponse): RemoteRawPointDaySummaryReadResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return RemoteRawPointDaySummaryReadResult.Failure(REMOTE_RAW_POINT_AUTH_FAILURE_MESSAGE)
        }

        if (response.statusCode !in 200..299) {
            return RemoteRawPointDaySummaryReadResult.Failure(
                parseMessage(response.body) ?: REMOTE_RAW_POINT_GENERIC_FAILURE_MESSAGE
            )
        }

        val json = parseJson(response.body)
            ?: return RemoteRawPointDaySummaryReadResult.Failure(REMOTE_RAW_POINT_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return RemoteRawPointDaySummaryReadResult.Failure(
                parseMessage(json) ?: REMOTE_RAW_POINT_GENERIC_FAILURE_MESSAGE
            )
        }

        return RemoteRawPointDaySummaryReadResult.Success(
            days = parseDaySummaries(json.optJSONArray("days"))
        )
    }

    private fun parsePoints(items: JSONArray?): List<RawTrackPoint> {
        if (items == null) return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val pointId = item.optLongOrNull("pointId") ?: continue
                val timestampMillis = item.optLongOrNull("timestampMillis") ?: continue
                val latitude = item.optFiniteDouble("latitude") ?: continue
                val longitude = item.optFiniteDouble("longitude") ?: continue
                add(
                    RawTrackPoint(
                        pointId = pointId,
                        timestampMillis = timestampMillis,
                        latitude = latitude,
                        longitude = longitude,
                        accuracyMeters = item.optFiniteDouble("accuracyMeters")?.toFloat(),
                        altitudeMeters = item.optFiniteDouble("altitudeMeters"),
                        speedMetersPerSecond = item.optFiniteDouble("speedMetersPerSecond")?.toFloat(),
                        bearingDegrees = item.optFiniteDouble("bearingDegrees")?.toFloat(),
                        provider = item.optNullableString("provider").orEmpty(),
                        sourceType = item.optNullableString("sourceType").orEmpty(),
                        isMock = item.optBoolean("isMock", false),
                        wifiFingerprintDigest = item.optNullableString("wifiFingerprintDigest"),
                        activityType = item.optNullableString("activityType"),
                        activityConfidence = item.optFiniteDouble("activityConfidence")?.toFloat(),
                        samplingTier = item.optSamplingTier("samplingTier"),
                    )
                )
            }
        }
    }

    private fun parseDaySummaries(items: JSONArray?): List<RemoteRawPointDaySummary> {
        if (items == null) return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val dayStartMillis = item.optLongOrNull("dayStartMillis") ?: continue
                val pointCount = item.optLongOrNull("pointCount")?.toInt() ?: continue
                val maxPointId = item.optLongOrNull("maxPointId") ?: continue
                add(
                    RemoteRawPointDaySummary(
                        dayStartMillis = dayStartMillis,
                        pointCount = pointCount,
                        maxPointId = maxPointId,
                    )
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

    private fun JSONObject.optSamplingTier(key: String): SamplingTier {
        val rawValue = optNullableString(key)
        return rawValue?.let { value ->
            runCatching { SamplingTier.valueOf(value) }.getOrDefault(SamplingTier.IDLE)
        } ?: SamplingTier.IDLE
    }
}

private const val REMOTE_RAW_POINT_AUTH_FAILURE_MESSAGE = "鉴权失败，请检查上传令牌"
private const val REMOTE_RAW_POINT_NETWORK_FAILURE_MESSAGE = "读取点位失败，请检查网络后重试"
private const val REMOTE_RAW_POINT_GENERIC_FAILURE_MESSAGE = "读取点位失败，请稍后重试"
