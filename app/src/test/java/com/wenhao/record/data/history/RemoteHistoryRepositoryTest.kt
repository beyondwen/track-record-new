package com.wenhao.record.data.history

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.tracking.RawTrackPoint
import com.wenhao.record.data.tracking.RemoteRawPointDaySummary
import com.wenhao.record.data.tracking.RemoteRawPointDaySummaryReadResult
import com.wenhao.record.data.tracking.RemoteRawPointReadResult
import com.wenhao.record.data.tracking.SamplingTier
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class RemoteHistoryRepositoryTest {

    @Test
    fun `loadMergedDailySummaries merges remote summaries and preserves local source ids`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localItem = historyItem(
            historyId = 8L,
            timestamp = 1713510000000,
            title = "本地覆盖",
        )
        val remoteUnique = rawDaySummary(
            dayStartMillis = HistoryDayAggregator.startOfDay(1713420000000),
            firstPointAt = 1713420000000,
            lastPointAt = 1713420300000,
            pointCount = 128,
            maxPointId = 631,
        )
        val remoteDuplicate = rawDaySummary(
            dayStartMillis = HistoryDayAggregator.startOfDay(1713510600000),
            firstPointAt = 1713510000000,
            lastPointAt = 1713510600000,
            pointCount = 88,
            maxPointId = 888,
        )
        val repository = RemoteHistoryRepository(
            localDailyLoader = { listOf(HistoryDayAggregator.aggregate(listOf(localItem)).first()) },
            rawDaySummaryLoader = { _, _, _ ->
                RemoteRawPointDaySummaryReadResult.Success(listOf(remoteUnique, remoteDuplicate))
            },
            rawPointDayLoader = { _, _, _ ->
                RemoteRawPointReadResult.Success(emptyList())
            },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = repository.loadMergedDailySummaries(context)

        assertEquals(listOf(8L), result[0].sourceIds)
        assertEquals("2024年4月19日", result[0].displayTitle)
        assertEquals(emptyList<Long>(), result[1].sourceIds)
        assertEquals(1713510600000, result[0].latestTimestamp)
    }




    @Test
    fun `loadMergedDailySummaries applies local raw point route title to remote only day`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dayStartMillis = HistoryDayAggregator.startOfDay(1713510000000)
        val remoteOnly = rawDaySummary(
            dayStartMillis = dayStartMillis,
            firstPointAt = 1713510000000,
            lastPointAt = 1713510600000,
            pointCount = 88,
            maxPointId = 888,
        )
        val repository = RemoteHistoryRepository(
            localDailyLoader = { emptyList() },
            localRouteTitleLoader = { mapOf(dayStartMillis to "成都天府广场") },
            rawDaySummaryLoader = { _, _, _ ->
                RemoteRawPointDaySummaryReadResult.Success(listOf(remoteOnly))
            },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = repository.loadMergedDailySummaries(context)

        assertEquals("成都天府广场", result.single().routeTitle)
    }

    @Test
    fun `loadDay prefers remote day even when local route is visible`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localItem = historyItem(
            historyId = 12L,
            timestamp = 1713420000000,
            title = "本地结果",
        )
        val remotePoints = rawPoints(startTimestamp = 1713420600000, pointCount = 2)
        val repository = RemoteHistoryRepository(
            localDailyLoader = { listOf(HistoryDayAggregator.aggregate(listOf(localItem)).first()) },
            localDayLoader = { _, _ ->
                HistoryDayAggregator.aggregate(listOf(localItem)).first()
            },
            rawDaySummaryLoader = { _, _, _ -> RemoteRawPointDaySummaryReadResult.Success(emptyList()) },
            rawPointDayLoader = { _, _, _ -> RemoteRawPointReadResult.Success(remotePoints) },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = repository.loadDay(context, HistoryDayAggregator.startOfDay(1713420600000))

        assertEquals(emptyList<Long>(), result?.sourceIds)
        assertEquals(1713420660000, result?.latestTimestamp)
    }

    @Test
    fun `loadDay falls back to remote when local day is missing`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val remotePoints = rawPoints(startTimestamp = 1713420000000, pointCount = 2)
        val repository = RemoteHistoryRepository(
            localDailyLoader = { emptyList() },
            localDayLoader = { _, _ -> null },
            rawDaySummaryLoader = { _, _, _ -> RemoteRawPointDaySummaryReadResult.Success(emptyList()) },
            rawPointDayLoader = { _, _, dayStartMillis ->
                assertEquals(HistoryDayAggregator.startOfDay(1713420000000), dayStartMillis)
                RemoteRawPointReadResult.Success(remotePoints)
            },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = repository.loadDay(context, HistoryDayAggregator.startOfDay(1713420000000))

        assertEquals(emptyList<Long>(), result?.sourceIds)
    }

    @Test
    fun `loadDay returns null when remote read fails`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = RemoteHistoryRepository(
            localDailyLoader = { emptyList() },
            localDayLoader = { _, _ -> null },
            rawDaySummaryLoader = { _, _, _ -> RemoteRawPointDaySummaryReadResult.Success(emptyList()) },
            rawPointDayLoader = { _, _, _ ->
                RemoteRawPointReadResult.Failure("network")
            },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        assertNull(repository.loadDay(context, 1713398400000))
    }

    @Test
    fun `deleteDay deletes local data`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var deletedIds: List<Long> = emptyList()
        val repository = RemoteHistoryRepository(
            localDeleteMany = { _, historyIds ->
                deletedIds = historyIds
            },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val deleted = repository.deleteDay(
            context = context,
            item = HistoryDaySummaryItem(
                dayStartMillis = 1713398400000L,
                latestTimestamp = 1713402000000L,
                sessionCount = 1,
                totalDistanceKm = 2.3,
                totalDurationSeconds = 600,
                averageSpeedKmh = 13.8,
                sourceIds = listOf(41L, 42L),
            ),
        )

        assertTrue(deleted)
        assertEquals(listOf(41L, 42L), deletedIds)
    }


    private fun trackPoint(timestamp: Long): TrackPoint {
        return TrackPoint(
            latitude = 30.1,
            longitude = 120.1,
            timestampMillis = timestamp,
        )
    }

    private fun rawDaySummary(
        dayStartMillis: Long,
        firstPointAt: Long,
        lastPointAt: Long,
        pointCount: Int,
        maxPointId: Long,
    ): RemoteRawPointDaySummary {
        return RemoteRawPointDaySummary(
            dayStartMillis = dayStartMillis,
            firstPointAt = firstPointAt,
            lastPointAt = lastPointAt,
            pointCount = pointCount,
            maxPointId = maxPointId,
            totalDistanceKm = 1.2,
            totalDurationSeconds = ((lastPointAt - firstPointAt).coerceAtLeast(0L) / 1_000L).toInt(),
            averageSpeedKmh = 14.4,
        )
    }

    private fun rawPoints(startTimestamp: Long, pointCount: Int): List<RawTrackPoint> {
        return List(pointCount) { index ->
            RawTrackPoint(
                pointId = index + 1L,
                timestampMillis = startTimestamp + (index * 60_000L),
                latitude = 30.1 + (index * 0.1),
                longitude = 120.1 + (index * 0.1),
                accuracyMeters = 5f,
                altitudeMeters = null,
                speedMetersPerSecond = null,
                bearingDegrees = null,
                provider = "gps",
                sourceType = "LOCATION_MANAGER",
                isMock = false,
                wifiFingerprintDigest = null,
                activityType = null,
                activityConfidence = null,
                samplingTier = SamplingTier.ACTIVE,
            )
        }
    }

    private fun historyItem(
        historyId: Long,
        timestamp: Long,
        title: String,
        pointCount: Int = 2,
    ): HistoryItem {
        return HistoryItem(
            id = historyId,
            timestamp = timestamp,
            distanceKm = 1.2,
            durationSeconds = 300,
            averageSpeedKmh = 14.4,
            title = title,
            points = List(pointCount) { index ->
                TrackPoint(
                    latitude = 30.1 + (index * 0.1),
                    longitude = 120.1 + (index * 0.1),
                    timestampMillis = timestamp + (index * 60_000L),
                )
            },
        )
    }
}
