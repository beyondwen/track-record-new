package com.wenhao.record.data.local.stream

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ContinuousTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRawPoint(entity: RawLocationPointEntity): Long

    @Query(
        """
        SELECT * FROM raw_location_point
        WHERE pointId > :afterPointId
        ORDER BY pointId ASC
        LIMIT :limit
        """
    )
    fun loadRawPoints(afterPointId: Long, limit: Int): List<RawLocationPointEntity>
}
