package com.wenhao.record.data.tracking

import android.content.Context
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.decision.DecisionEventEntity
import com.wenhao.record.data.local.decision.DecisionEventWithFeedbackRow
import com.wenhao.record.tracking.decision.DecisionFrame
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class DecisionReviewItem(
    val eventId: Long,
    val timestampMillis: Long,
    val phase: String,
    val isRecording: Boolean,
    val startScore: Double,
    val stopScore: Double,
    val finalDecision: String,
    val feedbackLabel: String?,
)

object DecisionEventStorage {

    fun warmUp(context: Context) {
        TrackDatabase.getInstance(context.applicationContext)
    }

    fun saveFrame(context: Context, frame: DecisionFrame): Long {
        val entity = DecisionEventEntity(
            timestampMillis = frame.vector.timestampMillis,
            phase = frame.vector.phase.name,
            isRecording = frame.vector.isRecording,
            startScore = frame.startScore,
            stopScore = frame.stopScore,
            finalDecision = frame.finalDecision.name,
            featureJson = JSONObject(frame.vector.features).toString(),
        )
        return runBlockingIo {
            TrackDatabase.getInstance(context.applicationContext)
                .decisionDao()
                .insertEvent(entity)
        }
    }

    fun loadReviewItems(
        context: Context,
        limit: Int = 8,
    ): List<DecisionReviewItem> {
        return runBlockingIo {
            TrackDatabase.getInstance(context.applicationContext)
                .decisionDao()
                .getRecentDecisionEvents(limit)
                .map(::toReviewItem)
        }
    }

    private fun toReviewItem(row: DecisionEventWithFeedbackRow): DecisionReviewItem {
        return DecisionReviewItem(
            eventId = row.eventId,
            timestampMillis = row.timestampMillis,
            phase = row.phase,
            isRecording = row.isRecording,
            startScore = row.startScore,
            stopScore = row.stopScore,
            finalDecision = row.finalDecision,
            feedbackLabel = row.feedbackLabel,
        )
    }

    private fun <T> runBlockingIo(block: () -> T): T {
        return runBlocking {
            withContext(Dispatchers.IO) {
                block()
            }
        }
    }
}
