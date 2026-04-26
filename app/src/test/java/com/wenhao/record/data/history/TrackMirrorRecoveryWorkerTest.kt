package com.wenhao.record.data.history

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
import com.wenhao.record.data.tracking.RemoteTodaySessionPoint
import com.wenhao.record.data.tracking.RemoteTodaySessionReadResult
import com.wenhao.record.data.tracking.RemoteTodaySessionRecord
import com.wenhao.record.data.tracking.RemoteTodaySessionSnapshot
import com.wenhao.record.data.tracking.TodaySessionStatus
import com.wenhao.record.data.tracking.TodaySessionStorage
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class TrackMirrorRecoveryWorkerTest {

    @Test
    fun `doWork restores remote histories before open today session`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = FakeTodaySessionDao()
        val callOrder = mutableListOf<String>()
        var restoredHistories: List<HistoryItem> = emptyList()
        val worker = buildWorker(
            context = context,
            todaySessionStorage = TodaySessionStorage(dao),
            historyLoader = { _, _ ->
                callOrder += "history"
                RemoteHistoryReadResult.Success(
                    listOf(
                        HistoryItem(
                            id = 7L,
                            timestamp = 1_714_300_000_000L,
                            distanceKm = 1.2,
                            durationSeconds = 300,
                            averageSpeedKmh = 14.4,
                            title = "remote",
                            points = listOf(TrackPoint(30.0, 120.0, 1_714_300_000_000L)),
                        )
                    )
                )
            },
            todaySessionLoader = { _, _ ->
                callOrder += "today"
                RemoteTodaySessionReadResult.Success(remoteTodaySessionSnapshot())
            },
            historyRestorer = { _, items ->
                restoredHistories = items
            },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf("history", "today"), callOrder)
        assertEquals(listOf(7L), restoredHistories.map { it.id })
        assertEquals(TodaySessionStatus.RECOVERED.name, dao.sessions.single().status)
        assertEquals(1, dao.points.size)
    }

    @Test
    fun `doWork skips recovery when mirror config is missing`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var remoteCalled = false
        val worker = buildWorker(
            context = context,
            todaySessionStorage = TodaySessionStorage(FakeTodaySessionDao()),
            config = TrainingSampleUploadConfig("", ""),
            historyLoader = { _, _ ->
                remoteCalled = true
                RemoteHistoryReadResult.Success(emptyList())
            },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(false, remoteCalled)
    }

    private fun buildWorker(
        context: Context,
        todaySessionStorage: TodaySessionStorage,
        config: TrainingSampleUploadConfig = TrainingSampleUploadConfig("https://worker.example.com", "token"),
        historyLoader: (TrainingSampleUploadConfig, String) -> RemoteHistoryReadResult = { _, _ ->
            RemoteHistoryReadResult.Success(emptyList())
        },
        todaySessionLoader: (TrainingSampleUploadConfig, String) -> RemoteTodaySessionReadResult = { _, _ ->
            RemoteTodaySessionReadResult.Success(RemoteTodaySessionSnapshot(session = null, points = emptyList()))
        },
        historyRestorer: suspend (Context, List<HistoryItem>) -> Unit = { _, _ -> },
    ): TrackMirrorRecoveryWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return TrackMirrorRecoveryWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    historyLoader = historyLoader,
                    todaySessionLoader = todaySessionLoader,
                    historyRestorer = historyRestorer,
                    todaySessionStorage = todaySessionStorage,
                    configLoader = { config },
                    deviceIdProvider = { "device-1" },
                    nowProvider = { 1_714_300_030_000L },
                )
            }
        }

        return TestListenableWorkerBuilder.from(context, TrackMirrorRecoveryWorker::class.java)
            .setWorkerFactory(factory)
            .build()
    }

    private fun remoteTodaySessionSnapshot(): RemoteTodaySessionSnapshot {
        return RemoteTodaySessionSnapshot(
            session = RemoteTodaySessionRecord(
                sessionId = "today-1",
                dayStartMillis = 1_714_262_400_000L,
                status = TodaySessionStatus.ACTIVE.name,
                startedAt = 1_714_300_000_000L,
                lastPointAt = 1_714_300_010_000L,
                endedAt = null,
                phase = "ACTIVE",
                updatedAt = 1_714_300_020_000L,
            ),
            points = listOf(
                RemoteTodaySessionPoint(
                    sessionId = "today-1",
                    pointId = 9L,
                    dayStartMillis = 1_714_262_400_000L,
                    timestampMillis = 1_714_300_010_000L,
                    latitude = 30.0,
                    longitude = 120.0,
                    accuracyMeters = 8.0,
                    altitudeMeters = 12.0,
                    speedMetersPerSecond = 1.1,
                    provider = "gps",
                    samplingTier = "ACTIVE",
                    updatedAt = 1_714_300_020_000L,
                )
            ),
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

        override suspend fun updatePointSyncState(
            sessionId: String,
            pointIds: List<Long>,
            syncState: String,
        ) {
            points.replaceAll { point ->
                if (point.sessionId == sessionId && point.pointId in pointIds) {
                    point.copy(syncState = syncState)
                } else {
                    point
                }
            }
        }

        override suspend fun deletePointsForSession(sessionId: String) {
            points.removeAll { it.sessionId == sessionId }
        }

        override suspend fun deleteSessionsByStatusBefore(status: String, minDayStartMillis: Long) {
            sessions.removeAll { it.status == status && it.dayStartMillis < minDayStartMillis }
        }
    }
}
