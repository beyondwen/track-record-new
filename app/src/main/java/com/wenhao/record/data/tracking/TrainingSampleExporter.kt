package com.wenhao.record.data.tracking

import android.content.Context
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.history.TrackRecordSource
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.decision.DecisionDao
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
    private const val SQLITE_IN_LIMIT_SAFE_CHUNK = 900
    private const val EVENT_EXPORT_PAGE_SIZE = 200

    fun exportRows(context: Context): List<TrainingSampleRow> {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val dao = TrackDatabase.getInstance(context.applicationContext).decisionDao()
                val manualItems = filterManualBoundaryItems(HistoryStorage.load(context.applicationContext))
                if (manualItems.isEmpty()) {
                    return@withContext emptyList()
                }
                val events = loadEventsForManualItems(dao, manualItems)
                val feedbackByEventId = loadFeedbackByEventId(dao, events.map { it.eventId })
                attachManualBoundaryMetadata(
                    events = events,
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

    internal data class ManualSampleWindow(
        val startMillis: Long,
        val endMillis: Long,
    )

    internal fun mergeManualSampleWindows(
        manualItems: List<HistoryItem>,
    ): List<ManualSampleWindow> {
        if (manualItems.isEmpty()) return emptyList()

        val windows = manualItems.mapNotNull { item ->
            val manualStartAt = item.manualStartAt ?: return@mapNotNull null
            val manualStopAt = item.manualStopAt ?: return@mapNotNull null
            ManualSampleWindow(
                startMillis = manualStartAt - START_WINDOW_LEAD_MS,
                endMillis = manualStopAt + STOP_WINDOW_TRAIL_MS,
            )
        }.sortedBy { it.startMillis }

        if (windows.isEmpty()) return emptyList()

        val merged = mutableListOf(windows.first())
        for (window in windows.drop(1)) {
            val lastWindow = merged.last()
            if (window.startMillis <= lastWindow.endMillis) {
                merged[merged.lastIndex] = lastWindow.copy(
                    endMillis = maxOf(lastWindow.endMillis, window.endMillis)
                )
            } else {
                merged += window
            }
        }
        return merged
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

    private fun loadEventsForManualItems(
        dao: DecisionDao,
        manualItems: List<HistoryItem>,
    ): List<DecisionEventEntity> {
        val windows = mergeManualSampleWindows(manualItems)
        if (windows.isEmpty()) return emptyList()

        val byEventId = linkedMapOf<Long, DecisionEventEntity>()
        for (window in windows) {
            loadEventsForWindowPaged(window) { startMillis, endMillis, limit, offset ->
                dao.getEventsBetweenPaged(
                    startMillis = startMillis,
                    endMillis = endMillis,
                    limit = limit,
                    offset = offset,
                )
            }.forEach { event ->
                byEventId.putIfAbsent(event.eventId, event)
            }
        }
        return byEventId.values.toList()
    }

    internal fun loadEventsForWindowPaged(
        window: ManualSampleWindow,
        pageSize: Int = EVENT_EXPORT_PAGE_SIZE,
        loadPage: (startMillis: Long, endMillis: Long, limit: Int, offset: Int) -> List<DecisionEventEntity>,
    ): List<DecisionEventEntity> {
        require(pageSize > 0) { "pageSize must be positive" }

        val loadedEvents = mutableListOf<DecisionEventEntity>()
        var offset = 0
        while (true) {
            val page = loadPage(window.startMillis, window.endMillis, pageSize, offset)
            if (page.isEmpty()) break
            loadedEvents += page
            if (page.size < pageSize) break
            offset += page.size
        }
        return loadedEvents
    }

    private fun loadFeedbackByEventId(
        dao: DecisionDao,
        eventIds: List<Long>,
    ): Map<Long, String> {
        val sanitizedEventIds = eventIds.distinct().filter { it > 0L }
        if (sanitizedEventIds.isEmpty()) return emptyMap()

        return sanitizedEventIds
            .chunked(SQLITE_IN_LIMIT_SAFE_CHUNK)
            .flatMap { chunk -> dao.getFeedbackForEventIds(chunk) }
            .associateBy({ it.eventId }, { it.feedbackType })
    }
}
