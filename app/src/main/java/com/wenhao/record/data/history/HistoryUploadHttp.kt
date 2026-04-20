package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.UploadHttpRequest
import com.wenhao.record.data.tracking.UploadHttpResponse
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal fun executeWithHttpUrlConnection(request: UploadHttpRequest): UploadHttpResponse {
    val connection = URL(request.url).openConnection() as HttpURLConnection
    connection.requestMethod = request.method
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.doOutput = request.body != null
    request.headers.forEach { (name, value) ->
        connection.setRequestProperty(name, value)
    }

    return try {
        request.body?.let { body ->
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
        }
        val responseCode = connection.responseCode
        val responseBody = if (responseCode in 200..299) {
            readBodySafely(connection.inputStream)
        } else {
            readBodySafely(connection.errorStream)
        }
        UploadHttpResponse(
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
