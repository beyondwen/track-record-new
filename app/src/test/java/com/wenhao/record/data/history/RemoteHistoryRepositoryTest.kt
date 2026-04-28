package com.wenhao.record.data.history

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val remoteUnique = HistoryDaySummaryItem(
            dayStartMillis = HistoryDayAggregator.startOfDay(1713420000000),
            latestTimestamp = 1713420000000,
            sessionCount = 1,
            totalDistanceKm = 1.2,
            totalDurationSeconds = 300,
            averageSpeedKmh = 14.4,
            sourceIds = listOf(7L),
        )
        val remoteDuplicate = HistoryDaySummaryItem(
            dayStartMillis = HistoryDayAggregator.startOfDay(1713510600000),
            latestTimestamp = 1713510600000,
            sessionCount = 2,
            totalDistanceKm = 3.6,
            totalDurationSeconds = 660,
            averageSpeedKmh = 19.6,
            sourceIds = listOf(88L),
        )
        val repository = RemoteHistoryRepository(
            localDailyLoader = { listOf(HistoryDayAggregator.aggregate(listOf(localItem)).first()) },
            remoteSummaryLoader = { _, _ ->
                RemoteHistoryDaySummaryReadResult.Success(listOf(remoteUnique, remoteDuplicate))
            },
            remoteHistoryDayLoader = { _, _, _ ->
                RemoteHistoryReadResult.Success(emptyList())
            },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = repository.loadMergedDailySummaries(context)

        assertEquals(listOf(8L), result[0].sourceIds)
        assertEquals("2024年4月19日", result[0].displayTitle)
        assertEquals(listOf(7L), result[1].sourceIds)
        assertEquals(1713510600000, result[0].latestTimestamp)
    }




    @Test
    fun `loadMergedDailySummaries applies local raw point route title to remote only day`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dayStartMillis = HistoryDayAggregator.startOfDay(1713510000000)
        val remoteOnly = HistoryDaySummaryItem(
            dayStartMillis = dayStartMillis,
            latestTimestamp = 1713510000000,
            sessionCount = 1,
            totalDistanceKm = 3.6,
            totalDurationSeconds = 660,
            averageSpeedKmh = 19.6,
            sourceIds = listOf(88L),
        )
        val repository = RemoteHistoryRepository(
            localDailyLoader = { emptyList() },
            localRouteTitleLoader = { mapOf(dayStartMillis to "成都天府广场") },
            remoteSummaryLoader = { _, _ ->
                RemoteHistoryDaySummaryReadResult.Success(listOf(remoteOnly))
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
        val remoteItem = historyItem(
            historyId = 12L,
            timestamp = 1713420600000,
            title = "远端权威结果",
        )
        val repository = RemoteHistoryRepository(
            localDailyLoader = { listOf(HistoryDayAggregator.aggregate(listOf(localItem)).first()) },
            localDayLoader = { _, _ ->
                HistoryDayAggregator.aggregate(listOf(localItem)).first()
            },
            remoteSummaryLoader = { _, _ -> RemoteHistoryDaySummaryReadResult.Success(emptyList()) },
            remoteHistoryDayLoader = { _, _, _ -> RemoteHistoryReadResult.Success(listOf(remoteItem)) },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = repository.loadDay(context, HistoryDayAggregator.startOfDay(remoteItem.timestamp))

        assertEquals(listOf(12L), result?.sourceIds)
        assertEquals(1713420600000, result?.latestTimestamp)
    }

    @Test
    fun `loadLocalDay returns cached day without invoking remote loader`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localItem = historyItem(
            historyId = 13L,
            timestamp = 1713420000000,
            title = "本地先展示",
        )
        val repository = RemoteHistoryRepository(
            localDailyLoader = { listOf(HistoryDayAggregator.aggregate(listOf(localItem)).first()) },
            localDayLoader = { _, _ ->
                HistoryDayAggregator.aggregate(listOf(localItem)).first()
            },
            remoteSummaryLoader = { _, _ -> error("remote all should not be called") },
            remoteHistoryDayLoader = { _, _, _ -> error("remote day should not be called") },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = repository.loadLocalDay(context, HistoryDayAggregator.startOfDay(localItem.timestamp))

        assertEquals(listOf(13L), result?.sourceIds)
    }

    @Test
    fun `loadDay falls back to remote when local day is missing`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val remoteItem = historyItem(
            historyId = 11L,
            timestamp = 1713420000000,
            title = "远端回补",
        )
        val repository = RemoteHistoryRepository(
            localDailyLoader = { emptyList() },
            localDayLoader = { _, _ -> null },
            remoteSummaryLoader = { _, _ -> RemoteHistoryDaySummaryReadResult.Success(emptyList()) },
            remoteHistoryDayLoader = { _, _, dayStartMillis ->
                assertEquals(HistoryDayAggregator.startOfDay(remoteItem.timestamp), dayStartMillis)
                RemoteHistoryReadResult.Success(listOf(remoteItem))
            },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = repository.loadDay(context, HistoryDayAggregator.startOfDay(remoteItem.timestamp))

        assertEquals(listOf(11L), result?.sourceIds)
    }

    @Test
    fun `loadDay returns null when remote read fails`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = RemoteHistoryRepository(
            localDailyLoader = { emptyList() },
            localDayLoader = { _, _ -> null },
            remoteSummaryLoader = { _, _ -> RemoteHistoryDaySummaryReadResult.Success(emptyList()) },
            remoteHistoryDayLoader = { _, _, _ ->
                RemoteHistoryReadResult.Failure("network")
            },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        assertNull(repository.loadDay(context, 1713398400000))
    }

    @Test
    fun `deleteDay deletes local data after remote success`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var deletedIds: List<Long> = emptyList()
        val repository = RemoteHistoryRepository(
            remoteDayDelete = { _, _, dayStartMillis ->
                assertEquals(1713398400000L, dayStartMillis)
                RemoteHistoryMutationResult.Success
            },
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

    @Test
    fun `deleteDay keeps local data when remote delete fails`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var localDeleteCalled = false
        val repository = RemoteHistoryRepository(
            remoteDayDelete = { _, _, _ ->
                RemoteHistoryMutationResult.Failure("network")
            },
            localDeleteMany = { _, _ ->
                localDeleteCalled = true
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
                sourceIds = listOf(41L),
            ),
        )

        assertFalse(deleted)
        assertFalse(localDeleteCalled)
    }


    private fun trackPoint(timestamp: Long): TrackPoint {
        return TrackPoint(
            latitude = 30.1,
            longitude = 120.1,
            timestampMillis = timestamp,
        )
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
