package com.wenhao.record.data.history

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.wenhao.record.data.tracking.TrackPoint
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
    fun `doWork uploads local histories directly to worker`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uploadedRows = mutableListOf<List<HistoryUploadRow>>()
        val worker = buildWorker(
            context = context,
            historyLoader = {
                listOf(
                    HistoryItem(
                        id = 11L,
                        timestamp = 1_713_456_010_000L,
                        distanceKm = 1.23,
                        durationSeconds = 456,
                        averageSpeedKmh = 9.7,
                        title = "本地完整轨迹",
                        points = listOf(
                            TrackPoint(30.0, 120.0, 1_713_456_010_000L),
                            TrackPoint(30.1, 120.1, 1_713_456_070_000L),
                        ),
                    )
                )
            },
            uploadService = HistoryUploadService(
                requestExecutor = { request ->
                    val payload = org.json.JSONObject(request.body.orEmpty())
                    val histories = payload.getJSONArray("histories")
                    uploadedRows += List(histories.length()) { index ->
                        val item = histories.getJSONObject(index)
                        HistoryUploadRow(
                            historyId = item.getLong("historyId"),
                            timestampMillis = item.getLong("timestampMillis"),
                            distanceKm = item.getDouble("distanceKm"),
                            durationSeconds = item.getInt("durationSeconds"),
                            averageSpeedKmh = item.getDouble("averageSpeedKmh"),
                            title = item.optString("title").takeIf { it.isNotBlank() },
                            startSource = item.optString("startSource").takeIf { it.isNotBlank() },
                            stopSource = item.optString("stopSource").takeIf { it.isNotBlank() },
                            manualStartAt = null,
                            manualStopAt = null,
                            points = emptyList(),
                        )
                    }
                    com.wenhao.record.data.tracking.UploadHttpResponse(
                        statusCode = 200,
                        body = """{"ok":true,"insertedCount":1,"dedupedCount":0,"acceptedHistoryIds":[11]}""",
                    )
                }
            ),
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(11L), uploadedRows.flatten().map { it.historyId })
    }

    private fun buildWorker(
        context: Context,
        historyLoader: suspend (Context) -> List<HistoryItem>,
        uploadService: HistoryUploadService,
        configLoader: (Context) -> TrainingSampleUploadConfig,
        deviceIdProvider: (Context) -> String,
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
                    uploadService = uploadService,
                    configLoader = configLoader,
                    deviceIdProvider = deviceIdProvider,
                )
            }
        }

        return TestListenableWorkerBuilder.from(context, HistoryUploadWorker::class.java)
            .setWorkerFactory(factory)
            .build()
    }
}
