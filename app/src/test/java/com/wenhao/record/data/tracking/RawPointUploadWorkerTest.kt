package com.wenhao.record.data.tracking

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.RawLocationPointEntity
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
class RawPointUploadWorkerTest {

    @Test
    fun `worker exposes workmanager compatible constructor`() {
        assertNotNull(
            RawPointUploadWorker::class.java.getDeclaredConstructor(
                Context::class.java,
                WorkerParameters::class.java,
            )
        )
    }

    @Test
    fun `doWork advances raw point cursor after successful batch`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = FakeContinuousTrackDao(
            rawPoints = listOf(
                rawEntity(pointId = 11L),
                rawEntity(pointId = 12L),
            )
        )
        val storage = ContinuousPointStorage(dao)
        val cursorStorage = UploadCursorStorage(dao)
        val worker = buildWorker(
            context = context,
            pointStorage = storage,
            cursorStorage = cursorStorage,
            uploadService = RawPointUploadService(
                requestExecutor = {
                    UploadHttpResponse(
                        statusCode = 200,
                        body = """{"ok":true,"insertedCount":2,"dedupedCount":0,"acceptedMaxPointId":12}"""
                    )
                }
            ),
            configLoader = {
                TrainingSampleUploadConfig("https://worker.example.com", "token-123")
            },
            deviceIdProvider = { "device-1" },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(12L, cursorStorage.load(UploadCursorType.RAW_POINT).lastUploadedId)
    }

    @Test
    fun `doWork returns failure when auth fails`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = FakeContinuousTrackDao(rawPoints = listOf(rawEntity(pointId = 11L)))
        val worker = buildWorker(
            context = context,
            pointStorage = ContinuousPointStorage(dao),
            cursorStorage = UploadCursorStorage(dao),
            uploadService = RawPointUploadService(
                requestExecutor = {
                    UploadHttpResponse(
                        statusCode = 401,
                        body = """{"ok":false,"message":"expired"}"""
                    )
                }
            ),
            configLoader = {
                TrainingSampleUploadConfig("https://worker.example.com", "token-123")
            },
            deviceIdProvider = { "device-1" },
        )

        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    @Test
    fun `doWork drains backlog across more than three batches`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = FakeContinuousTrackDao(
            rawPoints = (1L..650L).map(::rawEntity)
        )
        val cursorStorage = UploadCursorStorage(dao)
        var uploadRequestCount = 0
        val worker = buildWorker(
            context = context,
            pointStorage = ContinuousPointStorage(dao),
            cursorStorage = cursorStorage,
            uploadService = RawPointUploadService(
                requestExecutor = { request ->
                    uploadRequestCount += 1
                    val payload = org.json.JSONObject(request.body.orEmpty())
                    val points = payload.getJSONArray("points")
                    val acceptedMaxPointId = points.getJSONObject(points.length() - 1).getLong("pointId")
                    UploadHttpResponse(
                        statusCode = 200,
                        body = """{"ok":true,"insertedCount":${points.length()},"dedupedCount":0,"acceptedMaxPointId":$acceptedMaxPointId}"""
                    )
                }
            ),
            configLoader = {
                TrainingSampleUploadConfig("https://worker.example.com", "token-123")
            },
            deviceIdProvider = { "device-1" },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(650L, cursorStorage.load(UploadCursorType.RAW_POINT).lastUploadedId)
        assertEquals(4, uploadRequestCount)
    }

    private fun buildWorker(
        context: Context,
        pointStorage: ContinuousPointStorage,
        cursorStorage: UploadCursorStorage,
        uploadService: RawPointUploadService,
        configLoader: (Context) -> TrainingSampleUploadConfig,
        deviceIdProvider: (Context) -> String,
    ): RawPointUploadWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return RawPointUploadWorker(
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

        return TestListenableWorkerBuilder.from(context, RawPointUploadWorker::class.java)
            .setWorkerFactory(factory)
            .build()
    }

    private fun rawEntity(pointId: Long): RawLocationPointEntity {
        return RawLocationPointEntity(
            pointId = pointId,
            timestampMillis = 123_000L + pointId,
            latitude = 30.1,
            longitude = 120.1,
            accuracyMeters = 6.0f,
            altitudeMeters = 12.0,
            speedMetersPerSecond = 1.5f,
            bearingDegrees = 90f,
            provider = "gps",
            sourceType = "LOCATION_MANAGER",
            isMock = false,
            wifiFingerprintDigest = "wifi",
            activityType = "WALKING",
            activityConfidence = 0.9f,
            samplingTier = "IDLE",
        )
    }

    private class FakeContinuousTrackDao(
        rawPoints: List<RawLocationPointEntity> = emptyList(),
    ) : ContinuousTrackDao {
        private val items = rawPoints.toMutableList()
        private val uploadCursors = linkedMapOf<String, UploadCursorEntity>()

        override suspend fun insertRawPoint(entity: RawLocationPointEntity): Long {
            items += entity
            return entity.pointId
        }

        override suspend fun loadRawPoints(afterPointId: Long, limit: Int): List<RawLocationPointEntity> {
            return items.filter { it.pointId > afterPointId }.sortedBy { it.pointId }.take(limit)
        }

        override suspend fun loadRawPointsBetween(startMillis: Long, endMillis: Long, limit: Int): List<RawLocationPointEntity> {
            return items
                .filter { it.timestampMillis in startMillis..endMillis }
                .sortedBy { it.timestampMillis }
                .take(limit)
        }

        override suspend fun countRawPoints(): Int {
            return items.size
        }

        override suspend fun loadUploadCursor(cursorType: String): UploadCursorEntity? {
            return uploadCursors[cursorType]
        }

        override suspend fun upsertUploadCursor(entity: UploadCursorEntity) {
            uploadCursors[entity.cursorType] = entity
        }

        override suspend fun deleteRawPointsUpTo(upToPointId: Long) {}
    }
}
