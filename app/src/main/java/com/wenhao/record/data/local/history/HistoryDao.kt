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

    @Transaction
    @Query("SELECT * FROM history_record ORDER BY timestamp DESC, historyId DESC")
    fun getHistoryWithPoints(): List<HistoryRecordWithPoints>

    @Transaction
    @Query("SELECT * FROM history_record WHERE historyId = :historyId LIMIT 1")
    fun getHistoryWithPoints(historyId: Long): HistoryRecordWithPoints?

    @Query("SELECT COUNT(*) FROM history_record")
    fun getHistoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecords(records: List<HistoryRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecord(record: HistoryRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPoints(points: List<HistoryPointEntity>)

    @Query("SELECT MAX(historyId) FROM history_record")
    fun getMaxHistoryId(): Long?

    @Query("UPDATE history_record SET title = :title WHERE historyId = :historyId")
    fun updateTitle(historyId: Long, title: String?)

    @Query("DELETE FROM history_point WHERE historyId = :historyId")
    fun deletePointsForHistory(historyId: Long)

    @Query("DELETE FROM history_record WHERE historyId = :historyId")
    fun deleteRecordById(historyId: Long)

    @Query("DELETE FROM history_point WHERE historyId IN (:historyIds)")
    fun deletePointsForHistoryList(historyIds: List<Long>)

    @Query("DELETE FROM history_record WHERE historyId IN (:historyIds)")
    fun deleteRecordsByIds(historyIds: List<Long>)

    @Query("DELETE FROM history_point")
    fun deleteAllPoints()

    @Query("DELETE FROM history_record")
    fun deleteAllRecords()

    @Transaction
    fun replaceAll(
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
    fun upsertHistory(
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
    fun deleteHistory(historyId: Long) {
        deletePointsForHistory(historyId)
        deleteRecordById(historyId)
    }

    @Transaction
    fun deleteHistoryList(historyIds: List<Long>) {
        if (historyIds.isEmpty()) return
        deletePointsForHistoryList(historyIds)
        deleteRecordsByIds(historyIds)
    }
}
