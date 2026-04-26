package com.wenhao.record.data.tracking

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.wenhao.record.data.local.stream.TodaySessionDao
import com.wenhao.record.data.local.stream.TodaySessionEntity
import com.wenhao.record.data.local.stream.TodaySessionPointEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class TodaySessionSyncWorkerTest {

    @Test
    fun `doWork uploads session meta before session points and marks them synced`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

        val callOrder = mutableListOf<String>()
        val worker = buildWorker(
            context = context,
            storage = storage,
            sessionUploader = { _, _, _, _ ->
                callOrder += "session"
                UploadHttpResponse(200, "{\"ok\":true,\"message\":\"ok\"}")
            },
            pointUploader = { _, _, _, _ ->
                callOrder += "points"
                UploadHttpResponse(200, "{\"ok\":true,\"message\":\"ok\"}")
            },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf("session", "points"), callOrder)
        assertEquals(TodaySessionSyncState.SYNCED.name, dao.sessions.single().syncState)
        assertEquals(TodaySessionSyncState.SYNCED.name, dao.points.single().syncState)
    }

    private fun buildWorker(
        context: Context,
        storage: TodaySessionStorage,
        sessionUploader: (TrainingSampleUploadConfig, String, String, TodaySessionMetaUploadRow) -> UploadHttpResponse,
        pointUploader: (TrainingSampleUploadConfig, String, String, List<TodaySessionPointEntity>) -> UploadHttpResponse,
    ): TodaySessionSyncWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return TodaySessionSyncWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    sessionStorage = storage,
                    sessionUploadService = TodaySessionUploadService(requestExecutor = sessionUploader),
                    pointUploadService = TodaySessionPointUploadService(requestExecutor = pointUploader),
                    configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
                    deviceIdProvider = { "device-1" },
                    nowProvider = { 1_714_300_020_000L },
                )
            }
        }

        return TestListenableWorkerBuilder.from(context, TodaySessionSyncWorker::class.java)
            .setWorkerFactory(factory)
            .build()
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

        override suspend fun loadPointsBySyncState(sessionId: String, syncState: String, limit: Int): List<TodaySessionPointEntity> {
            return points
                .filter { it.sessionId == sessionId && it.syncState == syncState }
                .sortedBy { it.timestampMillis }
                .take(limit)
        }

        override suspend fun updatePointSyncState(sessionId: String, pointIds: List<Long>, syncState: String) {
            points.replaceAll { point ->
                if (point.sessionId == sessionId && point.pointId in pointIds) point.copy(syncState = syncState) else point
            }
        }

        override suspend fun deletePointsForSession(sessionId: String) = Unit

        override suspend fun deleteSessionsByStatusBefore(status: String, minDayStartMillis: Long) = Unit
    }
}
