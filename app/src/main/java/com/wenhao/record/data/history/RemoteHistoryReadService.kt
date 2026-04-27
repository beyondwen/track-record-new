package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.UploadHttpRequest
import com.wenhao.record.data.tracking.UploadHttpRequestExecutor
import com.wenhao.record.data.tracking.UploadHttpResponse
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.runtimeusage.RuntimeUsageModule
import com.wenhao.record.runtimeusage.RuntimeUsageRecorder
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.TimeZone

sealed interface RemoteHistoryReadResult {
    data class Success(
        val histories: List<HistoryItem>,
    ) : RemoteHistoryReadResult

    data class Failure(
        val message: String,
    ) : RemoteHistoryReadResult
}

sealed interface RemoteHistoryDaySummaryReadResult {
    data class Success(
        val items: List<HistoryDaySummaryItem>,
    ) : RemoteHistoryDaySummaryReadResult

    data class Failure(
        val message: String,
    ) : RemoteHistoryDaySummaryReadResult
}

sealed interface RemoteHistoryMutationResult {
    object Success : RemoteHistoryMutationResult

    data class Failure(
        val message: String,
    ) : RemoteHistoryMutationResult
}

class RemoteHistoryReadService(
    private val requestExecutor: UploadHttpRequestExecutor = ::executeWithHttpUrlConnection,
) {
    fun loadDays(
        config: TrainingSampleUploadConfig,
        deviceId: String,
        utcOffsetMinutes: Int = TimeZone.getDefault().getOffset(System.currentTimeMillis()).div(60_000),
    ): RemoteHistoryDaySummaryReadResult {
        RuntimeUsageRecorder.hit(RuntimeUsageModule.SERVICE_REMOTE_HISTORY_READ, "loadDays")
        return executeDaySummaryRead(
            url = buildString {
                append(config.workerBaseUrl.trim().trimEnd('/'))
                append("/history-days?deviceId=")
                append(deviceId.encodeUrlQuery())
                append("&utcOffsetMinutes=")
                append(utcOffsetMinutes)
            },
            token = config.uploadToken,
        )
    }

    fun loadAll(
        config: TrainingSampleUploadConfig,
        deviceId: String,
    ): RemoteHistoryReadResult {
        RuntimeUsageRecorder.hit(RuntimeUsageModule.SERVICE_REMOTE_HISTORY_READ, "loadAll")
        return executeRead(
            url = "${config.workerBaseUrl.trim().trimEnd('/')}/processed-histories?deviceId=${deviceId.encodeUrlQuery()}",
            token = config.uploadToken,
        )
    }

    fun loadByDay(
        config: TrainingSampleUploadConfig,
        deviceId: String,
        dayStartMillis: Long,
    ): RemoteHistoryReadResult {
        RuntimeUsageRecorder.hit(RuntimeUsageModule.SERVICE_REMOTE_HISTORY_READ, "loadByDay")
        return executeRead(
            url = buildString {
                append(config.workerBaseUrl.trim().trimEnd('/'))
                append("/processed-histories/day?deviceId=")
                append(deviceId.encodeUrlQuery())
                append("&dayStartMillis=")
                append(dayStartMillis)
            },
            token = config.uploadToken,
        )
    }

    fun deleteByDay(
        config: TrainingSampleUploadConfig,
        deviceId: String,
        dayStartMillis: Long,
    ): RemoteHistoryMutationResult {
        RuntimeUsageRecorder.hit(RuntimeUsageModule.SERVICE_REMOTE_HISTORY_READ, "deleteByDay")
        return executeMutation(
            method = "DELETE",
            url = buildString {
                append(config.workerBaseUrl.trim().trimEnd('/'))
                append("/processed-histories/day?deviceId=")
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
    ): RemoteHistoryReadResult {
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
            RemoteHistoryReadResult.Failure(REMOTE_HISTORY_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            RemoteHistoryReadResult.Failure(REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun executeDaySummaryRead(
        url: String,
        token: String,
    ): RemoteHistoryDaySummaryReadResult {
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
            RemoteHistoryDaySummaryReadResult.Failure(REMOTE_HISTORY_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            RemoteHistoryDaySummaryReadResult.Failure(REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun executeMutation(
        method: String,
        url: String,
        token: String,
    ): RemoteHistoryMutationResult {
        return try {
            val request = UploadHttpRequest(
                url = url,
                method = method,
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Accept" to "application/json",
                ),
            )
            parseMutationResponse(requestExecutor.invoke(request))
        } catch (_: IOException) {
            RemoteHistoryMutationResult.Failure(REMOTE_HISTORY_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            RemoteHistoryMutationResult.Failure(REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun parseResponse(response: UploadHttpResponse): RemoteHistoryReadResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return RemoteHistoryReadResult.Failure(REMOTE_HISTORY_AUTH_FAILURE_MESSAGE)
        }

        if (response.statusCode !in 200..299) {
            return RemoteHistoryReadResult.Failure(
                parseMessage(response.body) ?: REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE
            )
        }

        val json = parseJson(response.body)
            ?: return RemoteHistoryReadResult.Failure(REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return RemoteHistoryReadResult.Failure(
                parseMessage(json) ?: REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE
            )
        }

        return RemoteHistoryReadResult.Success(
            histories = parseHistories(json.optJSONArray("histories"))
        )
    }

    private fun parseDaySummaryResponse(response: UploadHttpResponse): RemoteHistoryDaySummaryReadResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return RemoteHistoryDaySummaryReadResult.Failure(REMOTE_HISTORY_AUTH_FAILURE_MESSAGE)
        }

        if (response.statusCode !in 200..299) {
            return RemoteHistoryDaySummaryReadResult.Failure(
                parseMessage(response.body) ?: REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE
            )
        }

        val json = parseJson(response.body)
            ?: return RemoteHistoryDaySummaryReadResult.Failure(REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return RemoteHistoryDaySummaryReadResult.Failure(
                parseMessage(json) ?: REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE
            )
        }

        return RemoteHistoryDaySummaryReadResult.Success(
            items = parseDaySummaries(json.optJSONArray("days"))
        )
    }

    private fun parseMutationResponse(response: UploadHttpResponse): RemoteHistoryMutationResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return RemoteHistoryMutationResult.Failure(REMOTE_HISTORY_AUTH_FAILURE_MESSAGE)
        }

        if (response.statusCode !in 200..299) {
            return RemoteHistoryMutationResult.Failure(
                parseMessage(response.body) ?: REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE
            )
        }

        val json = parseJson(response.body)
            ?: return RemoteHistoryMutationResult.Failure(REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return RemoteHistoryMutationResult.Failure(
                parseMessage(json) ?: REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE
            )
        }

        return RemoteHistoryMutationResult.Success
    }

    private fun parseHistories(items: JSONArray?): List<HistoryItem> {
        if (items == null) return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val historyId = item.optLongOrNull("historyId") ?: continue
                val timestampMillis = item.optLongOrNull("timestampMillis") ?: continue
                add(
                    HistoryItem(
                        id = historyId,
                        timestamp = timestampMillis,
                        distanceKm = item.optDouble("distanceKm"),
                        durationSeconds = item.optInt("durationSeconds"),
                        averageSpeedKmh = item.optDouble("averageSpeedKmh"),
                        title = item.optNullableString("title"),
                        points = parsePoints(item.optJSONArray("points")),
                        startSource = TrackRecordSource.fromStorage(item.optNullableString("startSource")),
                        stopSource = TrackRecordSource.fromStorage(item.optNullableString("stopSource")),
                        manualStartAt = item.optLongOrNull("manualStartAt"),
                        manualStopAt = item.optLongOrNull("manualStopAt"),
                    )
                )
            }
        }.sortedWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
    }

    private fun parsePoints(points: JSONArray?): List<TrackPoint> {
        if (points == null) return emptyList()
        return buildList {
            for (index in 0 until points.length()) {
                val point = points.optJSONObject(index) ?: continue
                val latitude = point.optFiniteDouble("latitude") ?: continue
                val longitude = point.optFiniteDouble("longitude") ?: continue
                add(
                    TrackPoint(
                        latitude = latitude,
                        longitude = longitude,
                        timestampMillis = point.optLongOrNull("timestampMillis") ?: 0L,
                        accuracyMeters = point.optFiniteDouble("accuracyMeters")?.toFloat(),
                        altitudeMeters = point.optFiniteDouble("altitudeMeters"),
                        wgs84Latitude = point.optFiniteDouble("wgs84Latitude"),
                        wgs84Longitude = point.optFiniteDouble("wgs84Longitude"),
                    )
                )
            }
        }
    }

    private fun parseDaySummaries(items: JSONArray?): List<HistoryDaySummaryItem> {
        if (items == null) return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val dayStartMillis = item.optLongOrNull("dayStartMillis") ?: continue
                val latestTimestamp = item.optLongOrNull("latestTimestamp") ?: continue
                val sessionCount = item.optLongOrNull("sessionCount")?.toInt() ?: continue
                add(
                    HistoryDaySummaryItem(
                        dayStartMillis = dayStartMillis,
                        latestTimestamp = latestTimestamp,
                        sessionCount = sessionCount,
                        totalDistanceKm = item.optFiniteDouble("totalDistanceKm") ?: 0.0,
                        totalDurationSeconds = item.optLongOrNull("totalDurationSeconds")?.toInt() ?: 0,
                        averageSpeedKmh = item.optFiniteDouble("averageSpeedKmh") ?: 0.0,
                        sourceIds = item.optLongList("sourceIds"),
                        routeTitle = item.optNullableString("routeTitle") ?: item.optNullableString("title"),
                    )
                )
            }
        }.sortedByDescending { it.dayStartMillis }
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

    private fun JSONObject.optLongList(key: String): List<Long> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                when (val value = array.opt(index)) {
                    is Number -> add(value.toLong())
                    is String -> value.toLongOrNull()?.let(::add)
                }
            }
        }
    }
}

private const val REMOTE_HISTORY_AUTH_FAILURE_MESSAGE = "鉴权失败，请检查上传令牌"
private const val REMOTE_HISTORY_NETWORK_FAILURE_MESSAGE = "读取历史失败，请检查网络后重试"
private const val REMOTE_HISTORY_GENERIC_FAILURE_MESSAGE = "读取历史失败，请稍后重试"
