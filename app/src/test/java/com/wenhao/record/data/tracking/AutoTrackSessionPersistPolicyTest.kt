package com.wenhao.record.data.tracking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoTrackSessionPersistPolicyTest {

    @Test
    fun `persists immediately for first session snapshot`() {
        val shouldPersist = AutoTrackSessionPersistPolicy.shouldPersistImmediately(
            persistedSession = null,
            newSession = session(pointCount = 1),
            lastPersistedAt = 0L,
            nowMillis = 1_000L
        )

        assertTrue(shouldPersist)
    }

    @Test
    fun `can delay small append within throttle window`() {
        val persisted = session(pointCount = 2)
        val updated = session(pointCount = 3)

        val shouldPersist = AutoTrackSessionPersistPolicy.shouldPersistImmediately(
            persistedSession = persisted,
            newSession = updated,
            lastPersistedAt = 10_000L,
            nowMillis = 11_000L
        )

        assertFalse(shouldPersist)
    }

    @Test
    fun `forces persist when enough points accumulate`() {
        val persisted = session(pointCount = 2)
        val updated = session(pointCount = 5)

        val shouldPersist = AutoTrackSessionPersistPolicy.shouldPersistImmediately(
            persistedSession = persisted,
            newSession = updated,
            lastPersistedAt = 10_000L,
            nowMillis = 11_000L
        )

        assertTrue(shouldPersist)
    }

    @Test
    fun `forces persist after interval timeout`() {
        val persisted = session(pointCount = 2)
        val updated = session(pointCount = 3)

        val shouldPersist = AutoTrackSessionPersistPolicy.shouldPersistImmediately(
            persistedSession = persisted,
            newSession = updated,
            lastPersistedAt = 10_000L,
            nowMillis = 13_500L
        )

        assertTrue(shouldPersist)
    }

    private fun session(pointCount: Int): AutoTrackSession {
        return AutoTrackSession(
            startTimestamp = 1_000L,
            lastMotionTimestamp = 2_000L,
            totalDistanceKm = pointCount.toDouble(),
            points = List(pointCount) { index ->
                TrackPoint(
                    latitude = 30.0 + index,
                    longitude = 120.0 + index,
                    timestampMillis = 1_000L + index
                )
            }
        )
    }
}
