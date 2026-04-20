package com.wenhao.record.data.tracking

import com.wenhao.record.data.history.executeWithHttpUrlConnection
import org.json.JSONObject
import java.io.IOException

sealed interface AnalysisUploadResult {
    data class Success(
        val acceptedMaxSegmentId: Long,
        val insertedCount: Int,
        val dedupedCount: Int,
    ) : AnalysisUploadResult

    data class Failure(
        val message: String,
    ) : AnalysisUploadResult
}

class AnalysisUploadService(
    private val requestExecutor: UploadHttpRequestExecutor = ::executeWithHttpUrlConnection,
) {
    fun upload(
        config: TrainingSampleUploadConfig,
        appVersion: String,
        deviceId: String,
        rows: List<AnalysisUploadRow>,
    ): AnalysisUploadResult {
        return try {
            val request = UploadHttpRequest(
                url = "${config.workerBaseUrl.trim().trimEnd('/')}/analysis/batch",
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer ${config.uploadToken}",
                    "Content-Type" to "application/json",
                ),
                body = AnalysisUploadPayloadCodec.encode(
                    deviceId = deviceId,
                    appVersion = appVersion,
                    rows = rows,
                ),
            )

            parseResponse(requestExecutor.invoke(request))
        } catch (_: IOException) {
            AnalysisUploadResult.Failure(ANALYSIS_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            AnalysisUploadResult.Failure(ANALYSIS_GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun parseResponse(response: UploadHttpResponse): AnalysisUploadResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return AnalysisUploadResult.Failure(ANALYSIS_AUTH_FAILURE_MESSAGE)
        }

        if (response.statusCode !in 200..299) {
            val errorMessage = parseMessage(response.body)
            return AnalysisUploadResult.Failure(errorMessage ?: ANALYSIS_GENERIC_FAILURE_MESSAGE)
        }

        val json = parseJson(response.body) ?: return AnalysisUploadResult.Failure(ANALYSIS_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return AnalysisUploadResult.Failure(
                parseMessage(json) ?: ANALYSIS_GENERIC_FAILURE_MESSAGE
            )
        }

        val acceptedMaxSegmentId = when (val raw = json.opt("acceptedMaxSegmentId")) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        } ?: 0L

        return AnalysisUploadResult.Success(
            acceptedMaxSegmentId = acceptedMaxSegmentId.coerceAtLeast(0L),
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

private const val ANALYSIS_AUTH_FAILURE_MESSAGE = "鉴权失败，请检查上传令牌"
private const val ANALYSIS_NETWORK_FAILURE_MESSAGE = "上传失败，请检查网络后重试"
private const val ANALYSIS_GENERIC_FAILURE_MESSAGE = "上传失败，请稍后重试"
