package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryDayItemTest {

    @Test
    fun `day item counts sanitized track segments instead of record count`() {
        val item = buildHistoryDayItem(
            dayStartMillis = 1_742_976_000_000L,
            latestTimestamp = 1_742_980_200_000L,
            sessionCount = 1,
            totalDistanceKm = 12.34,
            totalDurationSeconds = 754,
            averageSpeedKmh = 5.8,
            sourceIds = listOf(42L),
            segments = listOf(
                listOf(trackPoint(1_000L), trackPoint(2_000L)),
                listOf(trackPoint(3_000L), trackPoint(4_000L)),
                listOf(trackPoint(5_000L), trackPoint(6_000L)),
            ),
        )

        assertEquals(3, item.sessionCount)
        assertEquals("3 段", item.toSummaryItem().sessionCountLabel)
    }

    private fun trackPoint(timestampMillis: Long): TrackPoint {
        return TrackPoint(
            latitude = 30.0,
            longitude = 120.0,
            timestampMillis = timestampMillis,
        )
    }
}
