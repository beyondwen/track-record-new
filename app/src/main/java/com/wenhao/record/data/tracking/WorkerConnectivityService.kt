package com.wenhao.record.data.tracking

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface WorkerConnectivityResult {
    data class Reachable(
        val message: String,
    ) : WorkerConnectivityResult

    data class Unreachable(
        val message: String,
    ) : WorkerConnectivityResult
}

data class WorkerConnectivityRequest(
    val url: String,
    val method: String,
)

data class WorkerConnectivityResponse(
    val statusCode: Int,
    val body: String,
)

typealias WorkerConnectivityRequestExecutor = (WorkerConnectivityRequest) -> WorkerConnectivityResponse

class WorkerConnectivityService(
    private val requestExecutor: WorkerConnectivityRequestExecutor = ::executeWorkerConnectivityRequest,
) {
    fun check(workerBaseUrl: String): WorkerConnectivityResult {
        return try {
            val response = requestExecutor(
                WorkerConnectivityRequest(
                    url = "${workerBaseUrl.trim().trimEnd('/')}/health",
                    method = "GET",
                )
            )
            parseResponse(response)
        } catch (error: IOException) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            WorkerConnectivityResult.Unreachable("无法连接 Worker：$detail")
        } catch (_: Exception) {
            WorkerConnectivityResult.Unreachable("无法连接 Worker：请求异常")
        }
    }

    private fun parseResponse(response: WorkerConnectivityResponse): WorkerConnectivityResult {
        val message = when (response.statusCode) {
            200 -> "Worker 可达，健康检查正常（返回 200）"
            401, 403 -> "Worker 可达，但健康检查不该要求鉴权（返回 ${response.statusCode}）"
            404 -> "Worker 可达，但接口路径不对（返回 404）"
            in 500..599 -> "Worker 可达，但服务端异常（返回 ${response.statusCode}）"
            else -> "Worker 可达（返回 ${response.statusCode}）"
        }
        return WorkerConnectivityResult.Reachable(message)
    }
}

private fun executeWorkerConnectivityRequest(
    request: WorkerConnectivityRequest,
): WorkerConnectivityResponse {
    val connection = URL(request.url).openConnection() as HttpURLConnection
    connection.requestMethod = request.method
    connection.connectTimeout = 8000
    connection.readTimeout = 8000
    connection.instanceFollowRedirects = true

    return try {
        val responseCode = connection.responseCode
        val responseBody = if (responseCode in 200..299) {
            connection.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        WorkerConnectivityResponse(
            statusCode = responseCode,
            body = responseBody,
        )
    } finally {
        connection.disconnect()
    }
}
