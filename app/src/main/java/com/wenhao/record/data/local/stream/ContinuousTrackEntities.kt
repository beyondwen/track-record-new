package com.wenhao.record.data.local.stream

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_location_point")
data class RawLocationPointEntity(
    @PrimaryKey(autoGenerate = true)
    val pointId: Long = 0,
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val provider: String,
    val sourceType: String,
    val isMock: Boolean,
    val wifiFingerprintDigest: String?,
    val activityType: String?,
    val activityConfidence: Float?,
    val samplingTier: String,
)

@Entity(tableName = "analysis_segment")
data class AnalysisSegmentEntity(
    @PrimaryKey
    val segmentId: Long,
    val startPointId: Long,
    val endPointId: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val segmentType: String,
    val confidence: Float,
    val distanceMeters: Float,
    val durationMillis: Long,
    val avgSpeedMetersPerSecond: Float,
    val maxSpeedMetersPerSecond: Float,
    val analysisVersion: Int,
)

@Entity(tableName = "stay_cluster")
data class StayClusterEntity(
    @PrimaryKey
    val stayId: Long,
    val segmentId: Long,
    val centerLat: Double,
    val centerLng: Double,
    val radiusMeters: Float,
    val arrivalTime: Long,
    val departureTime: Long,
    val confidence: Float,
    val analysisVersion: Int,
)

@Entity(tableName = "analysis_cursor")
data class AnalysisCursorEntity(
    @PrimaryKey
    val cursorId: Int = 1,
    val lastAnalyzedPointId: Long,
    val updatedAt: Long,
)
