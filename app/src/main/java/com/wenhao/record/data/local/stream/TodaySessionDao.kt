package com.wenhao.record.data.local.stream

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TodaySessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: TodaySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPoint(entity: TodaySessionPointEntity)

    @Query(
        """
        SELECT * FROM today_session
        WHERE dayStartMillis = :dayStartMillis
          AND status IN (:openStates)
        ORDER BY startedAt DESC
        LIMIT 1
        """
    )
    suspend fun loadOpenSession(dayStartMillis: Long, openStates: List<String>): TodaySessionEntity?

    @Query("SELECT * FROM today_session WHERE sessionId = :sessionId LIMIT 1")
    suspend fun loadSession(sessionId: String): TodaySessionEntity?

    @Query(
        """
        SELECT * FROM today_session_point
        WHERE sessionId = :sessionId
        ORDER BY timestampMillis ASC
        """
    )
    suspend fun loadPoints(sessionId: String): List<TodaySessionPointEntity>

    @Query(
        """
        SELECT * FROM today_session_point
        WHERE sessionId = :sessionId
          AND syncState = :syncState
        ORDER BY timestampMillis ASC
        LIMIT :limit
        """
    )
    suspend fun loadPointsBySyncState(
        sessionId: String,
        syncState: String,
        limit: Int,
    ): List<TodaySessionPointEntity>

    @Query(
        """
        UPDATE today_session_point
        SET syncState = :syncState
        WHERE sessionId = :sessionId
          AND pointId IN (:pointIds)
        """
    )
    suspend fun updatePointSyncState(
        sessionId: String,
        pointIds: List<Long>,
        syncState: String,
    )

    @Query("DELETE FROM today_session_point WHERE sessionId = :sessionId")
    suspend fun deletePointsForSession(sessionId: String)

    @Query(
        """
        DELETE FROM today_session
        WHERE status = :status
          AND dayStartMillis < :minDayStartMillis
        """
    )
    suspend fun deleteSessionsByStatusBefore(status: String, minDayStartMillis: Long)
}
