package com.wenhao.record.data.tracking

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.history.TrackRecordSource
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.tracking.decision.DecisionGateBlockReason
import com.wenhao.record.tracking.decision.DecisionGateInput
import com.wenhao.record.tracking.decision.DecisionGateResult
import com.wenhao.record.tracking.decision.DecisionFrame
import com.wenhao.record.tracking.decision.FinalDecision
import com.wenhao.record.tracking.pipeline.FeatureVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class DecisionEventStorageTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TrackDatabase.closeInstance()
        context.deleteDatabase("track_record.db")
    }

    @After
    fun tearDown() {
        TrackDatabase.closeInstance()
        if (::context.isInitialized) {
            context.deleteDatabase("track_record.db")
        }
    }

    @Test
    fun `save frame and feedback then export rows`() {
        saveManualHistoryItem(
            manualStartAt = 40_000L,
            manualStopAt = 120_000L,
        )
        val frame = DecisionFrame(
            vector = FeatureVector(
                timestampMillis = 60_000L,
                features = mapOf("steps_30s" to 4.0),
                isRecording = false,
                phase = TrackingPhase.SUSPECT_MOVING,
            ),
            startScore = 0.91,
            stopScore = 0.02,
            finalDecision = FinalDecision.START,
        )

        val eventId = DecisionEventStorage.saveFrame(context, frame)
        DecisionFeedbackStore.markStartTooEarly(context, eventId)

        val rows = TrainingSampleExporter.exportRows(context)

        assertEquals(1, rows.size)
        assertEquals("START_TOO_EARLY", rows.single().feedbackLabel)
    }

    @Test
    fun `low quality stop event is exported but hidden from review list`() {
        saveManualHistoryItem(
            manualStartAt = 90_000L,
            manualStopAt = 240_000L,
        )
        DecisionEventStorage.saveFrame(
            context = context,
            frame = DecisionFrame(
                vector = FeatureVector(
                    timestampMillis = 120_000L,
                    features = mapOf("steps_30s" to 6.0),
                    isRecording = false,
                    phase = TrackingPhase.SUSPECT_MOVING,
                    gateInput = DecisionGateInput(
                        gpsSampleCount30s = 3.0,
                        gpsAccuracyAvg30s = 12.0,
                        motionEvidence30s = true,
                        insideFrequentPlace = false,
                        isRecording = false,
                        startScore = 0.0,
                        stopScore = 0.0,
                        recordingDurationSeconds = 0.0,
                        stopObservationPassed = false,
                    ),
                ),
                startScore = 0.91,
                stopScore = 0.02,
                finalDecision = FinalDecision.START,
                gateResult = DecisionGateResult.allowAll(),
            ),
        )

        DecisionEventStorage.saveFrame(
            context = context,
            frame = DecisionFrame(
                vector = FeatureVector(
                    timestampMillis = 180_000L,
                    features = mapOf("steps_30s" to 0.0),
                    isRecording = true,
                    phase = TrackingPhase.SUSPECT_STOPPING,
                    gateInput = DecisionGateInput(
                        gpsSampleCount30s = 3.0,
                        gpsAccuracyAvg30s = 60.0,
                        motionEvidence30s = true,
                        insideFrequentPlace = true,
                        isRecording = true,
                        startScore = 0.0,
                        stopScore = 0.0,
                        recordingDurationSeconds = 240.0,
                        stopObservationPassed = true,
                    ),
                ),
                startScore = 0.02,
                stopScore = 0.97,
                finalDecision = FinalDecision.STOP,
                gateResult = DecisionGateResult(
                    startEligible = false,
                    stopEligible = true,
                    startFeedbackEligible = false,
                    stopFeedbackEligible = false,
                    startBlockedReason = DecisionGateBlockReason.GPS_POOR_ACCURACY,
                    stopBlockedReason = null,
                    feedbackBlockedReason = DecisionGateBlockReason.FEEDBACK_BLOCKED_LOW_QUALITY,
                ),
            ),
        )

        val rows = TrainingSampleExporter.exportRows(context)
        val reviewItems = DecisionEventStorage.loadReviewItems(context)

        assertEquals(2, rows.size)
        assertTrue(rows.any { it.finalDecision == "STOP" })
        assertEquals(1, reviewItems.size)
        assertEquals("START", reviewItems.single().finalDecision)
    }

    @Test
    fun `delete uploaded events removes both events and feedback`() {
        saveManualHistoryItem(
            manualStartAt = 40_000L,
            manualStopAt = 180_000L,
        )
        val firstEventId = DecisionEventStorage.saveFrame(
            context = context,
            frame = DecisionFrame(
                vector = FeatureVector(
                    timestampMillis = 60_000L,
                    features = mapOf("steps_30s" to 4.0),
                    isRecording = false,
                    phase = TrackingPhase.SUSPECT_MOVING,
                ),
                startScore = 0.91,
                stopScore = 0.02,
                finalDecision = FinalDecision.START,
            ),
        )
        val secondEventId = DecisionEventStorage.saveFrame(
            context = context,
            frame = DecisionFrame(
                vector = FeatureVector(
                    timestampMillis = 120_000L,
                    features = mapOf("steps_30s" to 2.0),
                    isRecording = true,
                    phase = TrackingPhase.ACTIVE,
                ),
                startScore = 0.11,
                stopScore = 0.22,
                finalDecision = FinalDecision.HOLD,
            ),
        )

        DecisionFeedbackStore.save(context, firstEventId, DecisionFeedbackType.CORRECT, createdAt = 1_000L)
        DecisionFeedbackStore.save(context, secondEventId, DecisionFeedbackType.STOP_TOO_LATE, createdAt = 2_000L)

        val dao = TrackDatabase.getInstance(context).decisionDao()
        DecisionEventStorage.deleteUploadedEvents(context, listOf(firstEventId))

        val remainingEvents = runBlocking { withContext(Dispatchers.IO) { dao.getEvents() } }
        val remainingFeedback = runBlocking { withContext(Dispatchers.IO) { dao.getFeedback() } }
        assertEquals(listOf(secondEventId), remainingEvents.map { it.eventId })
        assertEquals(listOf(secondEventId), remainingFeedback.map { it.eventId })
        assertTrue(remainingEvents.none { it.eventId == firstEventId })
        assertTrue(remainingFeedback.none { it.eventId == firstEventId })
    }

    private fun saveManualHistoryItem(
        manualStartAt: Long,
        manualStopAt: Long,
    ) {
        HistoryStorage.save(
            context = context,
            items = listOf(
                HistoryItem(
                    id = 1L,
                    timestamp = manualStartAt,
                    distanceKm = 1.2,
                    durationSeconds = ((manualStopAt - manualStartAt) / 1000L).toInt(),
                    averageSpeedKmh = 18.0,
                    points = emptyList(),
                    startSource = TrackRecordSource.MANUAL,
                    stopSource = TrackRecordSource.MANUAL,
                    manualStartAt = manualStartAt,
                    manualStopAt = manualStopAt,
                )
            )
        )
    }
}
