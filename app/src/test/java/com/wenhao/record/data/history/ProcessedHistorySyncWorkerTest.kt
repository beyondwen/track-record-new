package com.wenhao.record.data.history

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.wenhao.record.data.tracking.ProcessedHistorySyncStateStorage
import com.wenhao.record.data.tracking.RawTrackPoint
import com.wenhao.record.data.tracking.RemoteRawPointDaySummary
import com.wenhao.record.data.tracking.RemoteRawPointDaySummaryReadResult
import com.wenhao.record.data.tracking.RemoteRawPointReadResult
import com.wenhao.record.data.tracking.SamplingTier
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.tracking.analysis.AnalysisContext
import com.wenhao.record.tracking.analysis.AnalyzedPoint
import com.wenhao.record.tracking.analysis.SegmentCandidate
import com.wenhao.record.tracking.analysis.SegmentKind
import com.wenhao.record.tracking.analysis.StayClusterCandidate
import com.wenhao.record.tracking.analysis.TrackAnalysisResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class ProcessedHistorySyncWorkerTest {

    @Test
    fun `worker exposes workmanager compatible constructor`() {
        assertNotNull(
            ProcessedHistorySyncWorker::class.java.getDeclaredConstructor(
                Context::class.java,
                WorkerParameters::class.java,
            )
        )
    }

    @Test
    fun `doWork downloads day points, projects histories and uploads processed histories`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uploadedRows = mutableListOf<List<HistoryUploadRow>>()
        val stateStorage = ProcessedHistorySyncStateStorage(context)
        val worker = buildWorker(
            context = context,
            daySummaryLoader = { _, _, offsetMinutes ->
                assertEquals(480, offsetMinutes)
                RemoteRawPointDaySummaryReadResult.Success(
                    listOf(
                        RemoteRawPointDaySummary(
                            dayStartMillis = 1713369600000L,
                            pointCount = 3,
                            maxPointId = 33L,
                        )
                    )
                )
            },
            pointLoader = { _, _, dayStartMillis ->
                assertEquals(1713369600000L, dayStartMillis)
                RemoteRawPointReadResult.Success(
                    listOf(
                        rawPoint(pointId = 31L, timestampMillis = 1713373200000L, latitude = 30.1, longitude = 120.1),
                        rawPoint(pointId = 32L, timestampMillis = 1713373260000L, latitude = 30.2, longitude = 120.2),
                        rawPoint(pointId = 33L, timestampMillis = 1713373320000L, latitude = 30.3, longitude = 120.3),
                    )
                )
            },
            uploadService = ProcessedHistoryUploadService(
                requestExecutor = { request ->
                    val payload = JSONObject(request.body.orEmpty())
                    val histories = payload.getJSONArray("histories")
                    uploadedRows += List(histories.length()) { index ->
                        val item = histories.getJSONObject(index)
                        HistoryUploadRow(
                            historyId = item.getLong("historyId"),
                            timestampMillis = item.getLong("timestampMillis"),
                            distanceKm = item.getDouble("distanceKm"),
                            durationSeconds = item.getInt("durationSeconds"),
                            averageSpeedKmh = item.getDouble("averageSpeedKmh"),
                            title = item.optionalString("title"),
                            startSource = item.optionalString("startSource"),
                            stopSource = item.optionalString("stopSource"),
                            manualStartAt = null,
                            manualStopAt = null,
                            points = emptyList(),
                        )
                    }
                    com.wenhao.record.data.tracking.UploadHttpResponse(
                        statusCode = 200,
                        body = """{"ok":true,"insertedCount":1,"dedupedCount":0,"acceptedHistoryIds":[123]}""",
                    )
                }
            ),
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
            analysisRunner = { points, _ ->
                assertEquals(3, points.size)
                TrackAnalysisResult(
                    scoredPoints = emptyList(),
                    segments = listOf(
                        SegmentCandidate(
                            kind = SegmentKind.DYNAMIC,
                            startTimestamp = 1713373200000L,
                            endTimestamp = 1713373320000L,
                            pointCount = 3,
                        )
                    ),
                    stayClusters = emptyList(),
                )
            },
            stateStorage = stateStorage,
            timeZoneProvider = { TimeZone.getTimeZone("Asia/Shanghai") },
            nowProvider = { 1713400000000L },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(1, uploadedRows.flatten().size)
        assertEquals(33L, stateStorage.load(1713369600000L))
    }

    @Test
    fun `doWork skips day when max point id already synced`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStorage = ProcessedHistorySyncStateStorage(context)
        stateStorage.markSynced(1713369600000L, 33L)
        var pointLoadCount = 0
        val worker = buildWorker(
            context = context,
            daySummaryLoader = { _, _, _ ->
                RemoteRawPointDaySummaryReadResult.Success(
                    listOf(
                        RemoteRawPointDaySummary(
                            dayStartMillis = 1713369600000L,
                            pointCount = 3,
                            maxPointId = 33L,
                        )
                    )
                )
            },
            pointLoader = { _, _, _ ->
                pointLoadCount += 1
                RemoteRawPointReadResult.Success(emptyList())
            },
            uploadService = ProcessedHistoryUploadService(
                requestExecutor = {
                    com.wenhao.record.data.tracking.UploadHttpResponse(
                        statusCode = 200,
                        body = """{"ok":true,"insertedCount":0,"dedupedCount":0,"acceptedHistoryIds":[]}""",
                    )
                }
            ),
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
            analysisRunner = { _, _ ->
                TrackAnalysisResult(emptyList(), emptyList(), emptyList())
            },
            stateStorage = stateStorage,
            timeZoneProvider = { TimeZone.getTimeZone("Asia/Shanghai") },
            nowProvider = { 1713400000000L },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(0, pointLoadCount)
    }

    private fun buildWorker(
        context: Context,
        daySummaryLoader: (TrainingSampleUploadConfig, String, Int) -> RemoteRawPointDaySummaryReadResult,
        pointLoader: (TrainingSampleUploadConfig, String, Long) -> RemoteRawPointReadResult,
        uploadService: ProcessedHistoryUploadService,
        configLoader: (Context) -> TrainingSampleUploadConfig,
        deviceIdProvider: (Context) -> String,
        analysisRunner: (List<AnalyzedPoint>, AnalysisContext?) -> TrackAnalysisResult,
        stateStorage: ProcessedHistorySyncStateStorage,
        timeZoneProvider: () -> TimeZone,
        nowProvider: () -> Long,
    ): ProcessedHistorySyncWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return ProcessedHistorySyncWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    daySummaryLoader = daySummaryLoader,
                    pointLoader = pointLoader,
                    uploadService = uploadService,
                    configLoader = configLoader,
                    deviceIdProvider = deviceIdProvider,
                    analysisRunner = analysisRunner,
                    stateStorage = stateStorage,
                    timeZoneProvider = timeZoneProvider,
                    nowProvider = nowProvider,
                )
            }
        }

        return TestListenableWorkerBuilder.from(context, ProcessedHistorySyncWorker::class.java)
            .setWorkerFactory(factory)
            .build()
    }

    private fun rawPoint(
        pointId: Long,
        timestampMillis: Long,
        latitude: Double,
        longitude: Double,
    ): RawTrackPoint {
        return RawTrackPoint(
            pointId = pointId,
            timestampMillis = timestampMillis,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = 5f,
            altitudeMeters = 12.0,
            speedMetersPerSecond = 1.5f,
            bearingDegrees = 90f,
            provider = "gps",
            sourceType = "LOCATION_MANAGER",
            isMock = false,
            wifiFingerprintDigest = null,
            activityType = "WALKING",
            activityConfidence = 0.9f,
            samplingTier = SamplingTier.ACTIVE,
        )
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() }
    }
}
