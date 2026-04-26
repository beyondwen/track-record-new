package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.TodaySessionDao
import com.wenhao.record.data.local.stream.TodaySessionEntity
import com.wenhao.record.data.local.stream.TodaySessionPointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodaySessionStorageTest {

    @Test
    fun `create or restore open session keeps same session within a day`() = runBlocking {
        val dao = FakeTodaySessionDao()
        val storage = TodaySessionStorage(dao)
        val first = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_000_000L)
        val second = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_600_000L)

        assertEquals(first.sessionId, second.sessionId)
        assertEquals(TodaySessionStatus.ACTIVE.name, dao.sessions.single().status)
    }

    @Test
    fun `append point marks point pending sync and updates latest timestamp`() = runBlocking {
        val dao = FakeTodaySessionDao()
        val storage = TodaySessionStorage(dao)
        val session = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_000_000L)

        storage.appendPoint(
            sessionId = session.sessionId,
            pointId = 18L,
            rawPoint = rawPoint(timestampMillis = 1_714_300_010_000L),
            phase = "ACTIVE",
            nowMillis = 1_714_300_010_000L,
        )

        assertEquals(1, dao.points.size)
        assertEquals(TodaySessionSyncState.PENDING.name, dao.points.single().syncState)
        assertEquals(1_714_300_010_000L, dao.sessions.single().lastPointAt)
    }

    @Test
    fun `complete session keeps meta but clears active query`() = runBlocking {
        val dao = FakeTodaySessionDao()
        val storage = TodaySessionStorage(dao)
        val session = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_000_000L)

        storage.markCompleted(
            sessionId = session.sessionId,
            endedAt = 1_714_303_000_000L,
            nowMillis = 1_714_303_000_000L,
        )

        assertFalse(storage.hasOpenSession(dayStartMillis = session.dayStartMillis))
        assertEquals(TodaySessionStatus.COMPLETED.name, dao.sessions.single().status)
        assertEquals(TodaySessionSyncState.PENDING.name, dao.sessions.single().syncState)
    }

    @Test
    fun `delete completed session points only clears completed sessions`() = runBlocking {
        val dao = FakeTodaySessionDao()
        val storage = TodaySessionStorage(dao)
        val session = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_000_000L)
        storage.appendPoint(
            sessionId = session.sessionId,
            pointId = 18L,
            rawPoint = rawPoint(timestampMillis = 1_714_300_010_000L),
            phase = "ACTIVE",
            nowMillis = 1_714_300_010_000L,
        )

        storage.deleteCompletedSessionPoints(session.sessionId)
        assertEquals(1, dao.points.size)

        storage.markCompleted(
            sessionId = session.sessionId,
            endedAt = 1_714_303_000_000L,
            nowMillis = 1_714_303_000_000L,
        )
        storage.deleteCompletedSessionPoints(session.sessionId)

        assertEquals(0, dao.points.size)
    }

    private fun rawPoint(timestampMillis: Long): RawTrackPoint {
        return RawTrackPoint(
            pointId = 0,
            timestampMillis = timestampMillis,
            latitude = 30.0,
            longitude = 120.0,
            accuracyMeters = 8f,
            altitudeMeters = 12.0,
            speedMetersPerSecond = 1.1f,
            bearingDegrees = null,
            provider = "gps",
            sourceType = "LOCATION_MANAGER",
            isMock = false,
            wifiFingerprintDigest = null,
            activityType = "WALKING",
            activityConfidence = 0.9f,
            samplingTier = SamplingTier.ACTIVE,
        )
    }

    private class FakeTodaySessionDao : TodaySessionDao {
        val sessions = mutableListOf<TodaySessionEntity>()
        val points = mutableListOf<TodaySessionPointEntity>()
        private val pointFlow = MutableStateFlow<List<TodaySessionPointEntity>>(emptyList())

        override suspend fun upsertSession(entity: TodaySessionEntity) {
            sessions.removeAll { it.sessionId == entity.sessionId }
            sessions += entity
        }

        override suspend fun upsertPoint(entity: TodaySessionPointEntity) {
            points.removeAll { it.sessionId == entity.sessionId && it.pointId == entity.pointId }
            points += entity
            pointFlow.value = points.toList()
        }

        override suspend fun loadOpenSession(dayStartMillis: Long, openStates: List<String>): TodaySessionEntity? {
            return sessions
                .filter { it.dayStartMillis == dayStartMillis && it.status in openStates }
                .maxByOrNull { it.startedAt }
        }

        override suspend fun loadSession(sessionId: String): TodaySessionEntity? {
            return sessions.firstOrNull { it.sessionId == sessionId }
        }

        override suspend fun loadPoints(sessionId: String): List<TodaySessionPointEntity> {
            return points.filter { it.sessionId == sessionId }.sortedBy { it.timestampMillis }
        }

        override suspend fun loadPointsBySyncState(
            sessionId: String,
            syncState: String,
            limit: Int,
        ): List<TodaySessionPointEntity> {
            return points
                .filter { it.sessionId == sessionId && it.syncState == syncState }
                .sortedBy { it.timestampMillis }
                .take(limit)
        }

        override suspend fun updatePointSyncState(sessionId: String, pointIds: List<Long>, syncState: String) {
            val pointIdSet = pointIds.toSet()
            replacePoints { point ->
                if (point.sessionId == sessionId && point.pointId in pointIdSet) {
                    point.copy(syncState = syncState)
                } else {
                    point
                }
            }
        }

        override suspend fun deletePointsForSession(sessionId: String) {
            points.removeAll { it.sessionId == sessionId }
            pointFlow.value = points.toList()
        }

        override suspend fun deleteSessionsByStatusBefore(status: String, minDayStartMillis: Long) {
            sessions.removeAll { it.status == status && it.dayStartMillis < minDayStartMillis }
        }

        private fun replacePoints(transform: (TodaySessionPointEntity) -> TodaySessionPointEntity) {
            val updated = points.map(transform)
            points.clear()
            points += updated
            pointFlow.value = points.toList()
        }
    }
}
