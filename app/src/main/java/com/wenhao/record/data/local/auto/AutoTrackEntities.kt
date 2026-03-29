package com.wenhao.record.data.local.auto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auto_track_session")
data class AutoTrackSessionEntity(
    @PrimaryKey
    val sessionId: Int = 1,
    val startTimestamp: Long,
    val lastMotionTimestamp: Long,
    val totalDistanceKm: Double,
    val lastLatitude: Double?,
    val lastLongitude: Double?
)

@Entity(
    tableName = "auto_track_point",
    primaryKeys = ["sessionId", "pointOrder"]
)
data class AutoTrackPointEntity(
    val sessionId: Int = 1,
    val pointOrder: Int,
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Float?,
    val altitudeMeters: Double? = null,
    val wgs84Latitude: Double? = null,
    val wgs84Longitude: Double? = null
)
