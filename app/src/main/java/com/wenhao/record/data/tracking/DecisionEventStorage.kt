package com.wenhao.record.data.tracking

import android.content.Context
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.decision.DecisionEventEntity
import com.wenhao.record.data.local.decision.DecisionEventWithFeedbackRow
import com.wenhao.record.tracking.decision.DecisionFrame
import com.wenhao.record.tracking.decision.DecisionGateBlockReason
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
    val feedbackEligible: Boolean,
    val feedbackBlockedReason: String?,
    val feedbackLabel: String?,
)

object DecisionEventStorage {

    fun warmUp(context: Context) {
        TrackDatabase.getInstance(context.applicationContext)
    }

    fun saveFrame(context: Context, frame: DecisionFrame): Long {
        val gateInput = frame.vector.gateInput
        val entity = DecisionEventEntity(
            timestampMillis = frame.vector.timestampMillis,
            phase = frame.vector.phase.name,
            isRecording = frame.vector.isRecording,
            startScore = frame.startScore,
            stopScore = frame.stopScore,
            finalDecision = frame.finalDecision.name,
            featureJson = JSONObject(frame.vector.features).toString(),
            gpsQualityPass = gateInput.gpsSampleCount30s >= 2.0 &&
                gateInput.gpsAccuracyAvg30s in 0.0..35.0,
            motionEvidencePass = gateInput.motionEvidence30s,
            frequentPlaceClearPass = !gateInput.insideFrequentPlace,
            feedbackEligible = when (frame.finalDecision) {
                com.wenhao.record.tracking.decision.FinalDecision.START -> frame.gateResult.startFeedbackEligible
                com.wenhao.record.tracking.decision.FinalDecision.STOP -> frame.gateResult.stopFeedbackEligible
                com.wenhao.record.tracking.decision.FinalDecision.HOLD -> false
            },
            feedbackBlockedReason = frame.gateResult.feedbackBlockedReason?.name,
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

    fun deleteUploadedEvents(
        context: Context,
        eventIds: List<Long>,
    ) {
        val sanitizedEventIds = eventIds.distinct().filter { it > 0L }
        if (sanitizedEventIds.isEmpty()) return
        runBlockingIo {
            val dao = TrackDatabase.getInstance(context.applicationContext).decisionDao()
            dao.deleteFeedbackForEventIds(sanitizedEventIds)
            dao.deleteEventsByIds(sanitizedEventIds)
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
            feedbackEligible = row.feedbackEligible,
            feedbackBlockedReason = row.feedbackBlockedReason,
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
