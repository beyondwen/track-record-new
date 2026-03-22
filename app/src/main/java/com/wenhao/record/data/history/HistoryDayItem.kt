package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrackPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.LazyThreadSafetyMode

data class HistoryDayItem(
    val dayStartMillis: Long,
    val latestTimestamp: Long,
    val sessionCount: Int,
    val totalDistanceKm: Double,
    val totalDurationSeconds: Int,
    val averageSpeedKmh: Double,
    val sourceIds: List<Long>,
    val segments: List<List<TrackPoint>>,
    val points: List<TrackPoint>
) {
    val quality: TrackQuality by lazy(LazyThreadSafetyMode.NONE) {
        TrackQualityEvaluator.evaluateSegments(
            segments = segments,
            totalDistanceKm = totalDistanceKm,
            totalDurationSeconds = totalDurationSeconds
        )
    }

    val displayTitle: String
        get() = formatDayDate("yyyy年M月d日", dayStartMillis)

    val formattedDateTitle: String
        get() = formatDayDate("M月d日 EEEE", dayStartMillis)

    val formattedDateDetail: String
        get() = "$displayTitle · 共 ${sessionCount} 段行程"

    val sessionCountLabel: String
        get() = "共 ${sessionCount} 段行程，最近 ${formattedLatestTime}"

    val formattedLatestTime: String
        get() = formatDayDate("HH:mm", latestTimestamp)

    val formattedDuration: String
        get() {
            val hours = totalDurationSeconds / 3600
            val minutes = (totalDurationSeconds % 3600) / 60
            val seconds = totalDurationSeconds % 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }

    val formattedDurationDetail: String
        get() {
            val hours = totalDurationSeconds / 3600
            val minutes = (totalDurationSeconds % 3600) / 60
            return when {
                hours > 0 -> "${hours}小时${minutes}分钟"
                minutes > 0 -> "${minutes}分钟"
                else -> "${totalDurationSeconds}秒"
            }
        }

    val formattedDistance: String
        get() = String.format(Locale.CHINA, "%.2f 公里", totalDistanceKm)

    val formattedSpeed: String
        get() = String.format(Locale.CHINA, "%.1f 公里/小时", averageSpeedKmh)

    val summary: String
        get() = "${formattedDistance} / ${formattedDuration} / 平均 ${formattedSpeed}"

    val pointCountLabel: String
        get() = "共 ${sessionCount} 段行程 · ${points.size} 个定位点"
}

object HistoryDayAggregator {

    fun aggregate(items: List<HistoryItem>): List<HistoryDayItem> {
        if (items.isEmpty()) return emptyList()

        return items
            .groupBy { item -> startOfDay(item.timestamp) }
            .map { (dayStartMillis, dayItems) ->
                val sortedItems = dayItems.sortedBy { it.timestamp }
                val totalDistanceKm = sortedItems.sumOf { it.distanceKm }
                val totalDurationSeconds = sortedItems.sumOf { it.durationSeconds }
                val segments = sortedItems
                    .map { item -> item.points.toList() }
                    .filter { segment -> segment.isNotEmpty() }

                HistoryDayItem(
                    dayStartMillis = dayStartMillis,
                    latestTimestamp = sortedItems.maxOf { it.timestamp },
                    sessionCount = sortedItems.size,
                    totalDistanceKm = totalDistanceKm,
                    totalDurationSeconds = totalDurationSeconds,
                    averageSpeedKmh = if (totalDurationSeconds > 0) {
                        totalDistanceKm / (totalDurationSeconds / 3600.0)
                    } else {
                        0.0
                    },
                    sourceIds = sortedItems.map { it.id },
                    segments = segments,
                    points = segments.flatten()
                )
            }
            .sortedByDescending { it.dayStartMillis }
    }

    fun startOfDay(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

private fun formatDayDate(pattern: String, timestamp: Long): String {
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(timestamp))
}
