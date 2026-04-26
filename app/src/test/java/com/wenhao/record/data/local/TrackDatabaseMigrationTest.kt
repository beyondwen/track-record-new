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

    @Test
    fun `track database declares migration 11 to 12 and exposes today display cache entities`() {
        val companionClass = Class.forName("com.wenhao.record.data.local.TrackDatabase\$Companion")

        assertTrue(
            companionClass.declaredFields.any { it.name == "MIGRATION_11_12" } ||
                companionClass.declaredMethods.any { it.name == "getMIGRATION_11_12" }
        )

        assertTrue(classExists("com.wenhao.record.data.local.stream.TodayDisplayPointEntity"))
        assertTrue(classExists("com.wenhao.record.data.local.stream.TodayTrackDisplayDao"))
    }

    @Test
    fun `track database declares migration 12 to 13 for tracking query indexes`() {
        val companionClass = Class.forName("com.wenhao.record.data.local.TrackDatabase\$Companion")

        assertTrue(
            companionClass.declaredFields.any { it.name == "MIGRATION_12_13" } ||
                companionClass.declaredMethods.any { it.name == "getMIGRATION_12_13" }
        )
    }

    @Test
    fun `track database declares migration 13 to 14 and exposes sync outbox entities`() {
        val companionClass = Class.forName("com.wenhao.record.data.local.TrackDatabase\$Companion")

        assertTrue(
            companionClass.declaredFields.any { it.name == "MIGRATION_13_14" } ||
                companionClass.declaredMethods.any { it.name == "getMIGRATION_13_14" }
        )

        assertTrue(classExists("com.wenhao.record.data.local.stream.SyncOutboxEntity"))
        assertTrue(classExists("com.wenhao.record.data.local.stream.SyncOutboxDao"))
    }

    @Test
    fun `track database declares migration 14 to 15 and exposes today session entities`() {
        val companionClass = Class.forName("com.wenhao.record.data.local.TrackDatabase\$Companion")

        assertTrue(
            companionClass.declaredFields.any { it.name == "MIGRATION_14_15" } ||
                companionClass.declaredMethods.any { it.name == "getMIGRATION_14_15" }
        )

        assertTrue(classExists("com.wenhao.record.data.local.stream.TodaySessionEntity"))
        assertTrue(classExists("com.wenhao.record.data.local.stream.TodaySessionPointEntity"))
        assertTrue(classExists("com.wenhao.record.data.local.stream.TodaySessionDao"))
    }

    private fun classExists(name: String): Boolean {
        return runCatching { Class.forName(name) }.isSuccess
    }
}
