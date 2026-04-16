package com.wenhao.record.data.tracking

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.tracking.decision.DecisionFrame
import com.wenhao.record.tracking.decision.FinalDecision
import com.wenhao.record.tracking.pipeline.FeatureVector
import org.junit.After
import org.junit.Assert.assertEquals
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
}
