package com.wenhao.record.data.history

import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class HistoryDaySummaryItem(
    val dayStartMillis: Long,
    val latestTimestamp: Long,
    val sessionCount: Int,
    val totalDistanceKm: Double,
    val totalDurationSeconds: Int,
    val averageSpeedKmh: Double,
    val sourceIds: List<Long> = emptyList(),
    val routeTitle: String? = null,
) {
    val displayTitle: String
        get() = summaryDisplayTitleFormatter.get()!!.format(Date(dayStartMillis))

    val formattedDateTitle: String
        get() = summaryDateTitleFormatter.get()!!.format(Date(dayStartMillis))

    val formattedLatestTime: String
        get() = summaryLatestTimeFormatter.get()!!.format(Date(latestTimestamp))

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

    val formattedDistance: String
        get() = String.format(Locale.CHINA, "%.2f 公里", totalDistanceKm)

    val formattedSpeed: String
        get() = String.format(Locale.CHINA, "%.1f 公里/小时", averageSpeedKmh)

    val summary: String
        get() = "$formattedDistance · $formattedDuration · 均速 $formattedSpeed"

    val sessionCountLabel: String
        get() = "$sessionCount 段"

    val metaLabel: String
        get() = "$formattedDateTitle · 最近 $formattedLatestTime"
}

fun HistoryDayItem.toSummaryItem(routeTitleOverride: String? = routeTitle): HistoryDaySummaryItem {
    return HistoryDaySummaryItem(
        dayStartMillis = dayStartMillis,
        latestTimestamp = latestTimestamp,
        sessionCount = sessionCount,
        totalDistanceKm = totalDistanceKm,
        totalDurationSeconds = totalDurationSeconds,
        averageSpeedKmh = averageSpeedKmh,
        sourceIds = sourceIds,
        routeTitle = routeTitleOverride,
    )
}

private val summaryDisplayTitleFormatter = formatter("yyyy年M月d日")
private val summaryDateTitleFormatter = formatter("M月d日 EEEE")
private val summaryLatestTimeFormatter = formatter("HH:mm")

private fun formatter(pattern: String) = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat(pattern, Locale.CHINA)
}
