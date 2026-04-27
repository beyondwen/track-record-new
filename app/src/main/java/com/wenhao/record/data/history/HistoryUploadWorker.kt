package com.wenhao.record.data.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wenhao.record.BuildConfig
import com.wenhao.record.data.diagnostics.DiagnosticLogger
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.runtimeusage.RuntimeUsageModule
import com.wenhao.record.runtimeusage.RuntimeUsageRecorder
import org.json.JSONObject
import com.wenhao.record.data.tracking.SyncOutboxStorage
import com.wenhao.record.data.tracking.SyncOutboxType
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
    private val pruneUploadedHistories: suspend (Context, Set<Long>, Long) -> List<Long> = { context, uploadedIds, nowMillis ->
        HistoryRetentionPolicy.pruneUploadedHistories(context, uploadedIds, nowMillis)
    },
    private val uploadService: HistoryUploadService = HistoryUploadService(),
    private val syncOutboxStorage: SyncOutboxStorage = SyncOutboxStorage(
        TrackDatabase.getInstance(appContext).syncOutboxDao(),
    ),
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : CoroutineWorker(appContext, workerParams) {

    constructor(
        appContext: Context,
        workerParams: WorkerParameters,
    ) : this(
        appContext = appContext,
        workerParams = workerParams,
        historyLoader = { context -> HistoryStorage.load(context) },
        uploadedHistoryIdsLoader = UploadedHistoryStore::load,
        markUploaded = UploadedHistoryStore::markUploaded,
        pruneUploadedHistories = { context, uploadedIds, nowMillis ->
            HistoryRetentionPolicy.pruneUploadedHistories(context, uploadedIds, nowMillis)
        },
        uploadService = HistoryUploadService(),
        syncOutboxStorage = SyncOutboxStorage(
            TrackDatabase.getInstance(appContext).syncOutboxDao(),
        ),
        configLoader = TrainingSampleUploadConfigStorage::load,
        deviceIdProvider = ::uploadDeviceId,
        nowProvider = System::currentTimeMillis,
    )

    override suspend fun doWork(): Result {
        RuntimeUsageRecorder.hit(RuntimeUsageModule.WORKER_HISTORY_UPLOAD)
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

        val outboxKeys = pendingRows.map { it.historyId.toString() }
        val startedAt = nowProvider()
        syncOutboxStorage.enqueueMany(SyncOutboxType.HISTORY_UPLOAD, outboxKeys, startedAt)
        syncOutboxStorage.markInProgress(SyncOutboxType.HISTORY_UPLOAD, outboxKeys, startedAt)

        val uploader = HistoryBatchUploader { batch ->
            uploadService.upload(
                config = config,
                appVersion = BuildConfig.VERSION_NAME,
                deviceId = deviceIdProvider(applicationContext),
                rows = batch,
            )
        }

        val uploadStartedAt = nowProvider()
        return when (val result = uploader.upload(rows = pendingRows)) {
            is HistoryBatchUploadResult.Success -> {
                val uploadedAt = nowProvider()
                reportSlowUploadIfNeeded(
                    durationMs = uploadedAt - uploadStartedAt,
                    batchSize = pendingRows.size,
                    acceptedCount = result.acceptedHistoryIds.size,
                )
                markUploaded(applicationContext, result.acceptedHistoryIds)
                syncOutboxStorage.markSucceeded(
                    type = SyncOutboxType.HISTORY_UPLOAD,
                    keys = result.acceptedHistoryIds.map { it.toString() },
                    nowMillis = uploadedAt,
                )
                pruneAcceptedHistory(result.acceptedHistoryIds, uploadedHistoryIds)
                Result.success()
            }

            is HistoryBatchUploadResult.Failure -> {
                val finishedAt = nowProvider()
                DiagnosticLogger.error(
                    context = applicationContext,
                    source = "HistoryUploadWorker",
                    message = result.message,
                    fingerprint = "history-upload-failed",
                    payloadJson = JSONObject().apply {
                        put("batchSize", pendingRows.size)
                        put("acceptedCount", result.acceptedHistoryIds.size)
                        put("durationMs", finishedAt - uploadStartedAt)
                    }.toString(),
                )
                markUploaded(applicationContext, result.acceptedHistoryIds)
                syncOutboxStorage.markSucceeded(
                    type = SyncOutboxType.HISTORY_UPLOAD,
                    keys = result.acceptedHistoryIds.map { it.toString() },
                    nowMillis = finishedAt,
                )
                syncOutboxStorage.markFailed(
                    type = SyncOutboxType.HISTORY_UPLOAD,
                    keys = outboxKeys - result.acceptedHistoryIds.map { it.toString() }.toSet(),
                    error = result.message,
                    nowMillis = finishedAt,
                )
                pruneAcceptedHistory(result.acceptedHistoryIds, uploadedHistoryIds)
                retryOrFail(result.message)
            }
        }
    }

    private fun reportSlowUploadIfNeeded(durationMs: Long, batchSize: Int, acceptedCount: Int) {
        if (durationMs < PERF_WARN_UPLOAD_MS) return
        DiagnosticLogger.perfWarn(
            context = applicationContext,
            source = "HistoryUploadWorker",
            message = "history upload took ${durationMs}ms",
            fingerprint = "history-upload-slow",
            payloadJson = JSONObject().apply {
                put("durationMs", durationMs)
                put("batchSize", batchSize)
                put("acceptedCount", acceptedCount)
            }.toString(),
        )
    }

    private suspend fun pruneAcceptedHistory(
        acceptedHistoryIds: List<Long>,
        existingUploadedHistoryIds: Set<Long>,
    ) {
        val mergedUploadedIds = existingUploadedHistoryIds + acceptedHistoryIds
        pruneUploadedHistories(
            applicationContext,
            mergedUploadedIds,
            nowProvider(),
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
        private const val PERF_WARN_UPLOAD_MS = 5_000L
    }
}
