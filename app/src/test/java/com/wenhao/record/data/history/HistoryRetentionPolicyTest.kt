package com.wenhao.record.data.history

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class HistoryRetentionPolicyTest {

    @Test
    fun `pruneUploadedHistories deletes only uploaded histories older than retention`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deletedIds = mutableListOf<List<Long>>()
        val removedUploadedIds = mutableListOf<List<Long>>()

        val prunedIds = HistoryRetentionPolicy.pruneUploadedHistories(
            context = context,
            uploadedHistoryIds = setOf(1L, 2L, 3L),
            nowMillis = 1_000_000_000L,
            historyLoader = {
                listOf(
                    historyItem(historyId = 1L, timestamp = 1_000_000_000L - HistoryRetentionPolicy.RETENTION_WINDOW_MILLIS - 1),
                    historyItem(historyId = 2L, timestamp = 1_000_000_000L - HistoryRetentionPolicy.RETENTION_WINDOW_MILLIS + 1),
                    historyItem(historyId = 4L, timestamp = 1_000_000_000L - HistoryRetentionPolicy.RETENTION_WINDOW_MILLIS - 10),
                )
            },
            deleteMany = { _, ids -> deletedIds += ids },
            removeUploadedIds = { _, ids -> removedUploadedIds += ids },
        )

        assertEquals(listOf(1L), prunedIds)
        assertEquals(listOf(listOf(1L)), deletedIds)
        assertEquals(listOf(listOf(1L)), removedUploadedIds)
    }

    private fun historyItem(historyId: Long, timestamp: Long): HistoryItem {
        return HistoryItem(
            id = historyId,
            timestamp = timestamp,
            distanceKm = 1.0,
            durationSeconds = 60,
            averageSpeedKmh = 60.0,
        )
    }
}
