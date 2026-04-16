package com.wenhao.record.ui.history

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.tracking.DecisionEventStorage
import com.wenhao.record.data.tracking.DecisionFeedbackType
import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.tracking.decision.DecisionFrame
import com.wenhao.record.tracking.decision.FinalDecision
import com.wenhao.record.tracking.pipeline.FeatureVector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class HistoryControllerTest {

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
        context.deleteDatabase("track_record.db")
    }

    @Test
    fun `submitting feedback updates ui state`() {
        val eventId = DecisionEventStorage.saveFrame(
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
        val controller = HistoryController(context)

        controller.reload()
        assertTrue(controller.uiState.decisionFeedbackItems.any { it.eventId == eventId })

        controller.setDecisionFeedbackSheet(eventId = eventId, visible = true)
        controller.submitFeedback(DecisionFeedbackType.START_TOO_EARLY)

        assertFalse(controller.uiState.isFeedbackSheetVisible)
        assertEquals(
            "START_TOO_EARLY",
            controller.uiState.decisionFeedbackItems.first { it.eventId == eventId }.feedbackLabel,
        )
    }
}
