package com.wenhao.record.data.history

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class RemoteHistoryRepositoryTest {

    @Test
    fun `loadMergedDaily merges remote histories and prefers remote duplicates`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localItem = historyItem(
            historyId = 8L,
            timestamp = 1713510000000,
            title = "本地覆盖",
        )
        val remoteUnique = historyItem(
            historyId = 7L,
            timestamp = 1713420000000,
            title = "远端旧记录",
        )
        val remoteDuplicate = historyItem(
            historyId = 8L,
            timestamp = 1713510600000,
            title = "远端重复",
        )
        val repository = RemoteHistoryRepository(
            localHistoryLoader = { listOf(localItem) },
            remoteHistoryLoader = { _, _ ->
                RemoteHistoryReadResult.Success(listOf(remoteUnique, remoteDuplicate))
            },
            remoteHistoryDayLoader = { _, _, _ ->
                RemoteHistoryReadResult.Success(emptyList())
            },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = repository.loadMergedDaily(context)

        assertEquals(listOf(8L), result[0].sourceIds)
        assertEquals("2024年4月19日", result[0].displayTitle)
        assertEquals(listOf(7L), result[1].sourceIds)
        assertEquals(1713510600000, result[0].latestTimestamp)
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
            localHistoryLoader = { listOf(localItem) },
            localDayLoader = { _, _ ->
                HistoryDayAggregator.aggregate(listOf(localItem)).first()
            },
            remoteHistoryLoader = { _, _ -> RemoteHistoryReadResult.Success(emptyList()) },
            remoteHistoryDayLoader = { _, _, _ -> RemoteHistoryReadResult.Success(listOf(remoteItem)) },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        val result = repository.loadDay(context, HistoryDayAggregator.startOfDay(remoteItem.timestamp))

        assertEquals(listOf(12L), result?.sourceIds)
        assertEquals(1713420600000, result?.latestTimestamp)
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
            localHistoryLoader = { emptyList() },
            localDayLoader = { _, _ -> null },
            remoteHistoryLoader = { _, _ -> RemoteHistoryReadResult.Success(emptyList()) },
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
            localHistoryLoader = { emptyList() },
            localDayLoader = { _, _ -> null },
            remoteHistoryLoader = { _, _ -> RemoteHistoryReadResult.Success(emptyList()) },
            remoteHistoryDayLoader = { _, _, _ ->
                RemoteHistoryReadResult.Failure("network")
            },
            configLoader = { TrainingSampleUploadConfig("https://worker.example.com", "token") },
            deviceIdProvider = { "device-1" },
        )

        assertNull(repository.loadDay(context, 1713398400000))
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
