package com.example.helloworld.data.history

import com.example.helloworld.data.tracking.TrackPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val id: Long,
    val timestamp: Long,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averageSpeedKmh: Double,
    val title: String? = null,
    val points: List<TrackPoint> = emptyList()
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: "行程 #${id.toString().padStart(2, '0')}"

    val formattedTime: String
        get() = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date(timestamp))

    val formattedDateTitle: String
        get() = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(timestamp))

    val formattedDateDetail: String
        get() = SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

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
        get() = String.format(Locale.getDefault(), "%.2f 公里", distanceKm)

    val formattedSpeed: String
        get() = String.format(Locale.getDefault(), "%.1f 公里/小时", averageSpeedKmh)

    val summary: String
        get() = "${formattedDistance} / ${formattedDuration} / 平均${formattedSpeed}"
}
