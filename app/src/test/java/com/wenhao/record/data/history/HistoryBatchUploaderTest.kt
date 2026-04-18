package com.wenhao.record.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryBatchUploaderTest {

    @Test
    fun `uploads histories in batches and aggregates success counts`() {
        val requestedHistoryIds = mutableListOf<List<Long>>()
        val progressUpdates = mutableListOf<HistoryBatchProgress>()
        val uploader = HistoryBatchUploader { batch ->
            requestedHistoryIds += batch.map { it.historyId }
            HistoryUploadResult.Success(
                acceptedHistoryIds = batch.map { it.historyId },
                insertedCount = batch.size,
                dedupedCount = 0,
            )
        }

        val result = uploader.upload(
            rows = historyRows(count = 5),
            batchSize = 2,
            onBatchStart = { progressUpdates += it },
        )

        assertTrue(result is HistoryBatchUploadResult.Success)
        val success = result as HistoryBatchUploadResult.Success
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L), listOf(5L)), requestedHistoryIds)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), success.acceptedHistoryIds)
        assertEquals(5, success.insertedCount)
        assertEquals(0, success.dedupedCount)
        assertEquals(
            listOf(
                HistoryBatchProgress(batchIndex = 0, totalBatches = 3, batchSize = 2),
                HistoryBatchProgress(batchIndex = 1, totalBatches = 3, batchSize = 2),
                HistoryBatchProgress(batchIndex = 2, totalBatches = 3, batchSize = 1),
            ),
            progressUpdates,
        )
    }

    @Test
    fun `stops on first failed history batch and keeps earlier successful ids`() {
        val requestedHistoryIds = mutableListOf<List<Long>>()
        val uploader = HistoryBatchUploader { batch ->
            requestedHistoryIds += batch.map { it.historyId }
            if (batch.first().historyId == 3L) {
                HistoryUploadResult.Failure("worker down")
            } else {
                HistoryUploadResult.Success(
                    acceptedHistoryIds = batch.map { it.historyId },
                    insertedCount = batch.size,
                    dedupedCount = 0,
                )
            }
        }

        val result = uploader.upload(
            rows = historyRows(count = 5),
            batchSize = 2,
        )

        assertTrue(result is HistoryBatchUploadResult.Failure)
        val failure = result as HistoryBatchUploadResult.Failure
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), requestedHistoryIds)
        assertEquals("worker down", failure.message)
        assertEquals(listOf(1L, 2L), failure.acceptedHistoryIds)
        assertEquals(2, failure.insertedCount)
        assertEquals(0, failure.dedupedCount)
        assertEquals(1, failure.failedBatchIndex)
        assertEquals(3, failure.totalBatches)
    }

    private fun historyRows(count: Int): List<HistoryUploadRow> {
        return (1..count).map { historyId ->
            HistoryUploadRow(
                historyId = historyId.toLong(),
                timestampMillis = 1_000L + historyId,
                distanceKm = historyId.toDouble(),
                durationSeconds = 60 * historyId,
                averageSpeedKmh = 12.3,
                title = "记录 $historyId",
                startSource = "MANUAL",
                stopSource = "MANUAL",
                manualStartAt = 1_000L,
                manualStopAt = 2_000L,
                points = listOf(
                    HistoryUploadPointRow(
                        latitude = 30.0,
                        longitude = 120.0,
                        timestampMillis = 1_000L,
                        accuracyMeters = 3.0,
                        altitudeMeters = null,
                        wgs84Latitude = null,
                        wgs84Longitude = null,
                    )
                ),
            )
        }
    }
}
