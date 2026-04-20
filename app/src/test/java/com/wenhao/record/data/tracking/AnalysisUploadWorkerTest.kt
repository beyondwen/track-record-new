package com.wenhao.record.data.tracking

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.wenhao.record.data.local.stream.AnalysisCursorEntity
import com.wenhao.record.data.local.stream.AnalysisSegmentEntity
import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.RawLocationPointEntity
import com.wenhao.record.data.local.stream.StayClusterEntity
import com.wenhao.record.data.local.stream.UploadCursorEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class AnalysisUploadWorkerTest {

    @Test
    fun `worker exposes workmanager compatible constructor`() {
        assertNotNull(
            AnalysisUploadWorker::class.java.getDeclaredConstructor(
                Context::class.java,
                WorkerParameters::class.java,
            )
        )
    }

    @Test
    fun `doWork advances analysis cursor after successful batch`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = FakeContinuousTrackDao(
            segments = listOf(segmentEntity(segmentId = 31L)),
            stayClusters = listOf(stayEntity(stayId = 201L, segmentId = 31L)),
        )
        val storage = ContinuousPointStorage(dao)
        val cursorStorage = UploadCursorStorage(dao)
        val worker = buildWorker(
            context = context,
            pointStorage = storage,
            cursorStorage = cursorStorage,
            uploadService = AnalysisUploadService(
                requestExecutor = {
                    UploadHttpResponse(
                        statusCode = 200,
                        body = """{"ok":true,"insertedCount":1,"dedupedCount":0,"acceptedMaxSegmentId":31}"""
                    )
                }
            ),
            configLoader = {
                TrainingSampleUploadConfig("https://worker.example.com", "token-123")
            },
            deviceIdProvider = { "device-1" },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(31L, cursorStorage.load(UploadCursorType.ANALYSIS_SEGMENT).lastUploadedId)
    }

    @Test
    fun `doWork retries when upload fails with network message`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = FakeContinuousTrackDao(segments = listOf(segmentEntity(segmentId = 31L)))
        val worker = buildWorker(
            context = context,
            pointStorage = ContinuousPointStorage(dao),
            cursorStorage = UploadCursorStorage(dao),
            uploadService = AnalysisUploadService(
                requestExecutor = {
                    UploadHttpResponse(
                        statusCode = 500,
                        body = """{"ok":false,"message":"server down"}"""
                    )
                }
            ),
            configLoader = {
                TrainingSampleUploadConfig("https://worker.example.com", "token-123")
            },
            deviceIdProvider = { "device-1" },
        )

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }

    private fun buildWorker(
        context: Context,
        pointStorage: ContinuousPointStorage,
        cursorStorage: UploadCursorStorage,
        uploadService: AnalysisUploadService,
        configLoader: (Context) -> TrainingSampleUploadConfig,
        deviceIdProvider: (Context) -> String,
    ): AnalysisUploadWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return AnalysisUploadWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    pointStorage = pointStorage,
                    cursorStorage = cursorStorage,
                    uploadService = uploadService,
                    configLoader = configLoader,
                    deviceIdProvider = deviceIdProvider,
                )
            }
        }

        return TestListenableWorkerBuilder.from(context, AnalysisUploadWorker::class.java)
            .setWorkerFactory(factory)
            .build()
    }

    private fun segmentEntity(segmentId: Long): AnalysisSegmentEntity {
        return AnalysisSegmentEntity(
            segmentId = segmentId,
            startPointId = 11L,
            endPointId = 19L,
            startTimestamp = 100_000L,
            endTimestamp = 160_000L,
            segmentType = "STATIC",
            confidence = 0.95f,
            distanceMeters = 18f,
            durationMillis = 60_000L,
            avgSpeedMetersPerSecond = 0.2f,
            maxSpeedMetersPerSecond = 0.4f,
            analysisVersion = 1,
        )
    }

    private fun stayEntity(stayId: Long, segmentId: Long): StayClusterEntity {
        return StayClusterEntity(
            stayId = stayId,
            segmentId = segmentId,
            centerLat = 30.1,
            centerLng = 120.1,
            radiusMeters = 25f,
            arrivalTime = 100_000L,
            departureTime = 160_000L,
            confidence = 0.91f,
            analysisVersion = 1,
        )
    }

    private class FakeContinuousTrackDao(
        private val segments: List<AnalysisSegmentEntity> = emptyList(),
        private val stayClusters: List<StayClusterEntity> = emptyList(),
    ) : ContinuousTrackDao {
        private val uploadCursors = linkedMapOf<String, UploadCursorEntity>()

        override fun insertRawPoint(entity: RawLocationPointEntity): Long {
            return entity.pointId
        }

        override fun loadRawPoints(afterPointId: Long, limit: Int): List<RawLocationPointEntity> {
            return emptyList()
        }

        override fun loadAnalysisSegments(afterSegmentId: Long, limit: Int): List<AnalysisSegmentEntity> {
            return segments.filter { it.segmentId > afterSegmentId }.sortedBy { it.segmentId }.take(limit)
        }

        override fun loadStayClustersForSegments(segmentIds: List<Long>): List<StayClusterEntity> {
            return stayClusters.filter { segmentIds.contains(it.segmentId) }.sortedBy { it.segmentId }
        }

        override fun loadAnalysisCursor(): AnalysisCursorEntity? {
            return null
        }

        override fun upsertAnalysisCursor(entity: AnalysisCursorEntity) {
        }

        override fun insertAnalysisSegments(entities: List<AnalysisSegmentEntity>) {
        }

        override fun insertStayClusters(entities: List<StayClusterEntity>) {
        }

        override fun loadUploadCursor(cursorType: String): UploadCursorEntity? {
            return uploadCursors[cursorType]
        }

        override fun upsertUploadCursor(entity: UploadCursorEntity) {
            uploadCursors[entity.cursorType] = entity
        }

        override fun deleteRawPointsUpTo(upToPointId: Long) {}
    }
}
