package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.UploadHttpRequest
import com.wenhao.record.data.tracking.UploadHttpRequestExecutor
import com.wenhao.record.data.tracking.UploadHttpResponse
import org.json.JSONObject
import java.io.IOException
import java.util.TimeZone

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
            val utcOffsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()).div(60_000)
            val request = UploadHttpRequest(
                url = "${config.workerBaseUrl.trim().trimEnd('/')}/processed-histories/batch",
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer ${config.uploadToken}",
                    "Content-Type" to "application/json",
                ),
                body = HistoryUploadPayloadCodec.encode(
                    deviceId = deviceId,
                    appVersion = appVersion,
                    utcOffsetMinutes = utcOffsetMinutes,
                    rows = rows,
                ),
            )

            parseResponse(requestExecutor.invoke(request))
        } catch (_: IOException) {
            HistoryUploadResult.Failure("上传失败，请检查网络后重试")
        } catch (_: Exception) {
            HistoryUploadResult.Failure("上传失败，请稍后重试")
        }
    }

    private fun parseResponse(response: UploadHttpResponse): HistoryUploadResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return HistoryUploadResult.Failure(HISTORY_AUTH_FAILURE_MESSAGE)
        }

        if (response.statusCode !in 200..299) {
            return HistoryUploadResult.Failure(parseMessage(response.body) ?: HISTORY_GENERIC_FAILURE_MESSAGE)
        }

        val json = parseJson(response.body) ?: return HistoryUploadResult.Failure(HISTORY_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return HistoryUploadResult.Failure(parseMessage(json) ?: HISTORY_GENERIC_FAILURE_MESSAGE)
        }

        val acceptedHistoryIds = json.optJSONArray("acceptedHistoryIds")
            ?.let { ids ->
                buildList {
                    for (index in 0 until ids.length()) {
                        val value = ids.opt(index)
                        when (value) {
                            is Number -> add(value.toLong())
                            is String -> value.toLongOrNull()?.let(::add)
                        }
                    }
                }
            }
            .orEmpty()

        return HistoryUploadResult.Success(
            acceptedHistoryIds = acceptedHistoryIds,
            insertedCount = json.optInt("insertedCount", 0),
            dedupedCount = json.optInt("dedupedCount", 0),
        )
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
}

private const val HISTORY_AUTH_FAILURE_MESSAGE = "鉴权失败，请检查上传令牌"
private const val HISTORY_GENERIC_FAILURE_MESSAGE = "上传失败，请稍后重试"
