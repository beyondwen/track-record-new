package com.wenhao.record.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.wenhao.record.data.tracking.TrackPoint

internal object TrackAltitudePalette {
    private val lowColor = Color(0xFF1F6FEB)
    private val midColor = Color(0xFF21B37E)
    private val highColor = Color(0xFFF59E0B)
    private val peakColor = Color(0xFFDC2626)
    private val fallbackColor = Color(0xFF5B8DEF)

    fun colorFor(point: TrackPoint, altitudeRange: ClosedFloatingPointRange<Double>?): Color {
        val altitude = point.altitudeMeters ?: return fallbackColor
        return colorForAltitude(altitude, altitudeRange)
    }

    fun colorForAltitude(altitudeMeters: Double?, altitudeRange: ClosedFloatingPointRange<Double>?): Color {
        altitudeMeters ?: return fallbackColor
        altitudeRange ?: return fallbackColor
        val start = altitudeRange.start
        val end = altitudeRange.endInclusive
        val normalized = if (end - start < 0.1) {
            0.5f
        } else {
            ((altitudeMeters - start) / (end - start)).toFloat().coerceIn(0f, 1f)
        }
        return when {
            normalized < 0.33f -> lerp(
                start = lowColor,
                stop = midColor,
                fraction = normalized / 0.33f,
            )

            normalized < 0.66f -> lerp(
                start = midColor,
                stop = highColor,
                fraction = (normalized - 0.33f) / 0.33f,
            )

            else -> lerp(
                start = highColor,
                stop = peakColor,
                fraction = (normalized - 0.66f) / 0.34f,
            )
        }
    }

    fun altitudeRange(points: List<TrackPoint>): ClosedFloatingPointRange<Double>? {
        val altitudes = points.mapNotNull { it.altitudeMeters }
        if (altitudes.isEmpty()) return null
        return altitudes.minOrNull()!!..altitudes.maxOrNull()!!
    }

    fun legendColors(): List<Color> = listOf(lowColor, midColor, highColor, peakColor)
}
