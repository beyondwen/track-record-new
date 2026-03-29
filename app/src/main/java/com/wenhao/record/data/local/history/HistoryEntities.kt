package com.wenhao.record.data.local.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_record")
data class HistoryRecordEntity(
    @PrimaryKey
    val historyId: Long,
    val timestamp: Long,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averageSpeedKmh: Double,
    val title: String?
)

@Entity(
    tableName = "history_point",
    primaryKeys = ["historyId", "pointOrder"]
)
data class HistoryPointEntity(
    val historyId: Long,
    val pointOrder: Int,
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Float?,
    val altitudeMeters: Double? = null,
    val wgs84Latitude: Double? = null,
    val wgs84Longitude: Double? = null
)
