package com.wenhao.record.stability

import kotlin.test.Test
import kotlin.test.assertTrue

class CrashLogStoreTest {

    @Test
    fun `build summary contains time type and message`() {
        val summary = CrashLogStore.buildSummary(
            throwable = IllegalStateException("boom"),
            timestampMillis = 1_700_000_000_000L
        )

        assertTrue(summary.contains("IllegalStateException"))
        assertTrue(summary.contains("boom"))
        assertTrue(summary.contains("上次异常退出"))
    }
}
