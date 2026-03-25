package com.wenhao.record.ui.history

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import java.util.Locale

class HistoryController(
    private val context: Context,
) {
    private var historyItems: List<HistoryDayItem> = emptyList()
    private var selectedDayStartMillis: Long? = null
    private var transferBusy = false
    private var recordTabSelected = true

    var uiState by mutableStateOf(
        HistoryScreenUiState(
            totalDistanceText = formatDistance(0.0),
            totalDurationText = formatDuration(0),
            totalCountText = context.getString(R.string.compose_history_days_value, 0),
            isRecordTabSelected = true,
        )
    )
        private set

    fun setTabSelected(isRecord: Boolean) {
        recordTabSelected = isRecord
        pushState()
    }

    fun setTransferBusy(isBusy: Boolean) {
        transferBusy = isBusy
        pushState()
    }

    fun reload() {
        historyItems = HistoryStorage.peekDaily(context)
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
        pushState()
    }

    private fun pushState() {
        val totalDistance = historyItems.sumOf { it.totalDistanceKm }
        val totalDurationSeconds = historyItems.sumOf { it.totalDurationSeconds }

        uiState = HistoryScreenUiState(
            items = historyItems,
            selectedDayStartMillis = selectedDayStartMillis,
            totalDistanceText = formatDistance(totalDistance),
            totalDurationText = formatDuration(totalDurationSeconds),
            totalCountText = context.getString(R.string.compose_history_days_value, historyItems.size),
            isTransferBusy = transferBusy,
            isRecordTabSelected = recordTabSelected,
        )
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
}
