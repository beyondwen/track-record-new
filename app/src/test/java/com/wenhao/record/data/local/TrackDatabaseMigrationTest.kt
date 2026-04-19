package com.wenhao.record.data.local

import kotlin.test.Test
import kotlin.test.assertTrue

class TrackDatabaseMigrationTest {

    @Test
    fun `track database declares migration 7 to 8 and continuous stream entities`() {
        val companionClass = Class.forName("com.wenhao.record.data.local.TrackDatabase\$Companion")

        assertTrue(
            companionClass.declaredFields.any { it.name == "MIGRATION_7_8" } ||
                companionClass.declaredMethods.any { it.name == "getMIGRATION_7_8" }
        )

        assertTrue(classExists("com.wenhao.record.data.local.stream.RawLocationPointEntity"))
        assertTrue(classExists("com.wenhao.record.data.local.stream.AnalysisSegmentEntity"))
        assertTrue(classExists("com.wenhao.record.data.local.stream.StayClusterEntity"))
    }

    private fun classExists(name: String): Boolean {
        return runCatching { Class.forName(name) }.isSuccess
    }
}
