package com.wenhao.record.data.tracking

import android.content.Context
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.history.TrackRecordSource
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.decision.DecisionEventEntity
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class TrainingSampleRow(
    val eventId: Long,
    val recordId: Long? = null,
    val timestampMillis: Long,
    val phase: String,
    val isRecording: Boolean,
    val startScore: Double,
    val stopScore: Double,
    val finalDecision: String,
    val gpsQualityPass: Boolean,
    val motionEvidencePass: Boolean,
    val frequentPlaceClearPass: Boolean,
    val feedbackEligible: Boolean,
    val feedbackBlockedReason: String?,
    val features: Map<String, Double>,
    val feedbackLabel: String?,
    val startSource: String? = null,
    val stopSource: String? = null,
    val manualStartAt: Long? = null,
    val manualStopAt: Long? = null,
)

object TrainingSampleExporter {
    private const val START_WINDOW_LEAD_MS = 30_000L
    private const val STOP_WINDOW_TRAIL_MS = 60_000L

    fun exportRows(context: Context): List<TrainingSampleRow> {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val dao = TrackDatabase.getInstance(context.applicationContext).decisionDao()
                val feedbackByEventId = dao.getFeedback()
                    .associateBy({ it.eventId }, { it.feedbackType })
                val manualItems = filterManualBoundaryItems(HistoryStorage.load(context.applicationContext))
                attachManualBoundaryMetadata(
                    events = dao.getEvents(),
                    feedbackByEventId = feedbackByEventId,
                    manualItems = manualItems,
                )
            }
        }
    }

    internal fun filterManualBoundaryItems(items: List<HistoryItem>): List<HistoryItem> {
        return items.filter { item ->
            item.startSource == TrackRecordSource.MANUAL &&
                item.stopSource == TrackRecordSource.MANUAL &&
                item.manualStartAt != null &&
                item.manualStopAt != null
        }.sortedBy { it.manualStartAt }
    }

    internal fun attachManualBoundaryMetadata(
        events: List<DecisionEventEntity>,
        feedbackByEventId: Map<Long, String>,
        manualItems: List<HistoryItem>,
    ): List<TrainingSampleRow> {
        if (events.isEmpty() || manualItems.isEmpty()) return emptyList()

        return events.mapNotNull { event ->
            val matchedItem = manualItems.firstOrNull { item ->
                val manualStartAt = item.manualStartAt ?: return@firstOrNull false
                val manualStopAt = item.manualStopAt ?: return@firstOrNull false
                event.timestampMillis in (manualStartAt - START_WINDOW_LEAD_MS)..(manualStopAt + STOP_WINDOW_TRAIL_MS)
            } ?: return@mapNotNull null

            TrainingSampleRow(
                eventId = event.eventId,
                recordId = matchedItem.id,
                timestampMillis = event.timestampMillis,
                phase = event.phase,
                isRecording = event.isRecording,
                startScore = event.startScore,
                stopScore = event.stopScore,
                finalDecision = event.finalDecision,
                gpsQualityPass = event.gpsQualityPass,
                motionEvidencePass = event.motionEvidencePass,
                frequentPlaceClearPass = event.frequentPlaceClearPass,
                feedbackEligible = event.feedbackEligible,
                feedbackBlockedReason = event.feedbackBlockedReason,
                features = parseFeatures(event.featureJson),
                feedbackLabel = feedbackByEventId[event.eventId],
                startSource = matchedItem.startSource.name,
                stopSource = matchedItem.stopSource.name,
                manualStartAt = matchedItem.manualStartAt,
                manualStopAt = matchedItem.manualStopAt,
            )
        }
    }

    private fun parseFeatures(featureJson: String): Map<String, Double> {
        val json = JSONObject(featureJson)
        return buildMap {
            json.keys().forEach { key ->
                put(key, json.optDouble(key))
            }
        }
    }
}
