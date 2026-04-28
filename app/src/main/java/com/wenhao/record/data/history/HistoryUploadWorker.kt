package com.wenhao.record.data.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wenhao.record.BuildConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.uploadDeviceId
import com.wenhao.record.runtimeusage.RuntimeUsageModule
import com.wenhao.record.runtimeusage.RuntimeUsageRecorder

class HistoryUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val historyLoader: suspend (Context) -> List<HistoryItem> = HistoryStorage::load,
    private val uploadService: HistoryUploadService = HistoryUploadService(),
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
) : CoroutineWorker(appContext, workerParams) {

    constructor(
        appContext: Context,
        workerParams: WorkerParameters,
    ) : this(
        appContext = appContext,
        workerParams = workerParams,
        historyLoader = HistoryStorage::load,
        uploadService = HistoryUploadService(),
        configLoader = TrainingSampleUploadConfigStorage::load,
        deviceIdProvider = ::uploadDeviceId,
    )

    override suspend fun doWork(): Result {
        RuntimeUsageRecorder.hit(RuntimeUsageModule.WORKER_HISTORY_UPLOAD)
        val config = configLoader(applicationContext)
        if (!config.isConfigured()) return Result.success()

        val histories = historyLoader(applicationContext)
        if (histories.isEmpty()) return Result.success()

        val uploader = HistoryBatchUploader { batch ->
            uploadService.upload(
                config = config,
                appVersion = BuildConfig.VERSION_NAME,
                deviceId = deviceIdProvider(applicationContext),
                rows = batch,
            )
        }
        return when (val result = uploader.upload(histories.map(HistoryUploadRow::from))) {
            is HistoryBatchUploadResult.Success -> Result.success()
            is HistoryBatchUploadResult.Failure -> {
                if (result.message.contains("鉴权失败")) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        }
    }

    private fun TrainingSampleUploadConfig.isConfigured(): Boolean {
        return workerBaseUrl.isNotBlank() && uploadToken.isNotBlank()
    }
}
