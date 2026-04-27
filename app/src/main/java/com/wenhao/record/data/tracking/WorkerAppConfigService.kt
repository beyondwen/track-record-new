package com.wenhao.record.data.tracking

import com.wenhao.record.data.history.executeWithHttpUrlConnection
import com.wenhao.record.runtimeusage.RuntimeUsageModule
import com.wenhao.record.runtimeusage.RuntimeUsageRecorder
import org.json.JSONObject
import java.io.IOException

sealed interface WorkerAppConfigResult {
    data class Success(
        val mapboxPublicToken: String,
    ) : WorkerAppConfigResult

    data class Failure(
        val message: String,
    ) : WorkerAppConfigResult
}

class WorkerAppConfigService(
    private val requestExecutor: UploadHttpRequestExecutor = ::executeWithHttpUrlConnection,
) {
    fun load(config: TrainingSampleUploadConfig): WorkerAppConfigResult {
        RuntimeUsageRecorder.hit(RuntimeUsageModule.SERVICE_WORKER_APP_CONFIG)
        return try {
            val request = UploadHttpRequest(
                url = "${config.workerBaseUrl.trim().trimEnd('/')}/app-config",
                method = "GET",
                headers = mapOf(
                    "Authorization" to "Bearer ${config.uploadToken}",
                    "Accept" to "application/json",
                ),
            )
            parseResponse(requestExecutor.invoke(request))
        } catch (_: IOException) {
            WorkerAppConfigResult.Failure(WORKER_APP_CONFIG_NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            WorkerAppConfigResult.Failure(WORKER_APP_CONFIG_GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun parseResponse(response: UploadHttpResponse): WorkerAppConfigResult {
        if (isProtectedByCloudflareAccess(response)) {
            return WorkerAppConfigResult.Failure(WORKER_APP_CONFIG_CLOUDFLARE_ACCESS_MESSAGE)
        }

        if (response.statusCode == 401 || response.statusCode == 403) {
            return WorkerAppConfigResult.Failure(WORKER_APP_CONFIG_AUTH_FAILURE_MESSAGE)
        }

        if (response.statusCode !in 200..299) {
            return WorkerAppConfigResult.Failure(
                parseMessage(response.body) ?: WORKER_APP_CONFIG_GENERIC_FAILURE_MESSAGE
            )
        }

        val json = parseJson(response.body)
            ?: return WorkerAppConfigResult.Failure(WORKER_APP_CONFIG_GENERIC_FAILURE_MESSAGE)
        if (!json.optBoolean("ok", false)) {
            return WorkerAppConfigResult.Failure(
                parseMessage(json) ?: WORKER_APP_CONFIG_GENERIC_FAILURE_MESSAGE
            )
        }

        val token = json.optString("mapboxPublicToken").trim()
        if (token.isBlank()) {
            return WorkerAppConfigResult.Failure(WORKER_APP_CONFIG_EMPTY_TOKEN_MESSAGE)
        }
        return WorkerAppConfigResult.Success(mapboxPublicToken = token)
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

    private fun isProtectedByCloudflareAccess(response: UploadHttpResponse): Boolean {
        if (response.statusCode in 300..399) {
            return true
        }
        val normalizedBody = response.body.lowercase()
        return normalizedBody.contains("cloudflareaccess.com") ||
            normalizedBody.contains("/cdn-cgi/access/login") ||
            normalizedBody.contains("cloudflare access")
    }
}

private const val WORKER_APP_CONFIG_AUTH_FAILURE_MESSAGE = "鉴权失败，请检查上传令牌"
private const val WORKER_APP_CONFIG_NETWORK_FAILURE_MESSAGE = "读取 Mapbox 配置失败，请检查网络后重试"
private const val WORKER_APP_CONFIG_GENERIC_FAILURE_MESSAGE = "读取 Mapbox 配置失败，请稍后重试"
private const val WORKER_APP_CONFIG_EMPTY_TOKEN_MESSAGE = "Worker 未返回有效的 Mapbox Token"
private const val WORKER_APP_CONFIG_CLOUDFLARE_ACCESS_MESSAGE =
    "Worker 地址被 Cloudflare Access 拦截，App 无法直接读取 Mapbox Token"
