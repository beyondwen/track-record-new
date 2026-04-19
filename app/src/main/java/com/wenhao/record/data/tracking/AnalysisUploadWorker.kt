package com.wenhao.record.data.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wenhao.record.BuildConfig
import com.wenhao.record.data.local.TrackDatabase

class AnalysisUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val pointStorage: ContinuousPointStorage = ContinuousPointStorage(
        TrackDatabase.getInstance(appContext).continuousTrackDao(),
    ),
    val cursorStorage: UploadCursorStorage = UploadCursorStorage(
        TrackDatabase.getInstance(appContext).continuousTrackDao(),
    ),
    private val uploadService: AnalysisUploadService = AnalysisUploadService(),
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val config = configLoader(applicationContext)
        if (!config.isConfigured()) return Result.success()

        repeat(MAX_BATCHES_PER_RUN) {
            val cursor = cursorStorage.load(UploadCursorType.ANALYSIS_SEGMENT)
            val rows = pointStorage.loadPendingAnalysisUploadRows(
                afterSegmentId = cursor.lastUploadedId,
                limit = BATCH_SIZE,
            )
            if (rows.isEmpty()) {
                return Result.success()
            }

            when (
                val result = uploadService.upload(
                    config = config,
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceId = deviceIdProvider(applicationContext),
                    rows = rows,
                )
            ) {
                is AnalysisUploadResult.Success -> {
                    cursorStorage.markUploaded(
                        type = UploadCursorType.ANALYSIS_SEGMENT,
                        lastUploadedId = result.acceptedMaxSegmentId,
                        updatedAt = System.currentTimeMillis(),
                    )
                }

                is AnalysisUploadResult.Failure -> {
                    return retryOrFail(result.message)
                }
            }
        }

        return Result.success()
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

    companion object {
        private const val BATCH_SIZE = 100
        private const val MAX_BATCHES_PER_RUN = 3
    }
}
