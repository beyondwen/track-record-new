package com.wenhao.record.data.tracking

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface TrainingSampleUploadResult {
    data class Success(
        val acceptedEventIds: List<Long>,
        val insertedCount: Int,
        val dedupedCount: Int,
    ) : TrainingSampleUploadResult

    data class Failure(
        val message: String,
    ) : TrainingSampleUploadResult
}

data class TrainingSampleUploadRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String,
)

data class TrainingSampleUploadResponse(
    val statusCode: Int,
    val body: String,
)

typealias TrainingSampleUploadRequestExecutor = (TrainingSampleUploadRequest) -> TrainingSampleUploadResponse

class TrainingSampleUploadService(
    private val requestExecutor: TrainingSampleUploadRequestExecutor = ::executeWithHttpUrlConnection,
) {
    fun upload(
        config: TrainingSampleUploadConfig,
        appVersion: String,
        deviceId: String,
        rows: List<TrainingSampleRow>,
    ): TrainingSampleUploadResult {
        return try {
            val request = TrainingSampleUploadRequest(
                url = "${config.workerBaseUrl.trim().trimEnd('/')}/samples/batch",
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer ${config.uploadToken}",
                    "Content-Type" to "application/json",
                ),
                body = TrainingSampleUploadPayloadCodec.encode(
                    deviceId = deviceId,
                    appVersion = appVersion,
                    rows = rows,
                ),
            )

            val response = requestExecutor.invoke(request)
            parseResponse(response)
        } catch (_: IOException) {
            TrainingSampleUploadResult.Failure(NETWORK_FAILURE_MESSAGE)
        } catch (_: Exception) {
            TrainingSampleUploadResult.Failure(GENERIC_FAILURE_MESSAGE)
        }
    }

    private fun parseResponse(response: TrainingSampleUploadResponse): TrainingSampleUploadResult {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return TrainingSampleUploadResult.Failure(AUTH_FAILURE_MESSAGE)
        }

        if (response.statusCode !in 200..299) {
            val errorMessage = parseMessage(response.body)
            return TrainingSampleUploadResult.Failure(errorMessage ?: GENERIC_FAILURE_MESSAGE)
        }

        val json = parseJson(response.body) ?: return TrainingSampleUploadResult.Failure(GENERIC_FAILURE_MESSAGE)
        val ok = json.optBoolean("ok", false)
        if (!ok) {
            return TrainingSampleUploadResult.Failure(
                parseMessage(json) ?: GENERIC_FAILURE_MESSAGE
            )
        }

        return TrainingSampleUploadResult.Success(
            acceptedEventIds = parseAcceptedEventIds(json.optJSONArray("acceptedEventIds")),
            insertedCount = json.optInt("insertedCount", 0),
            dedupedCount = json.optInt("dedupedCount", 0),
        )
    }

    private fun parseMessage(body: String): String? {
        return try {
            parseJson(body)?.let { parseMessage(it) }
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

    private fun parseAcceptedEventIds(ids: JSONArray?): List<Long> {
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
}

private const val AUTH_FAILURE_MESSAGE = "鉴权失败，请检查上传令牌"
private const val NETWORK_FAILURE_MESSAGE = "上传失败，请检查网络后重试"
private const val GENERIC_FAILURE_MESSAGE = "上传失败，请稍后重试"

internal fun executeWithHttpUrlConnection(request: TrainingSampleUploadRequest): TrainingSampleUploadResponse {
    val connection = URL(request.url).openConnection() as HttpURLConnection
    connection.requestMethod = request.method
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.doOutput = true
    request.headers.forEach { (name, value) ->
        connection.setRequestProperty(name, value)
    }

    return try {
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(request.body)
        }
        val responseCode = connection.responseCode
        val responseBody = if (responseCode in 200..299) {
            readBodySafely(connection.inputStream)
        } else {
            readBodySafely(connection.errorStream)
        }
        TrainingSampleUploadResponse(
            statusCode = responseCode,
            body = responseBody,
        )
    } finally {
        connection.disconnect()
    }
}

private fun readBodySafely(stream: InputStream?): String {
    if (stream == null) return ""
    return stream.bufferedReader().use { it.readText() }
}
