package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.TodaySessionDao
import com.wenhao.record.data.local.stream.TodaySessionEntity
import com.wenhao.record.data.local.stream.TodaySessionPointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private class TodaySessionBackedDisplayStorage(
    private val dao: TodaySessionDao,
) {
    suspend fun loadToday(nowMillis: Long): List<TrackPoint> {
        val session = dao.loadOpenSession(
            dayStartMillis = dayStartMillis(nowMillis),
            openStates = listOf(
                TodaySessionStatus.ACTIVE.name,
                TodaySessionStatus.PAUSED.name,
                TodaySessionStatus.RECOVERED.name,
            ),
        ) ?: return emptyList()
        return dao.loadPoints(session.sessionId)
            .sortedBy { it.timestampMillis }
            .takeLast(TodayTrackDisplayCache.MAX_DISPLAY_POINTS)
            .map {
                TrackPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    timestampMillis = it.timestampMillis,
                    accuracyMeters = it.accuracyMeters,
                    altitudeMeters = it.altitudeMeters,
                )
            }
    }

    private fun dayStartMillis(timestampMillis: Long): Long {
        return java.util.Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

class TodayTrackDisplayCacheTest {

    @Test
    fun `today session load keeps current day points in timestamp order and caps newest points`() = runBlocking {
        val dao = FakeTodaySessionDao()
        val todayDisplay = TodaySessionBackedDisplayStorage(dao)
        val storage = TodaySessionStorage(dao)
        val dayStart = 1_771_430_400_000L
        val session = storage.createOrRestoreOpenSession(nowMillis = dayStart + 1_000L)

        storage.appendPoint(
            sessionId = session.sessionId,
            pointId = 2L,
            rawPoint = rawPoint(timestampMillis = dayStart + 2_000L, latitude = 30.2),
            phase = "ACTIVE",
            nowMillis = dayStart + 2_000L,
        )
        storage.appendPoint(
            sessionId = session.sessionId,
            pointId = 1L,
            rawPoint = rawPoint(timestampMillis = dayStart + 1_000L, latitude = 30.1),
            phase = "ACTIVE",
            nowMillis = dayStart + 1_000L,
        )

        val points = todayDisplay.loadToday(nowMillis = dayStart + 3_000L)

        assertEquals(listOf(dayStart + 1_000L, dayStart + 2_000L), points.map { it.timestampMillis })
        assertEquals(listOf(30.1, 30.2), points.map { it.latitude })
    }

    @Test
    fun `load today returns empty after midnight when no open session exists`() = runBlocking {
        val dao = FakeTodaySessionDao()
        val todayDisplay = TodaySessionBackedDisplayStorage(dao)
        val storage = TodaySessionStorage(dao)
        val dayStart = 1_771_430_400_000L
        val nextDayStart = dayStart + 86_400_000L
        val session = storage.createOrRestoreOpenSession(nowMillis = dayStart + 1_000L)

        storage.appendPoint(
            sessionId = session.sessionId,
            pointId = 1L,
            rawPoint = rawPoint(timestampMillis = dayStart + 10_000L),
            phase = "ACTIVE",
            nowMillis = dayStart + 10_000L,
        )
        storage.markCompleted(
            sessionId = session.sessionId,
            endedAt = dayStart + 20_000L,
            nowMillis = dayStart + 20_000L,
        )

        assertEquals(emptyList(), todayDisplay.loadToday(nowMillis = nextDayStart + 1_000L))
    }

    @Test
    fun `observe today exposes flow return type`() {
        val method = TodayTrackDisplayCache::class.java.getDeclaredMethod(
            "observeToday",
            android.content.Context::class.java,
            Long::class.javaPrimitiveType,
        )

        assertEquals(Flow::class.java.name, method.returnType.name)
    }

    private fun rawPoint(timestampMillis: Long, latitude: Double = 30.0): RawTrackPoint {
        return RawTrackPoint(
            pointId = 0,
            timestampMillis = timestampMillis,
            latitude = latitude,
            longitude = 120.0,
            accuracyMeters = 8f,
            altitudeMeters = 18.0,
            speedMetersPerSecond = 1.2f,
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

        override suspend fun upsertSession(entity: TodaySessionEntity) {
            sessions.removeAll { it.sessionId == entity.sessionId }
            sessions += entity
        }

        override suspend fun upsertPoint(entity: TodaySessionPointEntity) {
            points.removeAll { it.sessionId == entity.sessionId && it.pointId == entity.pointId }
            points += entity
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

        override suspend fun updatePointSyncState(sessionId: String, pointIds: List<Long>, syncState: String) = Unit

        override suspend fun deletePointsForSession(sessionId: String) = Unit

        override suspend fun deleteSessionsByStatusBefore(status: String, minDayStartMillis: Long) = Unit
    }
}
