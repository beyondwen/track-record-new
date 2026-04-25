package com.wenhao.record.data.diagnostics

import com.wenhao.record.data.history.executeWithHttpUrlConnection
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.UploadHttpRequest
import com.wenhao.record.data.tracking.UploadHttpRequestExecutor
import com.wenhao.record.data.tracking.UploadHttpResponse
import org.json.JSONObject
import java.io.IOException

class DiagnosticLogUploadService(
    private val requestExecutor: UploadHttpRequestExecutor = ::executeWithHttpUrlConnection,
) {
    fun upload(
        config: TrainingSampleUploadConfig,
        appVersion: String,
        deviceId: String,
        logs: List<DiagnosticLogEntry>,
    ): DiagnosticLogUploadResult {
        if (logs.isEmpty()) return DiagnosticLogUploadResult.Success(insertedCount = 0, dedupedCount = 0)
        return try {
            val request = UploadHttpRequest(
                url = "${config.workerBaseUrl.trim().trimEnd('/')}/diagnostics/logs/batch",
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer ${config.uploadToken}",
                    "Content-Type" to "application/json",
                ),
                body = DiagnosticLogPayloadCodec.encode(
                    deviceId = deviceId,
                    appVersion = appVersion,
                    logs = logs,
                ),
            )
            parseResponse(requestExecutor.invoke(request))
        } catch (_: IOException) {
            DiagnosticLogUploadResult.Failure(DIAGNOSTIC_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            DiagnosticLogUploadResult.Failure(DIAGNOSTIC_GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun parseResponse(response: UploadHttpResponse): DiagnosticLogUploadResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return DiagnosticLogUploadResult.Failure(DIAGNOSTIC_AUTH_FAILURE_MESSAGE)
        }
        if (response.statusCode !in 200..299) {
            return DiagnosticLogUploadResult.Failure(parseMessage(response.body) ?: DIAGNOSTIC_GENERIC_FAILURE_MESSAGE)
        }
        val json = parseJson(response.body) ?: return DiagnosticLogUploadResult.Failure(DIAGNOSTIC_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return DiagnosticLogUploadResult.Failure(parseMessage(json) ?: DIAGNOSTIC_GENERIC_FAILURE_MESSAGE)
        }
        return DiagnosticLogUploadResult.Success(
            insertedCount = json.optInt("insertedCount", 0),
            dedupedCount = json.optInt("dedupedCount", 0),
        )
    }

    private fun parseMessage(body: String): String? {
        return parseJson(body)?.let(::parseMessage)
    }

    private fun parseMessage(json: JSONObject): String? {
        return (json.opt("message") as? String)?.trim()?.takeIf { it.isNotEmpty() }
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

private const val DIAGNOSTIC_AUTH_FAILURE_MESSAGE = "诊断日志鉴权失败，请检查上传令牌"
private const val DIAGNOSTIC_NETWORK_FAILURE_MESSAGE = "诊断日志上传失败，请检查网络后重试"
private const val DIAGNOSTIC_GENERIC_FAILURE_MESSAGE = "诊断日志上传失败，请稍后重试"
