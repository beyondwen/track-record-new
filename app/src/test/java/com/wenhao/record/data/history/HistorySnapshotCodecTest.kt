package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistorySnapshotCodecTest {

    @Test
    fun `round trips history items`() {
        val items = listOf(
            HistoryItem(
                id = 7L,
                timestamp = 1_717_171_717_000L,
                distanceKm = 12.34,
                durationSeconds = 890,
                averageSpeedKmh = 49.9,
                title = "通勤",
                points = listOf(
                    TrackPoint(30.1, 120.1, timestampMillis = 1_000L, accuracyMeters = 8f),
                    TrackPoint(30.2, 120.2, timestampMillis = 2_000L, accuracyMeters = 9f)
                )
            )
        )

        val encoded = HistorySnapshotCodec.encode(items)
        val decoded = HistorySnapshotCodec.decode(encoded)

        assertEquals(items, decoded)
    }

    @Test
    fun `returns empty list for invalid json`() {
        val decoded = HistorySnapshotCodec.decode("not-json")

        assertTrue(decoded.isEmpty())
    }
}
