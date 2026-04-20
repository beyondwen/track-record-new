package com.wenhao.record.ui.history

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.history.RemoteHistoryRepository
import com.wenhao.record.util.AppTaskExecutor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking

class HistoryController(
    private val context: Context,
    private val remoteHistoryRepository: RemoteHistoryRepository = RemoteHistoryRepository(),
) {
    private var historyItems: List<HistoryDayItem> = emptyList()
    private var cachedTotalDistanceKm = 0.0
    private var cachedTotalDurationSeconds = 0
    private var selectedDayStartMillis: Long? = null
    private var reloadGeneration = 0L

    var uiState by mutableStateOf(
        HistoryScreenUiState(
            totalDistanceText = formatDistance(0.0),
            totalDurationText = formatDuration(0),
            totalCountText = context.getString(R.string.compose_history_days_value, 0),
        )
    )
        private set

    fun reload() {
        val generation = nextReloadGeneration()
        historyItems = HistoryStorage.peekDaily(context)
        recalculateTotals()
        updateContent()

        val appContext = context.applicationContext
        AppTaskExecutor.runOnIo {
            val mergedDaily = runBlocking {
                remoteHistoryRepository.loadMergedDaily(appContext)
            }
            AppTaskExecutor.runOnMain {
                if (generation != reloadGeneration) return@runOnMain
                historyItems = mergedDaily
                recalculateTotals()
                updateContent()
            }
        }
    }

    fun updateContent() {
        if (selectedDayStartMillis != null && historyItems.none { it.dayStartMillis == selectedDayStartMillis }
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

    private fun pushState() {
        uiState = HistoryScreenUiState(
            items = historyItems,
            selectedDayStartMillis = selectedDayStartMillis,
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

    @Synchronized
    private fun nextReloadGeneration(): Long {
        reloadGeneration += 1
        return reloadGeneration
    }
}
