package com.wenhao.record.data.local.auto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface AutoTrackDao {

    @Query("SELECT * FROM auto_track_session WHERE sessionId = 1")
    fun getSessionEntity(): AutoTrackSessionEntity?

    @Query("SELECT * FROM auto_track_point WHERE sessionId = 1 ORDER BY pointOrder ASC")
    fun getSessionPoints(): List<AutoTrackPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSession(entity: AutoTrackSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSessionPoints(points: List<AutoTrackPointEntity>)

    @Query("DELETE FROM auto_track_point WHERE sessionId = 1")
    fun deleteSessionPoints()

    @Query("DELETE FROM auto_track_session WHERE sessionId = 1")
    fun deleteSession()

    @Query("SELECT COUNT(*) FROM auto_track_session")
    fun getSessionCount(): Int

    @Transaction
    fun replaceSession(
        entity: AutoTrackSessionEntity,
        points: List<AutoTrackPointEntity>
    ) {
        upsertSession(entity)
        deleteSessionPoints()
        if (points.isNotEmpty()) {
            insertSessionPoints(points)
        }
    }
}
