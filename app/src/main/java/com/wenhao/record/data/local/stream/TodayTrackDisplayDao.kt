package com.wenhao.record.data.local.stream

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodayTrackDisplayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPoint(entity: TodayDisplayPointEntity)

    @Query(
        """
        SELECT * FROM today_display_point
        WHERE dayStartMillis = :dayStartMillis
        ORDER BY timestampMillis ASC
        """
    )
    suspend fun loadPointsForDay(dayStartMillis: Long): List<TodayDisplayPointEntity>

    @Query(
        """
        SELECT * FROM today_display_point
        WHERE dayStartMillis = :dayStartMillis
        ORDER BY timestampMillis ASC
        """
    )
    fun observePointsForDay(dayStartMillis: Long): Flow<List<TodayDisplayPointEntity>>

    @Query("SELECT COUNT(*) FROM today_display_point WHERE dayStartMillis = :dayStartMillis")
    suspend fun countPointsForDay(dayStartMillis: Long): Int

    @Query("DELETE FROM today_display_point WHERE dayStartMillis != :dayStartMillis")
    suspend fun deleteExceptDay(dayStartMillis: Long)

    @Query(
        """
        DELETE FROM today_display_point
        WHERE dayStartMillis = :dayStartMillis
          AND timestampMillis NOT IN (
              SELECT timestampMillis FROM today_display_point
              WHERE dayStartMillis = :dayStartMillis
              ORDER BY timestampMillis DESC
              LIMIT :maxPoints
          )
        """
    )
    suspend fun trimDayToNewest(dayStartMillis: Long, maxPoints: Int)
}
