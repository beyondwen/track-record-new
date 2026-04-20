package com.wenhao.record.data.history

import android.content.Context

object HistoryRetentionPolicy {
    const val RETENTION_DAYS: Long = 7
    const val RETENTION_WINDOW_MILLIS: Long = RETENTION_DAYS * 24L * 60L * 60L * 1000L

    suspend fun pruneUploadedHistories(
        context: Context,
        uploadedHistoryIds: Set<Long>,
        nowMillis: Long,
        historyLoader: suspend (Context) -> List<HistoryItem> = { HistoryStorage.load(it) },
        deleteMany: (Context, List<Long>) -> Unit = HistoryStorage::deleteMany,
        removeUploadedIds: (Context, List<Long>) -> Unit = UploadedHistoryStore::remove,
    ): List<Long> {
        if (uploadedHistoryIds.isEmpty()) {
            return emptyList()
        }

        val cutoffMillis = nowMillis - RETENTION_WINDOW_MILLIS
        val prunedIds = historyLoader(context)
            .asSequence()
            .filter { history -> history.id in uploadedHistoryIds }
            .filter { history -> history.timestamp < cutoffMillis }
            .map { history -> history.id }
            .toList()

        if (prunedIds.isEmpty()) {
            return emptyList()
        }

        deleteMany(context, prunedIds)
        removeUploadedIds(context, prunedIds)
        return prunedIds
    }
}
