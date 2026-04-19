package com.wenhao.record.data.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wenhao.record.BuildConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.uploadDeviceId

class HistoryUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val historyLoader: suspend (Context) -> List<HistoryItem> = { context ->
        HistoryStorage.load(context)
    },
    private val uploadedHistoryIdsLoader: (Context) -> Set<Long> = UploadedHistoryStore::load,
    private val markUploaded: (Context, List<Long>) -> Unit = UploadedHistoryStore::markUploaded,
    private val uploadService: HistoryUploadService = HistoryUploadService(),
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val config = configLoader(applicationContext)
        if (!config.isConfigured()) return Result.success()

        val uploadedHistoryIds = uploadedHistoryIdsLoader(applicationContext)
        val pendingRows = historyLoader(applicationContext)
            .asSequence()
            .filterNot { uploadedHistoryIds.contains(it.id) }
            .sortedBy { it.id }
            .map(HistoryUploadRow::from)
            .toList()

        if (pendingRows.isEmpty()) {
            return Result.success()
        }

        val uploader = HistoryBatchUploader { batch ->
            uploadService.upload(
                config = config,
                appVersion = BuildConfig.VERSION_NAME,
                deviceId = deviceIdProvider(applicationContext),
                rows = batch,
            )
        }

        return when (val result = uploader.upload(rows = pendingRows)) {
            is HistoryBatchUploadResult.Success -> {
                markUploaded(applicationContext, result.acceptedHistoryIds)
                Result.success()
            }

            is HistoryBatchUploadResult.Failure -> {
                markUploaded(applicationContext, result.acceptedHistoryIds)
                retryOrFail(result.message)
            }
        }
    }

    private fun retryOrFail(message: String): Result {
        return if (message.contains("鉴权失败")) {
            Result.failure()
        } else {
            Result.retry()
        }
    }

    private fun TrainingSampleUploadConfig.isConfigured(): Boolean {
        return workerBaseUrl.isNotBlank() && uploadToken.isNotBlank()
    }
}
