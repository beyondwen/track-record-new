package com.wenhao.record.data.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wenhao.record.BuildConfig
import com.wenhao.record.data.diagnostics.DiagnosticLogger
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.runtimeusage.RuntimeUsageModule
import com.wenhao.record.runtimeusage.RuntimeUsageRecorder
import org.json.JSONObject

class AnalysisUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val pointStorage: ContinuousPointStorage = ContinuousPointStorage(
        TrackDatabase.getInstance(appContext).continuousTrackDao(),
    ),
    val cursorStorage: UploadCursorStorage = UploadCursorStorage(
        TrackDatabase.getInstance(appContext).continuousTrackDao(),
    ),
    private val syncOutboxStorage: SyncOutboxStorage = SyncOutboxStorage(
        TrackDatabase.getInstance(appContext).syncOutboxDao(),
    ),
    private val uploadService: AnalysisUploadService = AnalysisUploadService(),
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
        syncOutboxStorage = SyncOutboxStorage(
            TrackDatabase.getInstance(appContext).syncOutboxDao(),
        ),
        uploadService = AnalysisUploadService(),
        configLoader = TrainingSampleUploadConfigStorage::load,
        deviceIdProvider = ::uploadDeviceId,
    )

    override suspend fun doWork(): Result {
        RuntimeUsageRecorder.hit(RuntimeUsageModule.WORKER_ANALYSIS_UPLOAD)
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

            val outboxKeys = rows.map { it.segmentId.toString() }
            val startedAt = System.currentTimeMillis()
            syncOutboxStorage.enqueueMany(SyncOutboxType.ANALYSIS_UPLOAD, outboxKeys, startedAt)
            syncOutboxStorage.markInProgress(SyncOutboxType.ANALYSIS_UPLOAD, outboxKeys, startedAt)

            val uploadStartedAt = System.currentTimeMillis()
            when (
                val result = uploadService.upload(
                    config = config,
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceId = deviceIdProvider(applicationContext),
                    rows = rows,
                )
            ) {
                is AnalysisUploadResult.Success -> {
                    val uploadedAt = System.currentTimeMillis()
                    reportSlowUploadIfNeeded(
                        durationMs = uploadedAt - uploadStartedAt,
                        batchSize = rows.size,
                        acceptedMaxSegmentId = result.acceptedMaxSegmentId,
                    )
                    cursorStorage.markUploaded(
                        type = UploadCursorType.ANALYSIS_SEGMENT,
                        lastUploadedId = result.acceptedMaxSegmentId,
                        updatedAt = uploadedAt,
                    )
                    syncOutboxStorage.markSucceeded(
                        type = SyncOutboxType.ANALYSIS_UPLOAD,
                        keys = rows
                            .filter { it.segmentId <= result.acceptedMaxSegmentId }
                            .map { it.segmentId.toString() },
                        nowMillis = uploadedAt,
                    )
                }

                is AnalysisUploadResult.Failure -> {
                    val durationMs = System.currentTimeMillis() - uploadStartedAt
                    DiagnosticLogger.error(
                        context = applicationContext,
                        source = "AnalysisUploadWorker",
                        message = result.message,
                        fingerprint = "analysis-upload-failed",
                        payloadJson = JSONObject().apply {
                            put("batchSize", rows.size)
                            put("durationMs", durationMs)
                            put("lastSegmentId", rows.lastOrNull()?.segmentId ?: 0L)
                        }.toString(),
                    )
                    syncOutboxStorage.markFailed(
                        type = SyncOutboxType.ANALYSIS_UPLOAD,
                        keys = outboxKeys,
                        error = result.message,
                        nowMillis = System.currentTimeMillis(),
                    )
                    return retryOrFail(result.message)
                }
            }
        }

        return Result.success()
    }

    private fun reportSlowUploadIfNeeded(durationMs: Long, batchSize: Int, acceptedMaxSegmentId: Long) {
        if (durationMs < PERF_WARN_UPLOAD_MS) return
        DiagnosticLogger.perfWarn(
            context = applicationContext,
            source = "AnalysisUploadWorker",
            message = "analysis upload took ${durationMs}ms",
            fingerprint = "analysis-upload-slow",
            payloadJson = JSONObject().apply {
                put("durationMs", durationMs)
                put("batchSize", batchSize)
                put("acceptedMaxSegmentId", acceptedMaxSegmentId)
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
        private const val BATCH_SIZE = 100
        private const val MAX_BATCHES_PER_RUN = 3
        private const val PERF_WARN_UPLOAD_MS = 5_000L
    }
}
