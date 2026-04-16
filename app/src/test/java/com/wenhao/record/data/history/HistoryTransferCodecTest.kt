package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryTransferCodecTest {

    @Test
    fun `encodes and decodes wrapped transfer payload`() {
        val items = listOf(
            HistoryItem(
                id = 3L,
                timestamp = 1_725_000_000_000L,
                distanceKm = 8.2,
                durationSeconds = 1260,
                averageSpeedKmh = 23.4,
                title = "回家",
                points = listOf(
                    TrackPoint(30.1, 120.1, timestampMillis = 1_000L, accuracyMeters = 6f),
                    TrackPoint(30.2, 120.2, timestampMillis = 2_000L, accuracyMeters = 8f)
                )
            )
        )

        val encoded = HistoryTransferCodec.encode(items, exportedAtMillis = 1234L)
        val decoded = HistoryTransferCodec.decode(encoded)

        assertEquals(items, decoded)
    }

    @Test
    fun `decodes legacy raw snapshot arrays`() {
        val legacyRaw = HistorySnapshotCodec.encode(
            listOf(
                HistoryItem(
                    id = 7L,
                    timestamp = 2_000L,
                    distanceKm = 1.2,
                    durationSeconds = 300,
                    averageSpeedKmh = 14.4
                )
            )
        )

        val decoded = HistoryTransferCodec.decode(legacyRaw)

        assertEquals(1, decoded.size)
        assertEquals(7L, decoded.first().id)
    }

    @Test
    fun `merges imported items by id and keeps newest ordering`() {
        val existing = listOf(
            HistoryItem(id = 1L, timestamp = 1_000L, distanceKm = 1.0, durationSeconds = 60, averageSpeedKmh = 60.0),
            HistoryItem(id = 2L, timestamp = 2_000L, distanceKm = 2.0, durationSeconds = 120, averageSpeedKmh = 60.0)
        )
        val imported = listOf(
            HistoryItem(id = 2L, timestamp = 3_000L, distanceKm = 2.5, durationSeconds = 150, averageSpeedKmh = 60.0),
            HistoryItem(id = 4L, timestamp = 4_000L, distanceKm = 4.0, durationSeconds = 240, averageSpeedKmh = 60.0)
        )

        val merged = HistoryTransferCodec.merge(existing, imported)

        assertEquals(listOf(4L, 2L, 1L), merged.map { it.id })
        assertTrue(merged.any { it.id == 2L && it.distanceKm == 2.5 })
    }
}
