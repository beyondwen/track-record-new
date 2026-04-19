package com.wenhao.record.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTabTest {

    @Test
    fun `main tabs no longer include barometer`() {
        assertEquals(
            listOf("RECORD", "HISTORY", "ABOUT"),
            MainTab.entries.map { it.name },
        )
    }
}
