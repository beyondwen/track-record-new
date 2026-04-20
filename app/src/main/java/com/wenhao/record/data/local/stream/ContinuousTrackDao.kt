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

    @Query(
        """
        SELECT * FROM analysis_segment
        WHERE segmentId > :afterSegmentId
        ORDER BY segmentId ASC
        LIMIT :limit
        """
    )
    fun loadAnalysisSegments(afterSegmentId: Long, limit: Int): List<AnalysisSegmentEntity>

    @Query(
        """
        SELECT * FROM stay_cluster
        WHERE segmentId IN (:segmentIds)
        ORDER BY segmentId ASC, stayId ASC
        """
    )
    fun loadStayClustersForSegments(segmentIds: List<Long>): List<StayClusterEntity>

    @Query(
        """
        SELECT * FROM analysis_cursor
        WHERE cursorId = 1
        LIMIT 1
        """
    )
    fun loadAnalysisCursor(): AnalysisCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAnalysisCursor(entity: AnalysisCursorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAnalysisSegments(entities: List<AnalysisSegmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStayClusters(entities: List<StayClusterEntity>)

    @Query(
        """
        SELECT * FROM upload_cursor
        WHERE cursorType = :cursorType
        LIMIT 1
        """
    )
    fun loadUploadCursor(cursorType: String): UploadCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertUploadCursor(entity: UploadCursorEntity)

    @Query("DELETE FROM raw_location_point WHERE pointId <= :upToPointId")
    fun deleteRawPointsUpTo(upToPointId: Long)
}
