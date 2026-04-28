package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrackPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.LazyThreadSafetyMode

data class HistoryItem(
    val id: Long,
    val timestamp: Long,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averageSpeedKmh: Double,
    val title: String? = null,
    val points: List<TrackPoint> = emptyList(),
    val startSource: TrackRecordSource = TrackRecordSource.UNKNOWN,
    val stopSource: TrackRecordSource = TrackRecordSource.UNKNOWN,
    val manualStartAt: Long? = null,
    val manualStopAt: Long? = null,
) {
    val quality: TrackQuality by lazy(LazyThreadSafetyMode.NONE) {
        TrackQualityEvaluator.evaluateSegments(
            segments = listOf(points),
            totalDistanceKm = distanceKm,
            totalDurationSeconds = durationSeconds,
        )
    }

    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: "行程 #${id.toString().padStart(2, '0')}"

    val formattedTime: String
        get() = formatHistoryDate("yyyy年M月d日 HH:mm", timestamp)

    val formattedDateTitle: String
        get() = formatHistoryDate("M月d日 HH:mm", timestamp)

    val formattedDateDetail: String
        get() = formatHistoryDate("yyyy年M月d日 HH:mm:ss", timestamp)

    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }

    val formattedDurationDetail: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            return when {
                hours > 0 -> "${hours}小时${minutes}分钟"
                minutes > 0 -> "${minutes}分钟"
                else -> "${durationSeconds}秒"
            }
        }

    val formattedDistance: String
        get() = String.format(Locale.CHINA, "%.2f 公里", distanceKm)

    val formattedSpeed: String
        get() = String.format(Locale.CHINA, "%.1f 公里/小时", averageSpeedKmh)

    val summary: String
        get() = "${formattedDistance} · ${formattedDuration} · 均速 ${formattedSpeed}"

    val pointCountLabel: String
        get() = "${points.size} 个点"
}

private fun formatHistoryDate(pattern: String, timestamp: Long): String {
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(timestamp))
}
