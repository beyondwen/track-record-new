package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.SyncOutboxDao
import com.wenhao.record.data.local.stream.SyncOutboxEntity
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncOutboxStorageTest {

    @Test
    fun `enqueue creates pending rows and success marks accepted keys`() = runBlocking {
        val dao = FakeSyncOutboxDao()
        val storage = SyncOutboxStorage(dao)

        storage.enqueueMany(SyncOutboxType.ANALYSIS_UPLOAD, listOf("100", "101"), nowMillis = 1_000L)
        storage.markInProgress(SyncOutboxType.ANALYSIS_UPLOAD, listOf("100", "101"), nowMillis = 2_000L)
        storage.markSucceeded(SyncOutboxType.ANALYSIS_UPLOAD, listOf("100"), nowMillis = 3_000L)

        assertEquals(SyncOutboxStatus.SUCCEEDED.name, dao.items.getValue("ANALYSIS_UPLOAD:100").status)
        assertEquals(SyncOutboxStatus.IN_PROGRESS.name, dao.items.getValue("ANALYSIS_UPLOAD:101").status)
    }

    @Test
    fun `failure increments retry count and keeps last error`() = runBlocking {
        val dao = FakeSyncOutboxDao()
        val storage = SyncOutboxStorage(dao)

        storage.enqueueMany(SyncOutboxType.HISTORY_UPLOAD, listOf("7"), nowMillis = 1_000L)
        storage.markFailed(SyncOutboxType.HISTORY_UPLOAD, listOf("7"), error = "network", nowMillis = 2_000L)
        storage.markFailed(SyncOutboxType.HISTORY_UPLOAD, listOf("7"), error = "timeout", nowMillis = 3_000L)

        val row = dao.items.getValue("HISTORY_UPLOAD:7")
        assertEquals(SyncOutboxStatus.FAILED.name, row.status)
        assertEquals(2, row.retryCount)
        assertEquals("timeout", row.lastError)
    }

    private class FakeSyncOutboxDao : SyncOutboxDao {
        val items = linkedMapOf<String, SyncOutboxEntity>()

        override suspend fun upsert(entity: SyncOutboxEntity) {
            items[entity.key()] = entity
        }

        override suspend fun load(itemType: String, itemKey: String): SyncOutboxEntity? {
            return items["$itemType:$itemKey"]
        }

        override suspend fun updateStatus(itemType: String, itemKey: String, status: String, updatedAt: Long) {
            items[itemType + ":" + itemKey]?.let { current ->
                items[current.key()] = current.copy(status = status, updatedAt = updatedAt)
            }
        }

        override suspend fun markFailed(itemType: String, itemKey: String, status: String, lastError: String, updatedAt: Long) {
            items[itemType + ":" + itemKey]?.let { current ->
                items[current.key()] = current.copy(
                    status = status,
                    retryCount = current.retryCount + 1,
                    lastError = lastError,
                    updatedAt = updatedAt,
                )
            }
        }

        override suspend fun countByStatus(status: String): Int {
            return items.values.count { it.status == status }
        }

        override suspend fun loadLatestError(status: String): String? {
            return items.values
                .filter { it.status == status && !it.lastError.isNullOrBlank() }
                .maxByOrNull { it.updatedAt }
                ?.lastError
        }

        private fun SyncOutboxEntity.key(): String = "$itemType:$itemKey"
    }
}
