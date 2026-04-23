package com.wenhao.record.data.history

import android.content.Context
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.uploadDeviceId

class RemoteHistoryRepository(
    private val localDailyLoader: suspend (Context) -> List<HistoryDayItem> = { context ->
        HistoryStorage.loadDaily(context)
    },
    private val localDayLoader: suspend (Context, Long) -> HistoryDayItem? = { context, dayStartMillis ->
        HistoryStorage.loadDailyByStart(context, dayStartMillis)
    },
    private val remoteSummaryLoader: (TrainingSampleUploadConfig, String) -> RemoteHistoryDaySummaryReadResult = { config, deviceId ->
        RemoteHistoryReadService().loadDays(config, deviceId)
    },
    private val remoteHistoryDayLoader: (TrainingSampleUploadConfig, String, Long) -> RemoteHistoryReadResult = { config, deviceId, dayStartMillis ->
        RemoteHistoryReadService().loadByDay(config, deviceId, dayStartMillis)
    },
    private val remoteDayDelete: (TrainingSampleUploadConfig, String, Long) -> RemoteHistoryMutationResult = { config, deviceId, dayStartMillis ->
        RemoteHistoryReadService().deleteByDay(config, deviceId, dayStartMillis)
    },
    private val localDeleteMany: suspend (Context, List<Long>) -> Unit = HistoryStorage::deleteMany,
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
) {
    data class DailySummaryLoadResult(
        val items: List<HistoryDaySummaryItem>,
        val remoteStatus: RemoteStatus,
    )

    enum class RemoteStatus {
        DISABLED,
        SUCCESS,
        FAILURE,
    }

    suspend fun loadMergedDailySummaries(context: Context): List<HistoryDaySummaryItem> {
        return loadDailySummaryState(context).items
    }

    suspend fun loadDailySummaryState(context: Context): DailySummaryLoadResult {
        val localItems = localDailyLoader(context).map(HistoryDayItem::toSummaryItem)
        val config = configLoader(context)
        if (!config.isConfigured()) {
            return DailySummaryLoadResult(
                items = localItems,
                remoteStatus = RemoteStatus.DISABLED,
            )
        }

        return when (val result = remoteSummaryLoader(config, deviceIdProvider(context))) {
            is RemoteHistoryDaySummaryReadResult.Success -> DailySummaryLoadResult(
                items = mergeDailySummaries(localItems, result.items),
                remoteStatus = RemoteStatus.SUCCESS,
            )
            is RemoteHistoryDaySummaryReadResult.Failure -> DailySummaryLoadResult(
                items = localItems,
                remoteStatus = RemoteStatus.FAILURE,
            )
        }
    }

    suspend fun loadLocalDay(context: Context, dayStartMillis: Long): HistoryDayItem? {
        return localDayLoader(context, dayStartMillis)
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

    suspend fun deleteDay(
        context: Context,
        item: HistoryDaySummaryItem,
    ): Boolean {
        val config = configLoader(context)
        if (config.isConfigured()) {
            when (remoteDayDelete(config, deviceIdProvider(context), item.dayStartMillis)) {
                RemoteHistoryMutationResult.Success -> Unit
                is RemoteHistoryMutationResult.Failure -> return false
            }
        }
        localDeleteMany(context, item.sourceIds)
        return true
    }

    private fun mergeDailySummaries(
        localItems: List<HistoryDaySummaryItem>,
        remoteItems: List<HistoryDaySummaryItem>,
    ): List<HistoryDaySummaryItem> {
        val mergedByDay = localItems.associateBy { item -> item.dayStartMillis }.toMutableMap()
        remoteItems.forEach { item ->
            val localItem = mergedByDay[item.dayStartMillis]
            mergedByDay[item.dayStartMillis] = if (localItem == null) {
                item
            } else {
                item.copy(
                    sourceIds = if (localItem.sourceIds.isNotEmpty()) localItem.sourceIds else item.sourceIds,
                )
            }
        }
        return mergedByDay.values.sortedByDescending { it.dayStartMillis }
    }

    private fun TrainingSampleUploadConfig.isConfigured(): Boolean {
        return workerBaseUrl.isNotBlank() && uploadToken.isNotBlank()
    }
}
