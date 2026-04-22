package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.UploadHttpRequest
import com.wenhao.record.data.tracking.UploadHttpRequestExecutor
import java.io.IOException

class ProcessedHistoryUploadService(
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
                url = "${config.workerBaseUrl.trim().trimEnd('/')}/processed-histories/batch",
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

            parseHistoryUploadResponse(requestExecutor.invoke(request))
        } catch (_: IOException) {
            HistoryUploadResult.Failure("上传失败，请检查网络后重试")
        } catch (_: Exception) {
            HistoryUploadResult.Failure("上传失败，请稍后重试")
        }
    }
}
