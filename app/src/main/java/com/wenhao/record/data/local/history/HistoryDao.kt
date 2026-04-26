package com.wenhao.record.data.local.history

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction

data class HistoryRecordWithPoints(
    @Embedded
    val record: HistoryRecordEntity,
    @Relation(
        parentColumn = "historyId",
        entityColumn = "historyId"
    )
    val points: List<HistoryPointEntity>
)

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_record ORDER BY timestamp DESC, historyId DESC")
    suspend fun getHistoryRecords(): List<HistoryRecordEntity>

    @Query("SELECT * FROM history_record WHERE dateKey = :dayStartMillis ORDER BY timestamp ASC, historyId ASC")
    suspend fun getHistoryRecordsByDay(dayStartMillis: Long): List<HistoryRecordEntity>

    @Query("SELECT * FROM history_point WHERE historyId = :historyId ORDER BY pointOrder ASC")
    suspend fun getHistoryPoints(historyId: Long): List<HistoryPointEntity>

    @Transaction
    @Query("SELECT * FROM history_record ORDER BY timestamp DESC, historyId DESC")
    suspend fun getHistoryWithPoints(): List<HistoryRecordWithPoints>

    @Transaction
    @Query("SELECT * FROM history_record WHERE historyId = :historyId LIMIT 1")
    suspend fun getHistoryWithPoints(historyId: Long): HistoryRecordWithPoints?

    @Query("SELECT COUNT(*) FROM history_record")
    suspend fun getHistoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<HistoryRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HistoryRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<HistoryPointEntity>)

    @Query("SELECT MAX(historyId) FROM history_record")
    suspend fun getMaxHistoryId(): Long?

    @Query("UPDATE history_record SET title = :title WHERE historyId = :historyId")
    suspend fun updateTitle(historyId: Long, title: String?)

    @Query("DELETE FROM history_point WHERE historyId = :historyId")
    suspend fun deletePointsForHistory(historyId: Long)

    @Query("DELETE FROM history_record WHERE historyId = :historyId")
    suspend fun deleteRecordById(historyId: Long)

    @Query("DELETE FROM history_point WHERE historyId IN (:historyIds)")
    suspend fun deletePointsForHistoryList(historyIds: List<Long>)

    @Query("DELETE FROM history_record WHERE historyId IN (:historyIds)")
    suspend fun deleteRecordsByIds(historyIds: List<Long>)

    @Query("DELETE FROM history_point")
    suspend fun deleteAllPoints()

    @Query("DELETE FROM history_record")
    suspend fun deleteAllRecords()

    @Transaction
    suspend fun replaceAll(
        records: List<HistoryRecordEntity>,
        points: List<HistoryPointEntity>
    ) {
        deleteAllPoints()
        deleteAllRecords()
        if (records.isNotEmpty()) {
            insertRecords(records)
        }
        if (points.isNotEmpty()) {
            insertPoints(points)
        }
    }

    @Transaction
    suspend fun upsertHistory(
        record: HistoryRecordEntity,
        points: List<HistoryPointEntity>
    ) {
        insertRecord(record)
        deletePointsForHistory(record.historyId)
        if (points.isNotEmpty()) {
            insertPoints(points)
        }
    }

    @Transaction
    suspend fun deleteHistory(historyId: Long) {
        deletePointsForHistory(historyId)
        deleteRecordById(historyId)
    }

    @Transaction
    suspend fun deleteHistoryList(historyIds: List<Long>) {
        if (historyIds.isEmpty()) return
        deletePointsForHistoryList(historyIds)
        deleteRecordsByIds(historyIds)
    }
}
