package com.wenhao.record.data.local.stream

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ContinuousTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawPoint(entity: RawLocationPointEntity): Long

    @Query(
        """
        SELECT * FROM raw_location_point
        WHERE pointId > :afterPointId
        ORDER BY pointId ASC
        LIMIT :limit
        """
    )
    suspend fun loadRawPoints(afterPointId: Long, limit: Int): List<RawLocationPointEntity>


    @Query(
        """
        SELECT * FROM raw_location_point
        WHERE timestampMillis BETWEEN :startMillis AND :endMillis
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    suspend fun loadRawPointsBetween(startMillis: Long, endMillis: Long, limit: Int): List<RawLocationPointEntity>

    @Query("SELECT COUNT(*) FROM raw_location_point")
    suspend fun countRawPoints(): Int

    @Query(
        """
        SELECT * FROM analysis_segment
        WHERE segmentId > :afterSegmentId
        ORDER BY segmentId ASC
        LIMIT :limit
        """
    )
    suspend fun loadAnalysisSegments(afterSegmentId: Long, limit: Int): List<AnalysisSegmentEntity>

    @Query("SELECT COUNT(*) FROM analysis_segment")
    suspend fun countAnalysisSegments(): Int

    @Query(
        """
        SELECT * FROM stay_cluster
        WHERE segmentId IN (:segmentIds)
        ORDER BY segmentId ASC, stayId ASC
        """
    )
    suspend fun loadStayClustersForSegments(segmentIds: List<Long>): List<StayClusterEntity>

    @Query(
        """
        SELECT * FROM analysis_cursor
        WHERE cursorId = 1
        LIMIT 1
        """
    )
    suspend fun loadAnalysisCursor(): AnalysisCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalysisCursor(entity: AnalysisCursorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysisSegments(entities: List<AnalysisSegmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStayClusters(entities: List<StayClusterEntity>)

    @Query(
        """
        SELECT * FROM upload_cursor
        WHERE cursorType = :cursorType
        LIMIT 1
        """
    )
    suspend fun loadUploadCursor(cursorType: String): UploadCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUploadCursor(entity: UploadCursorEntity)

    @Query("DELETE FROM raw_location_point WHERE pointId <= :upToPointId")
    suspend fun deleteRawPointsUpTo(upToPointId: Long)
}
