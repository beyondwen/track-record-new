package com.wenhao.record.data.history

import com.wenhao.record.data.local.history.HistoryDao
import com.wenhao.record.data.local.history.HistoryPointEntity
import com.wenhao.record.data.local.history.HistoryRecordEntity
import com.wenhao.record.data.local.history.HistoryRecordWithPoints
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalHistoryRepositoryTest {

    @Test
    fun `loadDailySummaries only needs history records`() = runBlocking {
        val dayStart = 1_714_220_800_000L
        val dao = FakeHistoryDao(
            records = listOf(
                HistoryRecordEntity(
                    historyId = 7L,
                    sourceSessionId = "session_1",
                    dateKey = dayStart,
                    timestamp = dayStart + 2_600_000L,
                    distanceKm = 3.2,
                    durationSeconds = 900,
                    averageSpeedKmh = 12.8,
                    title = "下班回家",
                    syncState = "SYNCED",
                    version = 1L,
                    startSource = "AUTO",
                    stopSource = "AUTO",
                    manualStartAt = null,
                    manualStopAt = null,
                )
            ),
            points = emptyMap(),
        )
        val repository = LocalHistoryRepository(dao)

        val items = repository.loadDailySummaries()

        assertEquals(1, items.size)
        assertEquals("下班回家", items.single().routeTitle)
    }

    @Test
    fun `loadDayDetail fetches points lazily for selected day`() = runBlocking {
        val dayStart = 1_714_220_800_000L
        val dao = FakeHistoryDao(
            records = listOf(
                HistoryRecordEntity(
                    historyId = 8L,
                    sourceSessionId = "session_2",
                    dateKey = dayStart,
                    timestamp = dayStart + 6_000L,
                    distanceKm = 1.1,
                    durationSeconds = 300,
                    averageSpeedKmh = 13.2,
                    title = "夜跑",
                    syncState = "PENDING",
                    version = 2L,
                    startSource = "AUTO",
                    stopSource = "AUTO",
                    manualStartAt = null,
                    manualStopAt = null,
                )
            ),
            points = mapOf(
                8L to listOf(
                    HistoryPointEntity(8L, 0, 30.0, 120.0, dayStart + 1_000L, 8f, 10.0),
                    HistoryPointEntity(8L, 1, 30.0001, 120.0001, dayStart + 6_000L, 8f, 10.0),
                )
            ),
        )
        val repository = LocalHistoryRepository(dao)

        val item = repository.loadDayDetail(dayStart)

        assertEquals(1, item?.segments?.size)
        assertEquals(2, item?.pointCount)
    }

    private class FakeHistoryDao(
        private val records: List<HistoryRecordEntity>,
        private val points: Map<Long, List<HistoryPointEntity>>,
    ) : HistoryDao {
        override suspend fun getHistoryRecords(): List<HistoryRecordEntity> = records

        override suspend fun getHistoryRecordsByDay(dayStartMillis: Long): List<HistoryRecordEntity> {
            return records.filter { it.dateKey == dayStartMillis }.sortedBy { it.timestamp }
        }

        override suspend fun getHistoryPoints(historyId: Long): List<HistoryPointEntity> {
            return points[historyId].orEmpty().sortedBy { it.pointOrder }
        }

        override suspend fun getHistoryWithPoints(): List<HistoryRecordWithPoints> = error("unused")
        override suspend fun getHistoryWithPoints(historyId: Long): HistoryRecordWithPoints? = error("unused")
        override suspend fun getHistoryCount(): Int = records.size
        override suspend fun insertRecords(records: List<HistoryRecordEntity>) = Unit
        override suspend fun insertRecord(record: HistoryRecordEntity) = Unit
        override suspend fun insertPoints(points: List<HistoryPointEntity>) = Unit
        override suspend fun getMaxHistoryId(): Long? = records.maxOfOrNull { it.historyId }
        override suspend fun updateTitle(historyId: Long, title: String?) = Unit
        override suspend fun deletePointsForHistory(historyId: Long) = Unit
        override suspend fun deleteRecordById(historyId: Long) = Unit
        override suspend fun deletePointsForHistoryList(historyIds: List<Long>) = Unit
        override suspend fun deleteRecordsByIds(historyIds: List<Long>) = Unit
        override suspend fun deleteAllPoints() = Unit
        override suspend fun deleteAllRecords() = Unit
    }
}
