package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.TodaySessionDao
import com.wenhao.record.data.local.stream.TodaySessionEntity
import com.wenhao.record.data.local.stream.TodaySessionPointEntity
import java.util.Calendar

enum class TodaySessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    RECOVERED,
}

enum class TodaySessionSyncState {
    PENDING,
    SYNCED,
    FAILED,
}

class TodaySessionStorage(
    private val dao: TodaySessionDao,
) {
    private val openStates = listOf(
        TodaySessionStatus.ACTIVE.name,
        TodaySessionStatus.PAUSED.name,
        TodaySessionStatus.RECOVERED.name,
    )

    suspend fun createOrRestoreOpenSession(nowMillis: Long = System.currentTimeMillis()): TodaySessionEntity {
        val dayStartMillis = dayStartMillis(nowMillis)
        val existing = dao.loadOpenSession(dayStartMillis = dayStartMillis, openStates = openStates)
        if (existing != null) {
            val restored = existing.copy(
                status = TodaySessionStatus.ACTIVE.name,
                updatedAt = nowMillis,
            )
            dao.upsertSession(restored)
            return restored
        }

        val created = TodaySessionEntity(
            sessionId = buildSessionId(dayStartMillis),
            dayStartMillis = dayStartMillis,
            status = TodaySessionStatus.ACTIVE.name,
            startedAt = nowMillis,
            lastPointAt = null,
            endedAt = null,
            lastSyncedAt = null,
            syncState = TodaySessionSyncState.PENDING.name,
            phase = null,
            anchorPointRef = null,
            recoveredFromRemote = false,
            updatedAt = nowMillis,
        )
        dao.upsertSession(created)
        return created
    }

    suspend fun loadOpenSession(nowMillis: Long = System.currentTimeMillis()): TodaySessionEntity? {
        return dao.loadOpenSession(
            dayStartMillis = dayStartMillis(nowMillis),
            openStates = openStates,
        )
    }

    suspend fun loadSession(sessionId: String): TodaySessionEntity? {
        return dao.loadSession(sessionId)
    }

    suspend fun loadRawPoints(sessionId: String): List<RawTrackPoint> {
        return dao.loadPoints(sessionId)
            .sortedBy { it.timestampMillis }
            .map { point ->
                RawTrackPoint(
                    pointId = point.pointId,
                    timestampMillis = point.timestampMillis,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    accuracyMeters = point.accuracyMeters,
                    altitudeMeters = point.altitudeMeters,
                    speedMetersPerSecond = point.speedMetersPerSecond,
                    bearingDegrees = point.bearingDegrees,
                    provider = point.provider,
                    sourceType = point.sourceType,
                    isMock = point.isMock,
                    wifiFingerprintDigest = point.wifiFingerprintDigest,
                    activityType = point.activityType,
                    activityConfidence = point.activityConfidence,
                    samplingTier = runCatching {
                        SamplingTier.valueOf(point.samplingTier)
                    }.getOrDefault(SamplingTier.IDLE),
                )
            }
    }

    suspend fun appendPoint(
        sessionId: String,
        pointId: Long,
        rawPoint: RawTrackPoint,
        phase: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        dao.upsertPoint(
            TodaySessionPointEntity(
                sessionId = sessionId,
                pointId = pointId,
                timestampMillis = rawPoint.timestampMillis,
                latitude = rawPoint.latitude,
                longitude = rawPoint.longitude,
                accuracyMeters = rawPoint.accuracyMeters,
                altitudeMeters = rawPoint.altitudeMeters,
                speedMetersPerSecond = rawPoint.speedMetersPerSecond,
                bearingDegrees = rawPoint.bearingDegrees,
                provider = rawPoint.provider,
                sourceType = rawPoint.sourceType,
                isMock = rawPoint.isMock,
                wifiFingerprintDigest = rawPoint.wifiFingerprintDigest,
                activityType = rawPoint.activityType,
                activityConfidence = rawPoint.activityConfidence,
                samplingTier = rawPoint.samplingTier.name,
                phase = phase,
                syncState = TodaySessionSyncState.PENDING.name,
                updatedAt = nowMillis,
            )
        )

        val session = dao.loadOpenSession(
            dayStartMillis = dayStartMillis(rawPoint.timestampMillis),
            openStates = openStates,
        ) ?: return
        if (session.sessionId != sessionId) return

        dao.upsertSession(
            session.copy(
                lastPointAt = rawPoint.timestampMillis,
                syncState = TodaySessionSyncState.PENDING.name,
                phase = phase,
                updatedAt = nowMillis,
            )
        )
    }

    suspend fun markPaused(
        sessionId: String,
        phase: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val session = loadOpenSession(nowMillis) ?: return
        if (session.sessionId != sessionId) return

        dao.upsertSession(
            session.copy(
                status = TodaySessionStatus.PAUSED.name,
                phase = phase,
                updatedAt = nowMillis,
            )
        )
    }

    suspend fun markCompleted(
        sessionId: String,
        endedAt: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val session = dao.loadOpenSession(
            dayStartMillis = dayStartMillis(endedAt),
            openStates = openStates,
        ) ?: return
        if (session.sessionId != sessionId) return

        dao.upsertSession(
            session.copy(
                status = TodaySessionStatus.COMPLETED.name,
                endedAt = endedAt,
                syncState = TodaySessionSyncState.PENDING.name,
                updatedAt = nowMillis,
            )
        )
    }

    suspend fun deleteCompletedSessionPoints(sessionId: String) {
        val session = dao.loadSession(sessionId) ?: return
        if (session.status != TodaySessionStatus.COMPLETED.name) return
        dao.deletePointsForSession(sessionId)
    }

    suspend fun latestPoint(nowMillis: Long = System.currentTimeMillis()): TrackPoint? {
        val session = loadOpenSession(nowMillis) ?: return null
        return dao.loadPoints(session.sessionId)
            .sortedBy { it.timestampMillis }
            .lastOrNull()
            ?.toTrackPoint()
    }

    suspend fun loadPendingPoints(sessionId: String, limit: Int): List<TodaySessionPointEntity> {
        return dao.loadPointsBySyncState(
            sessionId = sessionId,
            syncState = TodaySessionSyncState.PENDING.name,
            limit = limit,
        )
    }

    suspend fun markPointsSynced(sessionId: String, pointIds: List<Long>, nowMillis: Long) {
        if (pointIds.isEmpty()) return
        dao.updatePointSyncState(
            sessionId = sessionId,
            pointIds = pointIds,
            syncState = TodaySessionSyncState.SYNCED.name,
        )
        val session = loadSessionForSync(sessionId) ?: return
        dao.upsertSession(
            session.copy(
                updatedAt = nowMillis,
            )
        )
    }

    suspend fun markSessionSynced(sessionId: String, nowMillis: Long) {
        val session = loadSessionForSync(sessionId) ?: return
        dao.upsertSession(
            session.copy(
                syncState = TodaySessionSyncState.SYNCED.name,
                lastSyncedAt = nowMillis,
                updatedAt = nowMillis,
            )
        )
    }

    suspend fun hasOpenSession(dayStartMillis: Long): Boolean {
        return dao.loadOpenSession(dayStartMillis = dayStartMillis, openStates = openStates) != null
    }

    suspend fun replaceWithRemoteSession(
        snapshot: RemoteTodaySessionSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val remoteSession = snapshot.session ?: return
        if (remoteSession.status == TodaySessionStatus.COMPLETED.name) return

        dao.upsertSession(
            TodaySessionEntity(
                sessionId = remoteSession.sessionId,
                dayStartMillis = remoteSession.dayStartMillis,
                status = TodaySessionStatus.RECOVERED.name,
                startedAt = remoteSession.startedAt,
                lastPointAt = remoteSession.lastPointAt ?: snapshot.points.maxOfOrNull { it.timestampMillis },
                endedAt = remoteSession.endedAt,
                lastSyncedAt = remoteSession.updatedAt,
                syncState = TodaySessionSyncState.SYNCED.name,
                phase = remoteSession.phase,
                anchorPointRef = null,
                recoveredFromRemote = true,
                updatedAt = maxOf(remoteSession.updatedAt, nowMillis),
            )
        )
        dao.deletePointsForSession(remoteSession.sessionId)
        snapshot.points
            .filter { point -> point.sessionId == remoteSession.sessionId }
            .sortedBy { point -> point.timestampMillis }
            .forEach { point ->
                dao.upsertPoint(
                    TodaySessionPointEntity(
                        sessionId = point.sessionId,
                        pointId = point.pointId,
                        timestampMillis = point.timestampMillis,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        accuracyMeters = point.accuracyMeters?.toFloat(),
                        altitudeMeters = point.altitudeMeters,
                        speedMetersPerSecond = point.speedMetersPerSecond?.toFloat(),
                        bearingDegrees = null,
                        provider = point.provider,
                        sourceType = "REMOTE_MIRROR",
                        isMock = false,
                        wifiFingerprintDigest = null,
                        activityType = null,
                        activityConfidence = null,
                        samplingTier = point.samplingTier,
                        phase = remoteSession.phase,
                        syncState = TodaySessionSyncState.SYNCED.name,
                        updatedAt = maxOf(point.updatedAt, nowMillis),
                    )
                )
            }
    }

    private suspend fun loadSessionForSync(sessionId: String): TodaySessionEntity? {
        dao.loadSession(sessionId)?.let { return it }
        val point = dao.loadPoints(sessionId).firstOrNull()
        if (point != null) {
            val openSession = dao.loadOpenSession(dayStartMillis(point.timestampMillis), openStates)
                ?.takeIf { it.sessionId == sessionId }
            if (openSession != null) return openSession
        }
        return loadOpenSession(System.currentTimeMillis())?.takeIf { it.sessionId == sessionId }
    }

    private fun TodaySessionPointEntity.toTrackPoint(): TrackPoint {
        return TrackPoint(
            latitude = latitude,
            longitude = longitude,
            timestampMillis = timestampMillis,
            accuracyMeters = accuracyMeters,
            altitudeMeters = altitudeMeters,
        )
    }

    private fun buildSessionId(dayStartMillis: Long): String {
        return "today-$dayStartMillis"
    }

    private fun dayStartMillis(timestampMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
