package com.wenhao.record.ui.barometer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarometerMetricsFormatterTest {

    @Test
    fun `trend label reports rising altitude`() {
        val samples = listOf(
            AltitudeSample(timestampMillis = 0L, altitudeMeters = 32.0),
            AltitudeSample(timestampMillis = 300_000L, altitudeMeters = 48.0),
        )

        assertEquals("明显上升", BarometerMetricsFormatter.trendLabel(samples))
    }

    @Test
    fun `trend label reports stable altitude for tiny delta`() {
        val samples = listOf(
            AltitudeSample(timestampMillis = 0L, altitudeMeters = 51.2),
            AltitudeSample(timestampMillis = 300_000L, altitudeMeters = 52.1),
        )

        assertEquals("基本平稳", BarometerMetricsFormatter.trendLabel(samples))
    }

    @Test
    fun `relative range reflects max min difference`() {
        val range = BarometerMetricsFormatter.formatRelativeRange(
            listOf(
                AltitudeSample(timestampMillis = 0L, altitudeMeters = 18.0),
                AltitudeSample(timestampMillis = 1000L, altitudeMeters = 26.0),
                AltitudeSample(timestampMillis = 2000L, altitudeMeters = 21.0),
            ),
        )

        assertEquals("8 m", range)
    }

    @Test
    fun `trend summary mentions observed window`() {
        val summary = BarometerMetricsFormatter.trendSummary(
            listOf(
                AltitudeSample(timestampMillis = 0L, altitudeMeters = 84.0),
                AltitudeSample(timestampMillis = 600_000L, altitudeMeters = 72.0),
            ),
        )

        assertTrue(summary.contains("10 分钟"))
        assertTrue(summary.contains("下降"))
    }
}
