package com.wenhao.record.data.local.stream

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncOutboxEntity)

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE itemType = :itemType AND itemKey = :itemKey
        LIMIT 1
        """
    )
    suspend fun load(itemType: String, itemKey: String): SyncOutboxEntity?

    @Query(
        """
        UPDATE sync_outbox
        SET status = :status, updatedAt = :updatedAt
        WHERE itemType = :itemType AND itemKey = :itemKey
        """
    )
    suspend fun updateStatus(itemType: String, itemKey: String, status: String, updatedAt: Long)

    @Query(
        """
        UPDATE sync_outbox
        SET status = :status,
            retryCount = retryCount + 1,
            lastError = :lastError,
            updatedAt = :updatedAt
        WHERE itemType = :itemType AND itemKey = :itemKey
        """
    )
    suspend fun markFailed(itemType: String, itemKey: String, status: String, lastError: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query(
        """
        SELECT lastError FROM sync_outbox
        WHERE status = :status AND lastError IS NOT NULL AND lastError != ''
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun loadLatestError(status: String): String?
}
