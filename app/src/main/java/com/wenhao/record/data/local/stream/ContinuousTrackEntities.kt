package com.wenhao.record.data.local.stream

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_location_point",
    indices = [
        Index(value = ["timestampMillis"]),
        Index(value = ["pointId", "timestampMillis"]),
    ],
)
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

@Entity(
    tableName = "analysis_segment",
    indices = [
        Index(value = ["startTimestamp"]),
        Index(value = ["endTimestamp"]),
    ],
)
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

@Entity(
    tableName = "stay_cluster",
    indices = [Index(value = ["segmentId"])],
)
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

@Entity(tableName = "upload_cursor")
data class UploadCursorEntity(
    @PrimaryKey
    val cursorType: String,
    val lastUploadedId: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "today_display_point",
    indices = [Index(value = ["dayStartMillis", "timestampMillis"])],
)
data class TodayDisplayPointEntity(
    @PrimaryKey
    val timestampMillis: Long,
    val dayStartMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
)

@Entity(
    tableName = "today_session",
    indices = [
        Index(value = ["dayStartMillis", "status", "startedAt"]),
        Index(value = ["status", "dayStartMillis"]),
    ],
)
data class TodaySessionEntity(
    @PrimaryKey
    val sessionId: String,
    val dayStartMillis: Long,
    val status: String,
    val startedAt: Long,
    val lastPointAt: Long?,
    val endedAt: Long?,
    val lastSyncedAt: Long?,
    val syncState: String,
    val phase: String?,
    val anchorPointRef: Long?,
    val recoveredFromRemote: Boolean,
    val updatedAt: Long,
)

@Entity(
    tableName = "today_session_point",
    primaryKeys = ["sessionId", "pointId"],
    indices = [
        Index(value = ["sessionId", "timestampMillis"]),
        Index(value = ["sessionId", "syncState", "timestampMillis"]),
    ],
)
data class TodaySessionPointEntity(
    val sessionId: String,
    val pointId: Long,
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
    val phase: String,
    val syncState: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["itemType", "itemKey"], unique = true),
        Index(value = ["status", "updatedAt"]),
    ],
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val outboxId: Long = 0,
    val itemType: String,
    val itemKey: String,
    val status: String,
    val retryCount: Int,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
