package com.wenhao.record.data.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wenhao.record.BuildConfig
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.stream.TodaySessionPointEntity

class TodaySessionSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val sessionStorage: TodaySessionStorage = TodaySessionStorage(
        TrackDatabase.getInstance(appContext).todaySessionDao(),
    ),
    private val sessionUploadService: TodaySessionUploadService = TodaySessionUploadService(),
    private val pointUploadService: TodaySessionPointUploadService = TodaySessionPointUploadService(),
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val config = configLoader(applicationContext)
        if (!config.isConfigured()) return Result.success()

        val session = sessionStorage.loadOpenSession(nowProvider()) ?: return Result.success()
        val sessionResponse = sessionUploadService.upload(
            config = config,
            appVersion = BuildConfig.VERSION_NAME,
            deviceId = deviceIdProvider(applicationContext),
            session = TodaySessionMetaUploadRow(
                sessionId = session.sessionId,
                dayStartMillis = session.dayStartMillis,
                status = session.status,
                startedAt = session.startedAt,
                lastPointAt = session.lastPointAt,
                endedAt = session.endedAt,
                phase = session.phase,
                updatedAt = session.updatedAt,
            ),
        )
        if (sessionResponse.statusCode !in 200..299) return Result.retry()

        val pendingPoints = sessionStorage.loadPendingPoints(session.sessionId, limit = 200)
        if (pendingPoints.isNotEmpty()) {
            val pointResponse = pointUploadService.upload(
                config = config,
                appVersion = BuildConfig.VERSION_NAME,
                deviceId = deviceIdProvider(applicationContext),
                points = pendingPoints,
            )
            if (pointResponse.statusCode !in 200..299) return Result.retry()
            sessionStorage.markPointsSynced(session.sessionId, pendingPoints.map(TodaySessionPointEntity::pointId), nowProvider())
        }
        sessionStorage.markSessionSynced(session.sessionId, nowProvider())
        return Result.success()
    }

    private fun TrainingSampleUploadConfig.isConfigured(): Boolean {
        return workerBaseUrl.isNotBlank() && uploadToken.isNotBlank()
    }
}
