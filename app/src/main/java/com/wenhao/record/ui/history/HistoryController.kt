package com.wenhao.record.ui.history

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.tracking.DecisionEventStorage
import com.wenhao.record.data.tracking.DecisionFeedbackStore
import com.wenhao.record.data.tracking.DecisionFeedbackType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryController(
    private val context: Context,
) {
    private var historyItems: List<HistoryDayItem> = emptyList()
    private var decisionFeedbackItems: List<HistoryDecisionFeedbackItem> = emptyList()
    private var cachedTotalDistanceKm = 0.0
    private var cachedTotalDurationSeconds = 0
    private var selectedDayStartMillis: Long? = null
    private var feedbackEventId: Long? = null
    private var feedbackSheetVisible = false

    var uiState by mutableStateOf(
        HistoryScreenUiState(
            totalDistanceText = formatDistance(0.0),
            totalDurationText = formatDuration(0),
            totalCountText = context.getString(R.string.compose_history_days_value, 0),
        )
    )
        private set

    fun reload() {
        historyItems = HistoryStorage.peekDaily(context)
        decisionFeedbackItems = loadDecisionFeedbackItems()
        recalculateTotals()
        updateContent()
    }

    fun updateContent() {
        if (selectedDayStartMillis != null &&
            historyItems.none { it.dayStartMillis == selectedDayStartMillis }
        ) {
            selectedDayStartMillis = historyItems.firstOrNull()?.dayStartMillis
        }
        pushState()
    }

    fun selectItem(item: HistoryDayItem) {
        selectedDayStartMillis = item.dayStartMillis
        pushState()
    }

    fun deleteHistory(item: HistoryDayItem) {
        val updated = historyItems.toMutableList()
        val index = updated.indexOfFirst { it.dayStartMillis == item.dayStartMillis }
        if (index == -1) return

        updated.removeAt(index)
        historyItems = updated
        if (selectedDayStartMillis == item.dayStartMillis) {
            selectedDayStartMillis = historyItems.firstOrNull()?.dayStartMillis
        }
        HistoryStorage.deleteMany(context, item.sourceIds)
        recalculateTotals()
        pushState()
    }

    fun setDecisionFeedbackSheet(eventId: Long, visible: Boolean) {
        feedbackEventId = if (visible) eventId else null
        feedbackSheetVisible = visible
        pushState()
    }

    fun submitFeedback(type: DecisionFeedbackType) {
        val eventId = feedbackEventId ?: return
        DecisionFeedbackStore.save(context, eventId, type)
        decisionFeedbackItems = loadDecisionFeedbackItems()
        feedbackSheetVisible = false
        feedbackEventId = null
        pushState()
    }

    private fun pushState() {
        uiState = HistoryScreenUiState(
            items = historyItems,
            decisionFeedbackItems = decisionFeedbackItems,
            selectedDayStartMillis = selectedDayStartMillis,
            isFeedbackSheetVisible = feedbackSheetVisible,
            totalDistanceText = formatDistance(cachedTotalDistanceKm),
            totalDurationText = formatDuration(cachedTotalDurationSeconds),
            totalCountText = context.getString(R.string.compose_history_days_value, historyItems.size),
        )
    }

    private fun recalculateTotals() {
        cachedTotalDistanceKm = historyItems.sumOf { it.totalDistanceKm }
        cachedTotalDurationSeconds = historyItems.sumOf { it.totalDurationSeconds }
    }

    private fun formatDistance(totalDistanceKm: Double): String {
        return String.format(Locale.CHINA, "%.1f 公里", totalDistanceKm)
    }

    private fun formatDuration(totalSeconds: Int): String {
        val totalMinutes = totalSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
            hours > 0 -> "${hours}小时"
            totalMinutes > 0 -> "${totalMinutes}分钟"
            else -> "少于 1 分钟"
        }
    }

    private fun loadDecisionFeedbackItems(): List<HistoryDecisionFeedbackItem> {
        return DecisionEventStorage.loadReviewItems(context).map { item ->
            HistoryDecisionFeedbackItem(
                eventId = item.eventId,
                title = when (item.finalDecision) {
                    "START" -> "识别为动态段起点"
                    "STOP" -> "识别为动态段终点"
                    else -> item.finalDecision
                },
                summary = buildString {
                    append(formatEventTime(item.timestampMillis))
                    append(" · 开始 ")
                    append(formatScore(item.startScore))
                    append(" / 结束 ")
                    append(formatScore(item.stopScore))
                    append(" · 阶段 ")
                    append(item.phase)
                },
                feedbackLabel = item.feedbackLabel,
            )
        }
    }

    private fun formatEventTime(timestampMillis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestampMillis))
    }

    private fun formatScore(score: Double): String {
        return String.format(Locale.US, "%.2f", score)
    }
}
