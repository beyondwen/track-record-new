package com.wenhao.record.data.history

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class HistoryUploadWorkerTest {

    @Test
    fun `worker exposes workmanager compatible constructor`() {
        assertNotNull(
            HistoryUploadWorker::class.java.getDeclaredConstructor(
                Context::class.java,
                WorkerParameters::class.java,
            )
        )
    }

    @Test
    fun `doWork marks uploaded history ids after successful batch`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val markedIds = mutableListOf<List<Long>>()
        val prunedSnapshots = mutableListOf<Pair<Set<Long>, Long>>()
        val worker = buildWorker(
            context = context,
            historyLoader = {
                listOf(
                    historyItem(historyId = 7L),
                    historyItem(historyId = 8L),
                )
            },
            uploadedHistoryIdsLoader = { emptySet() },
            markUploaded = { _, ids -> markedIds += ids },
            pruneUploadedHistories = { _, uploadedIds, nowMillis ->
                prunedSnapshots += uploadedIds to nowMillis
                emptyList()
            },
            uploadService = HistoryUploadService(
                requestExecutor = {
                    com.wenhao.record.data.tracking.UploadHttpResponse(
                        statusCode = 200,
                        body = """{"ok":true,"insertedCount":2,"dedupedCount":0,"acceptedHistoryIds":[7,8]}"""
                    )
                }
            ),
            configLoader = {
                TrainingSampleUploadConfig("https://worker.example.com", "token-123")
            },
            deviceIdProvider = { "device-1" },
            nowProvider = { 987_654_321L },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf(listOf(7L, 8L)), markedIds)
        assertEquals(listOf(setOf(7L, 8L) to 987_654_321L), prunedSnapshots)
    }

    @Test
    fun `doWork returns failure when auth fails`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = buildWorker(
            context = context,
            historyLoader = { listOf(historyItem(historyId = 7L)) },
            uploadedHistoryIdsLoader = { emptySet() },
            markUploaded = { _, _ -> },
            uploadService = HistoryUploadService(
                requestExecutor = {
                    com.wenhao.record.data.tracking.UploadHttpResponse(
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

    private fun buildWorker(
        context: Context,
        historyLoader: suspend (Context) -> List<HistoryItem>,
        uploadedHistoryIdsLoader: (Context) -> Set<Long>,
        markUploaded: (Context, List<Long>) -> Unit,
        pruneUploadedHistories: suspend (Context, Set<Long>, Long) -> List<Long> = { _, _, _ ->
            emptyList()
        },
        uploadService: HistoryUploadService,
        configLoader: (Context) -> TrainingSampleUploadConfig,
        deviceIdProvider: (Context) -> String,
        nowProvider: () -> Long = { System.currentTimeMillis() },
    ): HistoryUploadWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return HistoryUploadWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    historyLoader = historyLoader,
                    uploadedHistoryIdsLoader = uploadedHistoryIdsLoader,
                    markUploaded = markUploaded,
                    pruneUploadedHistories = pruneUploadedHistories,
                    uploadService = uploadService,
                    configLoader = configLoader,
                    deviceIdProvider = deviceIdProvider,
                    nowProvider = nowProvider,
                )
            }
        }

        return TestListenableWorkerBuilder.from(context, HistoryUploadWorker::class.java)
            .setWorkerFactory(factory)
            .build()
    }

    private fun historyItem(historyId: Long): HistoryItem {
        return HistoryItem(
            id = historyId,
            timestamp = 123_000L + historyId,
            distanceKm = 1.2,
            durationSeconds = 456,
            averageSpeedKmh = 9.4,
            title = "记录 $historyId",
        )
    }
}
