package com.wenhao.record.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSampleBatchUploaderTest {

    @Test
    fun `uploads rows in batches and aggregates success counts`() {
        val requestedEventIds = mutableListOf<List<Long>>()
        val progressUpdates = mutableListOf<TrainingSampleBatchProgress>()
        val uploader = TrainingSampleBatchUploader { batch ->
            requestedEventIds += batch.map { it.eventId }
            TrainingSampleUploadResult.Success(
                acceptedEventIds = batch.map { it.eventId },
                insertedCount = batch.size,
                dedupedCount = 0,
            )
        }

        val result = uploader.upload(
            rows = sampleRows(count = 5),
            batchSize = 2,
            onBatchStart = { progressUpdates += it },
        )

        assertTrue(result is TrainingSampleBatchUploadResult.Success)
        val success = result as TrainingSampleBatchUploadResult.Success
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L), listOf(5L)), requestedEventIds)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), success.acceptedEventIds)
        assertEquals(5, success.insertedCount)
        assertEquals(0, success.dedupedCount)
        assertEquals(
            listOf(
                TrainingSampleBatchProgress(batchIndex = 0, totalBatches = 3, batchSize = 2),
                TrainingSampleBatchProgress(batchIndex = 1, totalBatches = 3, batchSize = 2),
                TrainingSampleBatchProgress(batchIndex = 2, totalBatches = 3, batchSize = 1),
            ),
            progressUpdates,
        )
    }

    @Test
    fun `stops on first failed batch and keeps earlier successful ids`() {
        val requestedEventIds = mutableListOf<List<Long>>()
        val uploader = TrainingSampleBatchUploader { batch ->
            requestedEventIds += batch.map { it.eventId }
            if (batch.first().eventId == 3L) {
                TrainingSampleUploadResult.Failure("network down")
            } else {
                TrainingSampleUploadResult.Success(
                    acceptedEventIds = batch.map { it.eventId },
                    insertedCount = batch.size,
                    dedupedCount = 0,
                )
            }
        }

        val result = uploader.upload(
            rows = sampleRows(count = 5),
            batchSize = 2,
        )

        assertTrue(result is TrainingSampleBatchUploadResult.Failure)
        val failure = result as TrainingSampleBatchUploadResult.Failure
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), requestedEventIds)
        assertEquals("network down", failure.message)
        assertEquals(listOf(1L, 2L), failure.acceptedEventIds)
        assertEquals(2, failure.insertedCount)
        assertEquals(0, failure.dedupedCount)
        assertEquals(1, failure.failedBatchIndex)
        assertEquals(3, failure.totalBatches)
    }

    private fun sampleRows(count: Int): List<TrainingSampleRow> {
        return (1..count).map { eventId ->
            TrainingSampleRow(
                eventId = eventId.toLong(),
                recordId = 1L,
                timestampMillis = 1_000L + eventId,
                phase = "ACTIVE",
                isRecording = false,
                startScore = 0.5,
                stopScore = 0.1,
                finalDecision = "START",
                gpsQualityPass = true,
                motionEvidencePass = true,
                frequentPlaceClearPass = true,
                feedbackEligible = true,
                feedbackBlockedReason = null,
                features = mapOf("steps_30s" to 1.0),
                feedbackLabel = "CORRECT",
                startSource = "MANUAL",
                stopSource = "MANUAL",
                manualStartAt = 1_000L,
                manualStopAt = 2_000L,
            )
        }
    }
}
