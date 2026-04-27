package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.SyncOutboxDao
import com.wenhao.record.data.local.stream.SyncOutboxEntity

enum class SyncOutboxType {
    ANALYSIS_UPLOAD,
}

enum class SyncOutboxStatus {
    PENDING,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
}

class SyncOutboxStorage(
    private val dao: SyncOutboxDao,
) {
    suspend fun enqueueMany(type: SyncOutboxType, keys: List<String>, nowMillis: Long) {
        keys.distinct().forEach { key ->
            val existing = dao.load(type.name, key)
            if (existing == null) {
                dao.upsert(
                    SyncOutboxEntity(
                        itemType = type.name,
                        itemKey = key,
                        status = SyncOutboxStatus.PENDING.name,
                        retryCount = 0,
                        lastError = null,
                        createdAt = nowMillis,
                        updatedAt = nowMillis,
                    )
                )
            } else if (existing.status != SyncOutboxStatus.SUCCEEDED.name) {
                dao.updateStatus(
                    itemType = type.name,
                    itemKey = key,
                    status = SyncOutboxStatus.PENDING.name,
                    updatedAt = nowMillis,
                )
            }
        }
    }

    suspend fun markInProgress(type: SyncOutboxType, keys: List<String>, nowMillis: Long) {
        keys.distinct().forEach { key ->
            dao.updateStatus(type.name, key, SyncOutboxStatus.IN_PROGRESS.name, nowMillis)
        }
    }

    suspend fun markSucceeded(type: SyncOutboxType, keys: List<String>, nowMillis: Long) {
        keys.distinct().forEach { key ->
            dao.updateStatus(type.name, key, SyncOutboxStatus.SUCCEEDED.name, nowMillis)
        }
    }

    suspend fun markFailed(type: SyncOutboxType, keys: List<String>, error: String, nowMillis: Long) {
        keys.distinct().forEach { key ->
            dao.markFailed(type.name, key, SyncOutboxStatus.FAILED.name, error, nowMillis)
        }
    }

    suspend fun countByStatus(status: SyncOutboxStatus): Int {
        return dao.countByStatus(status.name)
    }
}
