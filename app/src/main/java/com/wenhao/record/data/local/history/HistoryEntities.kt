package com.wenhao.record.data.local.history

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history_record",
    indices = [Index(value = ["dateKey", "timestamp"])],
)
data class HistoryRecordEntity(
    @PrimaryKey
    val historyId: Long,
    val sourceSessionId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val dateKey: Long = 0L,
    val timestamp: Long,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averageSpeedKmh: Double,
    val title: String?,
    @ColumnInfo(defaultValue = "'SYNCED'")
    val syncState: String = "SYNCED",
    @ColumnInfo(defaultValue = "1")
    val version: Long = 1L,
    val startSource: String,
    val stopSource: String,
    val manualStartAt: Long?,
    val manualStopAt: Long?,
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
