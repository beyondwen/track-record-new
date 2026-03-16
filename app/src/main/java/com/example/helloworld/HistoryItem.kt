package com.example.helloworld

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val id: Long,
    val timestamp: Long,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averageSpeedKmh: Double,
    val title: String? = null
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: "轨迹 #${id.toString().padStart(2, '0')}"

    val formattedTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

    val formattedDateTitle: String
        get() = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(timestamp))

    val formattedDateDetail: String
        get() = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

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
        get() = String.format(Locale.getDefault(), "%.2f km", distanceKm)

    val formattedSpeed: String
        get() = String.format(Locale.getDefault(), "%.1f km/h", averageSpeedKmh)

    val summary: String
        get() = "${formattedDistance} / ${formattedDuration} / 均速 ${formattedSpeed}"
}
