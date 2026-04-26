package com.wenhao.record.data.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.tracking.RemoteTodaySessionReadResult
import com.wenhao.record.data.tracking.TodaySessionRemoteReadService
import com.wenhao.record.data.tracking.TodaySessionStorage
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.uploadDeviceId

class TrackMirrorRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val historyLoader: (TrainingSampleUploadConfig, String) -> RemoteHistoryReadResult = { config, deviceId ->
        RemoteHistoryReadService().loadAll(config, deviceId)
    },
    private val todaySessionLoader: (TrainingSampleUploadConfig, String) -> RemoteTodaySessionReadResult = { config, deviceId ->
        TodaySessionRemoteReadService().loadOpenSession(config, deviceId)
    },
    private val historyRestorer: suspend (Context, List<HistoryItem>) -> Unit = HistoryStorage::restoreFromRemote,
    private val todaySessionStorage: TodaySessionStorage = TodaySessionStorage(
        TrackDatabase.getInstance(appContext).todaySessionDao(),
    ),
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val config = configLoader(applicationContext)
        if (!config.isConfigured()) return Result.success()

        val deviceId = deviceIdProvider(applicationContext)
        when (val result = historyLoader(config, deviceId)) {
            is RemoteHistoryReadResult.Success -> historyRestorer(applicationContext, result.histories)
            is RemoteHistoryReadResult.Failure -> return retryOrFail(result.message)
        }

        return when (val result = todaySessionLoader(config, deviceId)) {
            is RemoteTodaySessionReadResult.Success -> {
                todaySessionStorage.replaceWithRemoteSession(result.snapshot, nowProvider())
                Result.success()
            }
            is RemoteTodaySessionReadResult.Failure -> retryOrFail(result.message)
        }
    }

    private fun retryOrFail(message: String): Result {
        return if (message.contains("鉴权失败")) Result.failure() else Result.retry()
    }

    private fun TrainingSampleUploadConfig.isConfigured(): Boolean {
        return workerBaseUrl.isNotBlank() && uploadToken.isNotBlank()
    }
}
