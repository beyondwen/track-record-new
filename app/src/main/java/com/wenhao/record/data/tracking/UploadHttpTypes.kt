package com.wenhao.record.data.tracking

data class UploadHttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String,
)

data class UploadHttpResponse(
    val statusCode: Int,
    val body: String,
)

typealias UploadHttpRequestExecutor = (UploadHttpRequest) -> UploadHttpResponse
