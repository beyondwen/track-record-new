package com.wenhao.record.data.tracking

import org.json.JSONObject
import java.io.IOException

sealed interface RawPointUploadResult {
    data class Success(
        val acceptedMaxPointId: Long,
        val insertedCount: Int,
        val dedupedCount: Int,
    ) : RawPointUploadResult

    data class Failure(
        val message: String,
    ) : RawPointUploadResult
}

class RawPointUploadService(
    private val requestExecutor: TrainingSampleUploadRequestExecutor = ::executeWithHttpUrlConnection,
) {
    fun upload(
        config: TrainingSampleUploadConfig,
        appVersion: String,
        deviceId: String,
        rows: List<RawPointUploadRow>,
    ): RawPointUploadResult {
        return try {
            val request = TrainingSampleUploadRequest(
                url = "${config.workerBaseUrl.trim().trimEnd('/')}/raw-points/batch",
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer ${config.uploadToken}",
                    "Content-Type" to "application/json",
                ),
                body = RawPointUploadPayloadCodec.encode(
                    deviceId = deviceId,
                    appVersion = appVersion,
                    rows = rows,
                ),
            )

            parseResponse(requestExecutor.invoke(request))
        } catch (_: IOException) {
            RawPointUploadResult.Failure(RAW_POINT_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            RawPointUploadResult.Failure(RAW_POINT_GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun parseResponse(response: TrainingSampleUploadResponse): RawPointUploadResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return RawPointUploadResult.Failure(RAW_POINT_AUTH_FAILURE_MESSAGE)
        }

        if (response.statusCode !in 200..299) {
            val errorMessage = parseMessage(response.body)
            return RawPointUploadResult.Failure(errorMessage ?: RAW_POINT_GENERIC_FAILURE_MESSAGE)
        }

        val json = parseJson(response.body) ?: return RawPointUploadResult.Failure(RAW_POINT_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return RawPointUploadResult.Failure(
                parseMessage(json) ?: RAW_POINT_GENERIC_FAILURE_MESSAGE
            )
        }

        val acceptedMaxPointId = when (val raw = json.opt("acceptedMaxPointId")) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        } ?: 0L

        return RawPointUploadResult.Success(
            acceptedMaxPointId = acceptedMaxPointId.coerceAtLeast(0L),
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

private const val RAW_POINT_AUTH_FAILURE_MESSAGE = "鉴权失败，请检查上传令牌"
private const val RAW_POINT_NETWORK_FAILURE_MESSAGE = "上传失败，请检查网络后重试"
private const val RAW_POINT_GENERIC_FAILURE_MESSAGE = "上传失败，请稍后重试"
