package com.wenhao.record.data.history

import androidx.compose.runtime.Immutable
import com.wenhao.record.data.tracking.TrackPathSanitizer
import com.wenhao.record.data.tracking.TrackPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Immutable
data class HistoryDayItem(
    val dayStartMillis: Long,
    val latestTimestamp: Long,
    val sessionCount: Int,
    val totalDistanceKm: Double,
    val totalDurationSeconds: Int,
    val averageSpeedKmh: Double,
    val sourceIds: List<Long>,
    val segments: List<List<TrackPoint>>,
    val pointCount: Int,
    val quality: TrackQuality,
    val displayTitle: String,
    val formattedDateTitle: String,
    val formattedLatestTime: String,
    val formattedDuration: String,
    val formattedDurationDetail: String,
    val formattedDistance: String,
    val formattedSpeed: String,
    val formattedDateDetail: String,
    val sessionCountLabel: String,
    val summary: String,
    val pointCountLabel: String,
)

object HistoryDayAggregator {

    fun aggregate(items: List<HistoryItem>): List<HistoryDayItem> {
        if (items.isEmpty()) return emptyList()

        return items
            .groupBy { item -> startOfDay(item.timestamp) }
            .map { (dayStartMillis, dayItems) ->
                val sortedItems = dayItems.sortedBy { it.timestamp }
                val sanitizedTracks = sortedItems.map { item ->
                    TrackPathSanitizer.sanitize(item.points, sortByTimestamp = true)
                }
                val totalDistanceKm = sanitizedTracks.sumOf { sanitized ->
                    sanitized.totalDistanceKm
                }
                val totalDurationSeconds = sortedItems.sumOf { it.durationSeconds }
                val segments = sanitizedTracks.flatMap { sanitized ->
                    sanitized.segments.map { segment -> segment.toList() }
                }
                val latestTimestamp = sortedItems.maxOf { it.timestamp }
                val averageSpeedKmh = if (totalDurationSeconds > 0) {
                    totalDistanceKm / (totalDurationSeconds / 3600.0)
                } else {
                    0.0
                }

                buildHistoryDayItem(
                    dayStartMillis = dayStartMillis,
                    latestTimestamp = latestTimestamp,
                    sessionCount = sortedItems.size,
                    totalDistanceKm = totalDistanceKm,
                    totalDurationSeconds = totalDurationSeconds,
                    averageSpeedKmh = averageSpeedKmh,
                    sourceIds = sortedItems.map { it.id },
                    segments = segments,
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

internal fun buildHistoryDayItem(
    dayStartMillis: Long,
    latestTimestamp: Long,
    sessionCount: Int,
    totalDistanceKm: Double,
    totalDurationSeconds: Int,
    averageSpeedKmh: Double,
    sourceIds: List<Long>,
    segments: List<List<TrackPoint>>,
): HistoryDayItem {
    val pointCount = segments.sumOf { segment -> segment.size }
    val quality = buildHistoryQuality(
        segments = segments,
        totalDistanceKm = totalDistanceKm,
        totalDurationSeconds = totalDurationSeconds,
    )
    val displayTitle = formatHistoryDisplayTitle(dayStartMillis)
    val formattedDateTitle = formatHistoryDateTitle(dayStartMillis)
    val formattedLatestTime = formatHistoryLatestTime(latestTimestamp)
    val formattedDuration = formatHistoryDuration(totalDurationSeconds)
    val formattedDurationDetail = formatHistoryDurationDetail(totalDurationSeconds)
    val formattedDistance = formatHistoryDistance(totalDistanceKm)
    val formattedSpeed = formatHistorySpeed(averageSpeedKmh)

    return HistoryDayItem(
        dayStartMillis = dayStartMillis,
        latestTimestamp = latestTimestamp,
        sessionCount = sessionCount,
        totalDistanceKm = totalDistanceKm,
        totalDurationSeconds = totalDurationSeconds,
        averageSpeedKmh = averageSpeedKmh,
        sourceIds = sourceIds,
        segments = segments,
        pointCount = pointCount,
        quality = quality,
        displayTitle = displayTitle,
        formattedDateTitle = formattedDateTitle,
        formattedLatestTime = formattedLatestTime,
        formattedDuration = formattedDuration,
        formattedDurationDetail = formattedDurationDetail,
        formattedDistance = formattedDistance,
        formattedSpeed = formattedSpeed,
        formattedDateDetail = buildHistoryDateDetail(
            displayTitle = displayTitle,
            sessionCount = sessionCount,
        ),
        sessionCountLabel = buildHistorySessionCountLabel(
            sessionCount = sessionCount,
            formattedLatestTime = formattedLatestTime,
        ),
        summary = buildHistorySummary(
            formattedDistance = formattedDistance,
            formattedDuration = formattedDuration,
            formattedSpeed = formattedSpeed,
        ),
        pointCountLabel = buildHistoryPointCountLabel(
            sessionCount = sessionCount,
            pointCount = pointCount,
        ),
    )
}

private val displayTitleFormatter = formatter("yyyy年M月d日")
private val dateTitleFormatter = formatter("M月d日 EEEE")
private val latestTimeFormatter = formatter("HH:mm")

private fun formatter(pattern: String) = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat(pattern, Locale.CHINA)
}

private fun buildHistoryQuality(
    segments: List<List<TrackPoint>>,
    totalDistanceKm: Double,
    totalDurationSeconds: Int,
): TrackQuality {
    return TrackQualityEvaluator.evaluateSegments(
        segments = segments,
        totalDistanceKm = totalDistanceKm,
        totalDurationSeconds = totalDurationSeconds,
    )
}

private fun formatHistoryDisplayTitle(timestamp: Long): String {
    return displayTitleFormatter.get()!!.format(Date(timestamp))
}

private fun formatHistoryDateTitle(timestamp: Long): String {
    return dateTitleFormatter.get()!!.format(Date(timestamp))
}

private fun formatHistoryLatestTime(timestamp: Long): String {
    return latestTimeFormatter.get()!!.format(Date(timestamp))
}

private fun formatHistoryDuration(totalDurationSeconds: Int): String {
    val hours = totalDurationSeconds / 3600
    val minutes = (totalDurationSeconds % 3600) / 60
    val seconds = totalDurationSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun formatHistoryDurationDetail(totalDurationSeconds: Int): String {
    val hours = totalDurationSeconds / 3600
    val minutes = (totalDurationSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分钟"
        minutes > 0 -> "${minutes}分钟"
        else -> "${totalDurationSeconds}秒"
    }
}

private fun formatHistoryDistance(totalDistanceKm: Double): String {
    return String.format(Locale.CHINA, "%.2f 公里", totalDistanceKm)
}

private fun formatHistorySpeed(averageSpeedKmh: Double): String {
    return String.format(Locale.CHINA, "%.1f 公里/小时", averageSpeedKmh)
}

private fun buildHistoryDateDetail(
    displayTitle: String,
    sessionCount: Int,
): String {
    return "$displayTitle · $sessionCount 段"
}

private fun buildHistorySessionCountLabel(
    sessionCount: Int,
    formattedLatestTime: String,
): String {
    return "$sessionCount 段 · 最近 $formattedLatestTime"
}

private fun buildHistorySummary(
    formattedDistance: String,
    formattedDuration: String,
    formattedSpeed: String,
): String {
    return "$formattedDistance · $formattedDuration · 均速 $formattedSpeed"
}

private fun buildHistoryPointCountLabel(
    sessionCount: Int,
    pointCount: Int,
): String {
    return "$sessionCount 段 · $pointCount 个点"
}
