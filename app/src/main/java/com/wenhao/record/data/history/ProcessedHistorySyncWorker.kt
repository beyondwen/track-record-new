package com.wenhao.record.data.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wenhao.record.BuildConfig
import com.wenhao.record.data.diagnostics.DiagnosticLogger
import com.wenhao.record.data.tracking.AnalysisHistoryProjector
import com.wenhao.record.data.tracking.ProcessedHistorySyncStateStorage
import com.wenhao.record.data.tracking.RawTrackPoint
import com.wenhao.record.data.tracking.RemoteRawPointDaySummaryReadResult
import com.wenhao.record.data.tracking.RemoteRawPointReadResult
import com.wenhao.record.data.tracking.RemoteRawPointReadService
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.uploadDeviceId
import org.json.JSONObject
import com.wenhao.record.tracking.analysis.AnalysisContext
import com.wenhao.record.tracking.analysis.AnalyzedPoint
import com.wenhao.record.tracking.analysis.TrackAnalysisResult
import com.wenhao.record.tracking.analysis.TrackAnalysisRunner
import java.util.TimeZone

class ProcessedHistorySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val daySummaryLoader: (TrainingSampleUploadConfig, String, Int) -> RemoteRawPointDaySummaryReadResult = { config, deviceId, utcOffsetMinutes ->
        RemoteRawPointReadService().loadDays(config, deviceId, utcOffsetMinutes)
    },
    private val pointLoader: (TrainingSampleUploadConfig, String, Long) -> RemoteRawPointReadResult = { config, deviceId, dayStartMillis ->
        RemoteRawPointReadService().loadByDay(config, deviceId, dayStartMillis)
    },
    private val uploadService: ProcessedHistoryUploadService = ProcessedHistoryUploadService(),
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
    private val analysisRunner: (List<AnalyzedPoint>, AnalysisContext?) -> TrackAnalysisResult = { points, context ->
        TrackAnalysisRunner().analyze(points, context)
    },
    private val historyProjector: AnalysisHistoryProjector = AnalysisHistoryProjector(),
    private val stateStorage: ProcessedHistorySyncStateStorage,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.getDefault() },
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : CoroutineWorker(appContext, workerParams) {

    constructor(
        appContext: Context,
        workerParams: WorkerParameters,
    ) : this(
        appContext = appContext,
        workerParams = workerParams,
        stateStorage = ProcessedHistorySyncStateStorage(appContext),
    )

    override suspend fun doWork(): Result {
        val config = configLoader(applicationContext)
        if (!config.isConfigured()) return Result.success()

        val deviceId = deviceIdProvider(applicationContext)
        val utcOffsetMinutes = timeZoneProvider().getOffset(nowProvider()).div(60_000)
        val days = when (val result = daySummaryLoader(config, deviceId, utcOffsetMinutes)) {
            is RemoteRawPointDaySummaryReadResult.Success -> result.days.sortedBy { it.dayStartMillis }
            is RemoteRawPointDaySummaryReadResult.Failure -> {
                logError(
                    message = result.message,
                    fingerprint = "processed-history-day-summary-load-failed",
                    payload = JSONObject().apply { put("utcOffsetMinutes", utcOffsetMinutes) },
                )
                return retryOrFail(result.message)
            }
        }

        for (day in days) {
            if (stateStorage.load(day.dayStartMillis) >= day.maxPointId) {
                continue
            }

            val points = when (val result = pointLoader(config, deviceId, day.dayStartMillis)) {
                is RemoteRawPointReadResult.Success -> result.points.sortedBy { it.timestampMillis }
                is RemoteRawPointReadResult.Failure -> {
                    logError(
                        message = result.message,
                        fingerprint = "processed-history-point-load-failed",
                        payload = JSONObject().apply {
                            put("dayStartMillis", day.dayStartMillis)
                            put("maxPointId", day.maxPointId)
                        },
                    )
                    return retryOrFail(result.message)
                }
            }

            if (points.size < 3) {
                stateStorage.markSynced(day.dayStartMillis, day.maxPointId)
                continue
            }

            val analysisStartedAt = nowProvider()
            val analysisResult = analysisRunner(
                points.map { point -> point.toAnalyzedPoint() },
                null,
            )
            reportSlowAnalysisIfNeeded(
                durationMs = nowProvider() - analysisStartedAt,
                pointCount = points.size,
                dayStartMillis = day.dayStartMillis,
            )
            val histories = historyProjector.project(
                segments = analysisResult.segments,
                rawPoints = points,
            )

            if (histories.isNotEmpty()) {
                val uploader = HistoryBatchUploader { batch ->
                    uploadService.upload(
                        config = config,
                        appVersion = BuildConfig.VERSION_NAME,
                        deviceId = deviceId,
                        rows = batch,
                    )
                }
                when (val uploadResult = uploader.upload(histories.map(HistoryUploadRow::from))) {
                    is HistoryBatchUploadResult.Success -> {
                    }
                    is HistoryBatchUploadResult.Failure -> {
                        logError(
                            message = uploadResult.message,
                            fingerprint = "processed-history-upload-failed",
                            payload = JSONObject().apply {
                                put("dayStartMillis", day.dayStartMillis)
                                put("historyCount", histories.size)
                                put("acceptedCount", uploadResult.acceptedHistoryIds.size)
                            },
                        )
                        return retryOrFail(uploadResult.message)
                    }
                }
            }

            stateStorage.markSynced(day.dayStartMillis, day.maxPointId)
        }

        return Result.success()
    }

    private fun logError(message: String, fingerprint: String, payload: JSONObject) {
        DiagnosticLogger.error(
            context = applicationContext,
            source = "ProcessedHistorySyncWorker",
            message = message,
            fingerprint = fingerprint,
            payloadJson = payload.toString(),
        )
    }

    private fun reportSlowAnalysisIfNeeded(durationMs: Long, pointCount: Int, dayStartMillis: Long) {
        if (durationMs < PERF_WARN_ANALYSIS_MS) return
        DiagnosticLogger.perfWarn(
            context = applicationContext,
            source = "ProcessedHistorySyncWorker",
            message = "processed history analysis took ${durationMs}ms",
            fingerprint = "processed-history-analysis-slow",
            payloadJson = JSONObject().apply {
                put("durationMs", durationMs)
                put("pointCount", pointCount)
                put("dayStartMillis", dayStartMillis)
            }.toString(),
        )
    }

    private fun RawTrackPoint.toAnalyzedPoint(): AnalyzedPoint {
        return AnalyzedPoint(
            timestampMillis = timestampMillis,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            speedMetersPerSecond = speedMetersPerSecond,
            activityType = activityType,
            activityConfidence = activityConfidence,
            wifiFingerprintDigest = wifiFingerprintDigest,
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
        private const val PERF_WARN_ANALYSIS_MS = 5_000L
    }
}
