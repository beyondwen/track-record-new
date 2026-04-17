package com.wenhao.record.data.tracking

import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.TrackRecordSource
import com.wenhao.record.data.local.decision.DecisionEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualBoundaryTrainingSampleExporterTest {

    @Test
    fun `exports only fully manual record segments`() {
        val manualItems = TrainingSampleExporter.filterManualBoundaryItems(
            listOf(
                HistoryItem(
                    id = 1L,
                    timestamp = 1_000L,
                    distanceKm = 1.0,
                    durationSeconds = 120,
                    averageSpeedKmh = 10.0,
                    points = emptyList(),
                    startSource = TrackRecordSource.MANUAL,
                    stopSource = TrackRecordSource.MANUAL,
                    manualStartAt = 1_000L,
                    manualStopAt = 121_000L,
                ),
                HistoryItem(
                    id = 2L,
                    timestamp = 2_000L,
                    distanceKm = 1.0,
                    durationSeconds = 120,
                    averageSpeedKmh = 10.0,
                    points = emptyList(),
                    startSource = TrackRecordSource.AUTO,
                    stopSource = TrackRecordSource.AUTO,
                ),
            )
        )

        val rows = TrainingSampleExporter.attachManualBoundaryMetadata(
            events = listOf(
                DecisionEventEntity(
                    eventId = 11L,
                    timestampMillis = 20_000L,
                    phase = "ACTIVE",
                    isRecording = true,
                    startScore = 0.7,
                    stopScore = 0.2,
                    finalDecision = "HOLD",
                    featureJson = """{"steps_30s":4.0,"speed_avg_30s":1.6}""",
                    gpsQualityPass = true,
                    motionEvidencePass = true,
                    frequentPlaceClearPass = true,
                    feedbackEligible = false,
                    feedbackBlockedReason = null,
                ),
                DecisionEventEntity(
                    eventId = 12L,
                    timestampMillis = 300_000L,
                    phase = "ACTIVE",
                    isRecording = false,
                    startScore = 0.1,
                    stopScore = 0.9,
                    finalDecision = "STOP",
                    featureJson = """{"steps_30s":0.0,"speed_avg_30s":0.2}""",
                    gpsQualityPass = true,
                    motionEvidencePass = false,
                    frequentPlaceClearPass = true,
                    feedbackEligible = false,
                    feedbackBlockedReason = null,
                ),
            ),
            feedbackByEventId = emptyMap(),
            manualItems = manualItems,
        )

        assertEquals(1, rows.size)
        assertTrue(rows.all { it.startSource == "MANUAL" && it.stopSource == "MANUAL" })
        assertEquals(1L, rows.first().recordId)
        assertEquals(1_000L, rows.first().manualStartAt)
        assertEquals(121_000L, rows.first().manualStopAt)
    }
}
