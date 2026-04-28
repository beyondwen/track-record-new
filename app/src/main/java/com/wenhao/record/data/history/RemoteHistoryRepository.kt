package com.wenhao.record.data.history

import android.content.Context
import com.wenhao.record.data.tracking.RawTrackPoint
import com.wenhao.record.data.tracking.RemoteRawPointDaySummary
import com.wenhao.record.data.tracking.RemoteRawPointDaySummaryReadResult
import com.wenhao.record.data.tracking.RemoteRawPointReadResult
import com.wenhao.record.data.tracking.RemoteRawPointReadService
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.uploadDeviceId
import com.wenhao.record.map.GeoMath
import java.util.TimeZone

class RemoteHistoryRepository(
    private val rawDaySummaryLoader: (TrainingSampleUploadConfig, String, Int) -> RemoteRawPointDaySummaryReadResult = { config, deviceId, utcOffsetMinutes ->
        RemoteRawPointReadService().loadDays(config, deviceId, utcOffsetMinutes)
    },
    private val rawPointDayLoader: (TrainingSampleUploadConfig, String, Long) -> RemoteRawPointReadResult = { config, deviceId, dayStartMillis ->
        RemoteRawPointReadService().loadByDay(config, deviceId, dayStartMillis)
    },
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
) {
    data class DailySummaryLoadResult(
        val items: List<HistoryDaySummaryItem>,
        val remoteStatus: RemoteStatus,
    )

    enum class RemoteStatus {
        DISABLED,
        SUCCESS,
        FAILURE,
    }

    suspend fun loadMergedDailySummaries(context: Context): List<HistoryDaySummaryItem> {
        return loadDailySummaryState(context).items
    }

    suspend fun loadDailySummaryState(context: Context): DailySummaryLoadResult {
        val config = configLoader(context)
        if (!config.isConfigured()) {
            return DailySummaryLoadResult(
                items = emptyList(),
                remoteStatus = RemoteStatus.DISABLED,
            )
        }

        val utcOffsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()).div(60_000)
        return when (val result = rawDaySummaryLoader(config, deviceIdProvider(context), utcOffsetMinutes)) {
            is RemoteRawPointDaySummaryReadResult.Success -> DailySummaryLoadResult(
                items = result.days.map(::rawDaySummaryToHistorySummary).sortedByDescending { it.dayStartMillis },
                remoteStatus = RemoteStatus.SUCCESS,
            )
            is RemoteRawPointDaySummaryReadResult.Failure -> DailySummaryLoadResult(
                items = emptyList(),
                remoteStatus = RemoteStatus.FAILURE,
            )
        }
    }

    suspend fun loadDay(context: Context, dayStartMillis: Long): HistoryDayItem? {
        val config = configLoader(context)
        if (!config.isConfigured()) {
            return null
        }

        return when (
            val result = rawPointDayLoader(
                config,
                deviceIdProvider(context),
                dayStartMillis,
            )
        ) {
            is RemoteRawPointReadResult.Success -> rawPointsToHistoryDayItem(dayStartMillis, result.points)

            is RemoteRawPointReadResult.Failure -> null
        }
    }

    suspend fun deleteDay(
        context: Context,
        item: HistoryDaySummaryItem,
    ): Boolean {
        return true
    }

    private fun TrainingSampleUploadConfig.isConfigured(): Boolean {
        return workerBaseUrl.isNotBlank() && uploadToken.isNotBlank()
    }

    private fun rawDaySummaryToHistorySummary(summary: RemoteRawPointDaySummary): HistoryDaySummaryItem {
        return HistoryDaySummaryItem(
            dayStartMillis = summary.dayStartMillis,
            latestTimestamp = summary.lastPointAt,
            sessionCount = 1,
            totalDistanceKm = summary.totalDistanceKm,
            totalDurationSeconds = summary.totalDurationSeconds,
            averageSpeedKmh = summary.averageSpeedKmh,
            sourceIds = emptyList(),
        )
    }

    private fun rawPointsToHistoryDayItem(
        dayStartMillis: Long,
        rawPoints: List<RawTrackPoint>,
    ): HistoryDayItem? {
        val points = rawPoints
            .sortedBy { point -> point.timestampMillis }
            .map { point ->
                TrackPoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    timestampMillis = point.timestampMillis,
                    accuracyMeters = point.accuracyMeters,
                    altitudeMeters = point.altitudeMeters,
                    wgs84Latitude = point.latitude,
                    wgs84Longitude = point.longitude,
                )
            }
        if (points.size < 2) return null

        val durationSeconds = ((points.last().timestampMillis - points.first().timestampMillis)
            .coerceAtLeast(0L) / 1_000L).toInt()
        val distanceKm = points.zipWithNext { first, second ->
            GeoMath.distanceMeters(first, second).toDouble()
        }.sum() / 1_000.0
        val averageSpeedKmh = if (durationSeconds > 0) {
            distanceKm / (durationSeconds / 3_600.0)
        } else {
            0.0
        }

        return buildHistoryDayItem(
            dayStartMillis = dayStartMillis,
            latestTimestamp = points.last().timestampMillis,
            sessionCount = 1,
            totalDistanceKm = distanceKm,
            totalDurationSeconds = durationSeconds,
            averageSpeedKmh = averageSpeedKmh,
            sourceIds = emptyList(),
            segments = listOf(points),
        )
    }
}
