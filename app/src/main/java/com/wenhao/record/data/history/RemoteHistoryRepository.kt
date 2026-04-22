package com.wenhao.record.data.history

import android.content.Context
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.uploadDeviceId

class RemoteHistoryRepository(
    private val localHistoryLoader: suspend (Context) -> List<HistoryItem> = { context ->
        HistoryStorage.load(context)
    },
    private val localDayLoader: suspend (Context, Long) -> HistoryDayItem? = { context, dayStartMillis ->
        HistoryStorage.loadDailyByStart(context, dayStartMillis)
    },
    private val remoteHistoryLoader: (TrainingSampleUploadConfig, String) -> RemoteHistoryReadResult = { config, deviceId ->
        RemoteHistoryReadService().loadAll(config, deviceId)
    },
    private val remoteHistoryDayLoader: (TrainingSampleUploadConfig, String, Long) -> RemoteHistoryReadResult = { config, deviceId, dayStartMillis ->
        RemoteHistoryReadService().loadByDay(config, deviceId, dayStartMillis)
    },
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
) {
    suspend fun loadMergedDaily(context: Context): List<HistoryDayItem> {
        val localHistories = localHistoryLoader(context)
        val config = configLoader(context)
        if (!config.isConfigured()) {
            return HistoryDayAggregator.aggregate(localHistories)
        }

        return when (val result = remoteHistoryLoader(config, deviceIdProvider(context))) {
            is RemoteHistoryReadResult.Success -> {
                HistoryDayAggregator.aggregate(mergeHistories(localHistories, result.histories))
            }

            is RemoteHistoryReadResult.Failure -> HistoryDayAggregator.aggregate(localHistories)
        }
    }

    suspend fun loadDay(context: Context, dayStartMillis: Long): HistoryDayItem? {
        val localDay = localDayLoader(context, dayStartMillis)
        val config = configLoader(context)
        if (!config.isConfigured()) {
            return localDay
        }

        return when (
            val result = remoteHistoryDayLoader(
                config,
                deviceIdProvider(context),
                dayStartMillis,
            )
        ) {
            is RemoteHistoryReadResult.Success -> {
                HistoryDayAggregator.aggregate(result.histories)
                    .firstOrNull { item -> item.dayStartMillis == dayStartMillis }
                    ?: localDay
            }

            is RemoteHistoryReadResult.Failure -> localDay
        }
    }

    private fun mergeHistories(
        localHistories: List<HistoryItem>,
        remoteHistories: List<HistoryItem>,
    ): List<HistoryItem> {
        val mergedById = localHistories.associateBy { item -> item.id }.toMutableMap()
        remoteHistories.forEach { item ->
            mergedById[item.id] = item
        }
        return mergedById.values
            .sortedWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
    }

    private fun TrainingSampleUploadConfig.isConfigured(): Boolean {
        return workerBaseUrl.isNotBlank() && uploadToken.isNotBlank()
    }
}
