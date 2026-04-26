package com.wenhao.record.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MapActivityHistoryRenderingConfigTest {

    @Test
    fun `uses detailed history polyline rendering budget`() {
        assertEquals(180, HISTORY_POLYLINE_MAX_POINTS_PER_SEGMENT)
        assertEquals(4, HISTORY_POLYLINE_ALTITUDE_BUCKETS)
    }
}
