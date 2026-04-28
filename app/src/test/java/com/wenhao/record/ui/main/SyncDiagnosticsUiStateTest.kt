package com.wenhao.record.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncDiagnosticsUiStateTest {

    @Test
    fun `build diagnostics rows formats queue and outbox counts`() {
        val rows = buildSyncDiagnosticsRows(
            SyncDiagnosticsUiState(
                rawPointCount = 12,
                todayDisplayPointCount = 8,
                outboxPendingCount = 2,
                outboxInProgressCount = 1,
                outboxFailedCount = 4,
                lastError = "timeout",
            )
        )

        assertEquals("原始采集队列", rows[0].title)
        assertEquals("12 个点", rows[0].value)
        assertEquals("待上传", rows[2].title)
        assertEquals("失败", rows.last().title)
        assertEquals("timeout", rows.last().value)
    }
}
