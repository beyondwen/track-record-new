package com.wenhao.record.data.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wenhao.record.BuildConfig
import com.wenhao.record.data.diagnostics.DiagnosticLogger
import com.wenhao.record.data.local.TrackDatabase
import org.json.JSONObject

class RawPointUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val pointStorage: ContinuousPointStorage = ContinuousPointStorage(
        TrackDatabase.getInstance(appContext).continuousTrackDao(),
    ),
    val cursorStorage: UploadCursorStorage = UploadCursorStorage(
        TrackDatabase.getInstance(appContext).continuousTrackDao(),
    ),
    private val uploadService: RawPointUploadService = RawPointUploadService(),
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
) : CoroutineWorker(appContext, workerParams) {

    constructor(
        appContext: Context,
        workerParams: WorkerParameters,
    ) : this(
        appContext = appContext,
        workerParams = workerParams,
        pointStorage = ContinuousPointStorage(
            TrackDatabase.getInstance(appContext).continuousTrackDao(),
        ),
        cursorStorage = UploadCursorStorage(
            TrackDatabase.getInstance(appContext).continuousTrackDao(),
        ),
        uploadService = RawPointUploadService(),
        configLoader = TrainingSampleUploadConfigStorage::load,
        deviceIdProvider = ::uploadDeviceId,
    )

    override suspend fun doWork(): Result {
        val config = configLoader(applicationContext)
        if (!config.isConfigured()) return Result.success()

        repeat(MAX_BATCHES_PER_RUN) {
            val cursor = cursorStorage.load(UploadCursorType.RAW_POINT)
            val rows = pointStorage.loadPendingRawUploadRows(
                afterPointId = cursor.lastUploadedId,
                limit = BATCH_SIZE,
            )
            if (rows.isEmpty()) {
                return Result.success()
            }

            val uploadStartedAt = System.currentTimeMillis()
            when (
                val result = uploadService.upload(
                    config = config,
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceId = deviceIdProvider(applicationContext),
                    rows = rows,
                )
            ) {
                is RawPointUploadResult.Success -> {
                    reportSlowUploadIfNeeded(
                        durationMs = System.currentTimeMillis() - uploadStartedAt,
                        batchSize = rows.size,
                        acceptedMaxPointId = result.acceptedMaxPointId,
                    )
                    cursorStorage.markUploaded(
                        type = UploadCursorType.RAW_POINT,
                        lastUploadedId = result.acceptedMaxPointId,
                        updatedAt = System.currentTimeMillis(),
                    )
                }

                is RawPointUploadResult.Failure -> {
                    val durationMs = System.currentTimeMillis() - uploadStartedAt
                    DiagnosticLogger.error(
                        context = applicationContext,
                        source = "RawPointUploadWorker",
                        message = result.message,
                        fingerprint = "raw-point-upload-failed",
                        payloadJson = JSONObject().apply {
                            put("batchSize", rows.size)
                            put("durationMs", durationMs)
                            put("lastPointId", rows.lastOrNull()?.pointId ?: 0L)
                        }.toString(),
                    )
                    return retryOrFail(result.message)
                }
            }
        }

        return Result.success()
    }

    private fun reportSlowUploadIfNeeded(durationMs: Long, batchSize: Int, acceptedMaxPointId: Long) {
        if (durationMs < PERF_WARN_UPLOAD_MS) return
        DiagnosticLogger.perfWarn(
            context = applicationContext,
            source = "RawPointUploadWorker",
            message = "raw point upload took ${durationMs}ms",
            fingerprint = "raw-point-upload-slow",
            payloadJson = JSONObject().apply {
                put("durationMs", durationMs)
                put("batchSize", batchSize)
                put("acceptedMaxPointId", acceptedMaxPointId)
            }.toString(),
        )
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
        private const val BATCH_SIZE = 200
        private const val MAX_BATCHES_PER_RUN = 25
        private const val PERF_WARN_UPLOAD_MS = 5_000L
    }
}
