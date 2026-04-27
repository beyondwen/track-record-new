package com.wenhao.record.data.diagnostics

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wenhao.record.BuildConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.uploadDeviceId
import com.wenhao.record.runtimeusage.RuntimeUsageModule
import com.wenhao.record.runtimeusage.RuntimeUsageRecorder

class DiagnosticLogUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val store: DiagnosticLogStore = DiagnosticLogStore(appContext),
    private val uploadService: DiagnosticLogUploadService = DiagnosticLogUploadService(),
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
) : CoroutineWorker(appContext, workerParams) {

    constructor(appContext: Context, workerParams: WorkerParameters) : this(
        appContext = appContext,
        workerParams = workerParams,
        store = DiagnosticLogStore(appContext),
        uploadService = DiagnosticLogUploadService(),
        configLoader = TrainingSampleUploadConfigStorage::load,
        deviceIdProvider = ::uploadDeviceId,
    )

    override suspend fun doWork(): Result {
        RuntimeUsageRecorder.hit(RuntimeUsageModule.WORKER_DIAGNOSTIC_LOG_UPLOAD)
        val config = configLoader(applicationContext)
        if (!config.isConfigured()) return Result.success()

        val batch = store.loadBatch(BATCH_SIZE)
        if (batch.isEmpty()) return Result.success()

        return when (val result = uploadService.upload(
            config = config,
            appVersion = BuildConfig.VERSION_NAME,
            deviceId = deviceIdProvider(applicationContext),
            logs = batch,
        )) {
            is DiagnosticLogUploadResult.Success -> {
                store.removeByLogIds(batch.map { it.logId }.toSet())
                Result.success()
            }
            is DiagnosticLogUploadResult.Failure -> retryOrFail(result.message)
        }
    }

    private fun retryOrFail(message: String): Result {
        return if (message.contains("鉴权失败")) Result.failure() else Result.retry()
    }

    private fun TrainingSampleUploadConfig.isConfigured(): Boolean {
        return workerBaseUrl.isNotBlank() && uploadToken.isNotBlank()
    }

    companion object {
        private const val BATCH_SIZE = 50
    }
}
