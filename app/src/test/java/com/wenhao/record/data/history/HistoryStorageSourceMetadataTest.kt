package com.wenhao.record.data.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryStorageSourceMetadataTest {

    @Test
    fun `history item keeps manual boundary metadata`() {
        val item = HistoryItem(
            id = 1L,
            timestamp = 1_700_000_000_000L,
            distanceKm = 1.2,
            durationSeconds = 180,
            averageSpeedKmh = 24.0,
            points = emptyList(),
            title = "测试记录",
            startSource = TrackRecordSource.MANUAL,
            stopSource = TrackRecordSource.MANUAL,
            manualStartAt = 1_700_000_000_000L,
            manualStopAt = 1_700_000_180_000L,
        )

        assertEquals(TrackRecordSource.MANUAL, item.startSource)
        assertEquals(TrackRecordSource.MANUAL, item.stopSource)
        assertEquals(1_700_000_000_000L, item.manualStartAt)
        assertEquals(1_700_000_180_000L, item.manualStopAt)
    }

    @Test
    fun `history item defaults unknown sources for legacy records`() {
        val item = HistoryItem(
            id = 2L,
            timestamp = 1_700_000_000_000L,
            distanceKm = 0.8,
            durationSeconds = 90,
            averageSpeedKmh = 12.0,
            points = emptyList(),
            title = null,
        )

        assertEquals(TrackRecordSource.UNKNOWN, item.startSource)
        assertEquals(TrackRecordSource.UNKNOWN, item.stopSource)
        assertNull(item.manualStartAt)
        assertNull(item.manualStopAt)
    }
}
