package com.wenhao.record.ui.barometer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BarometerController {
    private val recentSamples = ArrayDeque<AltitudeSample>()
    private var latestAltitudeMeters: Double? = null
    private var lastAltitudeTimestampMillis: Long? = null
    private var latestAccuracyMeters: Float? = null

    var uiState by mutableStateOf(
        BarometerUiState(
            isSensorAvailable = true,
            hasBarometerFeature = true,
            isReadingLive = false,
        ),
    )
        private set

    fun setSensorAvailability(isAvailable: Boolean) = Unit

    fun setBarometerFeatureAvailable(isAvailable: Boolean) = Unit

    fun updateSensorDebugLabel(label: String) = Unit

    fun updateSensorDiagnostics(label: String) = Unit

    fun updateSensorInventory(label: String) = Unit

    fun updateLocationAltitude(
        altitudeMeters: Double?,
        accuracyMeters: Float? = null,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        latestAccuracyMeters = accuracyMeters
        if (altitudeMeters != null) {
            latestAltitudeMeters = altitudeMeters
            lastAltitudeTimestampMillis = timestampMillis
            recentSamples.addLast(
                AltitudeSample(
                    timestampMillis = timestampMillis,
                    altitudeMeters = altitudeMeters,
                ),
            )
            trimSamples(timestampMillis)
        }
        rebuildState()
    }

    private fun trimSamples(nowMillis: Long) {
        while (recentSamples.size > 18) {
            recentSamples.removeFirst()
        }
        while (recentSamples.isNotEmpty() && nowMillis - recentSamples.first().timestampMillis > 30 * 60_000L) {
            recentSamples.removeFirst()
        }
    }

    private fun rebuildState() {
        val samples = recentSamples.toList()
        uiState = BarometerUiState(
            isSensorAvailable = true,
            hasBarometerFeature = true,
            isReadingLive = latestAltitudeMeters != null,
            sensorDebugLabel = BarometerMetricsFormatter.sourceLabel(
                altitudeMeters = latestAltitudeMeters,
                timestampMillis = lastAltitudeTimestampMillis,
            ),
            sensorDiagnostics = latestAccuracyMeters?.let {
                "定位海拔会随卫星状态波动，当前参考精度约 ${it.toInt()} 米。"
            }.orEmpty(),
            sensorInventory = "",
            pressureValue = BarometerMetricsFormatter.formatPrimaryAltitude(latestAltitudeMeters),
            pressureUnit = "m",
            trendLabel = BarometerMetricsFormatter.trendLabel(samples),
            trendSummary = BarometerMetricsFormatter.trendSummary(samples),
            altitudeValue = BarometerMetricsFormatter.formatRelativeRange(samples),
            seaLevelValue = BarometerMetricsFormatter.formatLocationStatus(
                altitudeMeters = latestAltitudeMeters,
                accuracyMeters = latestAccuracyMeters,
            ),
            comfortLabel = BarometerMetricsFormatter.terrainLabel(latestAltitudeMeters),
            comfortSummary = BarometerMetricsFormatter.terrainSummary(
                altitudeMeters = latestAltitudeMeters,
                accuracyMeters = latestAccuracyMeters,
            ),
            note = BarometerMetricsFormatter.note(
                altitudeMeters = latestAltitudeMeters,
                timestampMillis = lastAltitudeTimestampMillis,
            ),
            trendPoints = BarometerMetricsFormatter.normalizedTrendPoints(samples),
        )
    }
}
