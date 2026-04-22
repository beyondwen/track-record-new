package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.UploadHttpRequest
import com.wenhao.record.data.tracking.UploadHttpRequestExecutor
import com.wenhao.record.data.tracking.UploadHttpResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

sealed interface HistoryUploadResult {
    data class Success(
        val acceptedHistoryIds: List<Long>,
        val insertedCount: Int,
        val dedupedCount: Int,
    ) : HistoryUploadResult

    data class Failure(
        val message: String,
    ) : HistoryUploadResult
}

class HistoryUploadService(
    private val requestExecutor: UploadHttpRequestExecutor,
) {
    constructor() : this(::executeWithHttpUrlConnection)

    fun upload(
        config: TrainingSampleUploadConfig,
        appVersion: String,
        deviceId: String,
        rows: List<HistoryUploadRow>,
    ): HistoryUploadResult {
        return try {
            val request = UploadHttpRequest(
                url = "${config.workerBaseUrl.trim().trimEnd('/')}/histories/batch",
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer ${config.uploadToken}",
                    "Content-Type" to "application/json",
                ),
                body = HistoryUploadPayloadCodec.encode(
                    deviceId = deviceId,
                    appVersion = appVersion,
                    rows = rows,
                ),
            )

            val response = requestExecutor.invoke(request)
            parseHistoryUploadResponse(response)
        } catch (_: IOException) {
            HistoryUploadResult.Failure(HISTORY_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            HistoryUploadResult.Failure(HISTORY_GENERIC_FAILURE_MESSAGE)
        }
    }
}

internal fun parseHistoryUploadResponse(response: UploadHttpResponse): HistoryUploadResult {
    if (response.statusCode == 401 || response.statusCode == 403) {
        return HistoryUploadResult.Failure(HISTORY_AUTH_FAILURE_MESSAGE)
    }

    if (response.statusCode !in 200..299) {
        val errorMessage = parseHistoryUploadMessage(response.body)
        return HistoryUploadResult.Failure(errorMessage ?: HISTORY_GENERIC_FAILURE_MESSAGE)
    }

    val json = parseHistoryUploadJson(response.body)
        ?: return HistoryUploadResult.Failure(HISTORY_GENERIC_FAILURE_MESSAGE)
    if (!json.optBoolean("ok", false)) {
        return HistoryUploadResult.Failure(
            parseHistoryUploadMessage(json) ?: HISTORY_GENERIC_FAILURE_MESSAGE
        )
    }

    return HistoryUploadResult.Success(
        acceptedHistoryIds = parseAcceptedHistoryIds(json.optJSONArray("acceptedHistoryIds")),
        insertedCount = json.optInt("insertedCount", 0),
        dedupedCount = json.optInt("dedupedCount", 0),
    )
}

private fun parseHistoryUploadMessage(body: String): String? {
    return try {
        parseHistoryUploadJson(body)?.let(::parseHistoryUploadMessage)
    } catch (_: Exception) {
        null
    }
}

private fun parseHistoryUploadMessage(json: JSONObject): String? {
    val value = json.opt("message")
    return (value as? String)?.trim()?.takeIf { it.isNotEmpty() }
}

private fun parseHistoryUploadJson(body: String): JSONObject? {
    if (body.isBlank()) return null
    return try {
        JSONObject(body)
    } catch (_: Exception) {
        null
    }
}

private fun parseAcceptedHistoryIds(ids: JSONArray?): List<Long> {
    if (ids == null) return emptyList()
    return buildList {
        for (index in 0 until ids.length()) {
            val parsedId = when (val raw = ids.opt(index)) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
            if (parsedId != null && parsedId > 0L) {
                add(parsedId)
            }
        }
    }
}

private const val HISTORY_AUTH_FAILURE_MESSAGE = "鉴权失败，请检查上传令牌"
private const val HISTORY_NETWORK_FAILURE_MESSAGE = "上传失败，请检查网络后重试"
private const val HISTORY_GENERIC_FAILURE_MESSAGE = "上传失败，请稍后重试"
