package com.wenhao.record.ui.barometer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class AltitudeSample(
    val timestampMillis: Long,
    val altitudeMeters: Double,
)

internal object BarometerMetricsFormatter {
    fun formatPrimaryAltitude(altitudeMeters: Double?): String {
        return altitudeMeters?.let { String.format(Locale.CHINA, "%.0f", it) } ?: "--"
    }

    fun formatRelativeRange(samples: List<AltitudeSample>): String {
        if (samples.size < 2) return "--"
        val min = samples.minOf { it.altitudeMeters }
        val max = samples.maxOf { it.altitudeMeters }
        return String.format(Locale.CHINA, "%.0f m", max - min)
    }

    fun formatLocationStatus(
        altitudeMeters: Double?,
        accuracyMeters: Float?,
    ): String {
        if (altitudeMeters == null) return "等待定位"
        return accuracyMeters?.let {
            "精度 ${it.roundToInt()} m"
        } ?: "GPS 海拔"
    }

    fun trendLabel(samples: List<AltitudeSample>): String {
        val delta = altitudeDelta(samples) ?: return "等待定位"
        return when {
            delta >= 12.0 -> "明显上升"
            delta >= 4.0 -> "缓慢上升"
            delta <= -12.0 -> "明显下降"
            delta <= -4.0 -> "缓慢下降"
            else -> "基本平稳"
        }
    }

    fun trendSummary(samples: List<AltitudeSample>): String {
        if (samples.size < 2) {
            return "正在等待连续定位，拿到几笔稳定海拔后会显示高度变化趋势。"
        }
        val first = samples.first()
        val last = samples.last()
        val delta = last.altitudeMeters - first.altitudeMeters
        val minutes = ((last.timestampMillis - first.timestampMillis) / 60_000L).coerceAtLeast(1L)
        val direction = when {
            delta >= 4.0 -> "上升"
            delta <= -4.0 -> "下降"
            else -> "保持平顺"
        }
        return String.format(
            Locale.CHINA,
            "过去 %d 分钟海拔%s %.0f m，适合结合轨迹判断地势变化。",
            minutes,
            direction,
            abs(delta),
        )
    }

    fun terrainLabel(altitudeMeters: Double?): String {
        if (altitudeMeters == null) return "等待定位"
        return when {
            altitudeMeters >= 1500.0 -> "高位地势"
            altitudeMeters >= 500.0 -> "中高起伏"
            altitudeMeters >= 100.0 -> "轻微起伏"
            else -> "地势平缓"
        }
    }

    fun terrainSummary(
        altitudeMeters: Double?,
        accuracyMeters: Float?,
    ): String {
        if (altitudeMeters == null) {
            return "当前位置海拔还没拿到，页面会在定位成功后自动更新。"
        }
        val accuracyPart = accuracyMeters?.let {
            "当前定位精度约 ${it.roundToInt()} 米，海拔值会随卫星状态略有波动。"
        } ?: "当前海拔由系统定位提供，静止几秒后通常会更稳定。"
        return when {
            altitudeMeters >= 1500.0 -> "你现在处在较高海拔区域，体感和路线爬升会更明显。$accuracyPart"
            altitudeMeters >= 500.0 -> "当前位置已经有一定高度起伏，路线变化会比较容易看出来。$accuracyPart"
            else -> "当前位置整体还算平缓，更适合看细小的抬升和下探。$accuracyPart"
        }
    }

    fun note(
        altitudeMeters: Double?,
        timestampMillis: Long?,
    ): String {
        if (altitudeMeters == null) {
            return "这个页面现在改成了海拔页，需要系统先给出一次有效定位。"
        }
        val time = timestampMillis?.let {
            SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(it))
        } ?: "--:--"
        return "海拔来自系统定位，最近一次更新于 $time。后面如果你要，我可以再给它补海拔历史曲线。"
    }

    fun sourceLabel(
        altitudeMeters: Double?,
        timestampMillis: Long?,
    ): String {
        if (altitudeMeters == null) return "等待当前位置"
        val time = timestampMillis?.let {
            SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(it))
        } ?: "--:--"
        return "定位海拔 · $time"
    }

    fun normalizedTrendPoints(samples: List<AltitudeSample>): List<Float> {
        if (samples.isEmpty()) {
            return listOf(0.62f, 0.6f, 0.58f, 0.56f, 0.57f, 0.55f, 0.54f)
        }
        val source = if (samples.size >= 7) {
            samples.takeLast(7)
        } else {
            buildList {
                repeat(7 - samples.size) { add(samples.first()) }
                addAll(samples)
            }
        }
        val min = source.minOf { it.altitudeMeters }
        val max = source.maxOf { it.altitudeMeters }
        if (abs(max - min) < 0.1) {
            return List(source.size) { 0.58f }
        }
        return source.map { sample ->
            val normalized = ((sample.altitudeMeters - min) / (max - min)).toFloat()
            0.72f - normalized * 0.32f
        }
    }

    private fun altitudeDelta(samples: List<AltitudeSample>): Double? {
        if (samples.size < 2) return null
        return samples.last().altitudeMeters - samples.first().altitudeMeters
    }
}
