package com.wenhao.record.data.local

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackDatabaseMigrationTest {

    @Test
    fun `track database declares migration 8 to 9 and no longer exposes auto track classes`() {
        val companionClass = Class.forName("com.wenhao.record.data.local.TrackDatabase\$Companion")

        assertTrue(
            companionClass.declaredFields.any { it.name == "MIGRATION_8_9" } ||
                companionClass.declaredMethods.any { it.name == "getMIGRATION_8_9" }
        )

        assertTrue(classExists("com.wenhao.record.data.local.stream.RawLocationPointEntity"))
        assertTrue(classExists("com.wenhao.record.data.local.stream.AnalysisSegmentEntity"))
        assertTrue(classExists("com.wenhao.record.data.local.stream.StayClusterEntity"))
        assertFalse(classExists("com.wenhao.record.data.tracking.AutoTrackStorage"))
        assertFalse(classExists("com.wenhao.record.data.tracking.AutoTrackSessionPersistPolicy"))
        assertFalse(classExists("com.wenhao.record.data.local.auto.AutoTrackSessionEntity"))
    }

    @Test
    fun `track database declares migration 9 to 10 and exposes upload cursor entity`() {
        val companionClass = Class.forName("com.wenhao.record.data.local.TrackDatabase\$Companion")

        assertTrue(
            companionClass.declaredFields.any { it.name == "MIGRATION_9_10" } ||
                companionClass.declaredMethods.any { it.name == "getMIGRATION_9_10" }
        )

        assertTrue(classExists("com.wenhao.record.data.local.stream.UploadCursorEntity"))
    }

    private fun classExists(name: String): Boolean {
        return runCatching { Class.forName(name) }.isSuccess
    }
}
