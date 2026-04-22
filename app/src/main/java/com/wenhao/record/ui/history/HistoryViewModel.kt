package com.wenhao.record.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.history.RemoteHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val KEY_SELECTED_DAY = "selected_day_start_millis"

class HistoryViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val remoteHistoryRepository: RemoteHistoryRepository = RemoteHistoryRepository(),
) : AndroidViewModel(application) {

    private var historyItems: List<HistoryDayItem> = emptyList()
    private var cachedTotalDistanceKm = 0.0
    private var cachedTotalDurationSeconds = 0
    private var reloadGeneration = 0L

    private val _uiState = MutableStateFlow(
        HistoryScreenUiState(
            totalDistanceText = formatDistance(0.0),
            totalDurationText = formatDuration(0),
            totalCountText = application.getString(R.string.compose_history_days_value, 0),
        )
    )
    val uiState: StateFlow<HistoryScreenUiState> = _uiState.asStateFlow()

    private var selectedDayStartMillis: Long?
        get() = savedStateHandle[KEY_SELECTED_DAY]
        set(value) { savedStateHandle[KEY_SELECTED_DAY] = value }

    fun reload() {
        val generation = nextReloadGeneration()
        historyItems = HistoryStorage.peekDaily(getApplication())
        recalculateTotals()
        updateContent()

        viewModelScope.launch(Dispatchers.IO) {
            val mergedDaily = try {
                remoteHistoryRepository.loadMergedDaily(getApplication())
            } catch (_: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                if (generation != reloadGeneration) return@withContext
                historyItems = mergedDaily
                recalculateTotals()
                updateContent()
            }
        }
    }

    fun updateContent() {
        if (selectedDayStartMillis != null && historyItems.none { it.dayStartMillis == selectedDayStartMillis }) {
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
        viewModelScope.launch(Dispatchers.IO) {
            HistoryStorage.deleteMany(getApplication(), item.sourceIds)
        }
        recalculateTotals()
        pushState()
    }

    private fun pushState() {
        _uiState.value = HistoryScreenUiState(
            items = historyItems,
            selectedDayStartMillis = selectedDayStartMillis,
            totalDistanceText = formatDistance(cachedTotalDistanceKm),
            totalDurationText = formatDuration(cachedTotalDurationSeconds),
            totalCountText = getApplication<Application>().getString(R.string.compose_history_days_value, historyItems.size),
        )
    }

    private fun recalculateTotals() {
        cachedTotalDistanceKm = historyItems.sumOf { it.totalDistanceKm }
        cachedTotalDurationSeconds = historyItems.sumOf { it.totalDurationSeconds }
    }

    @Synchronized
    private fun nextReloadGeneration(): Long {
        reloadGeneration += 1
        return reloadGeneration
    }

    companion object {
        fun formatDistance(totalDistanceKm: Double): String {
            return String.format(Locale.CHINA, "%.1f 公里", totalDistanceKm)
        }

        fun formatDuration(totalSeconds: Int): String {
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

        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    @Suppress("UNCHECKED_CAST")
                    return HistoryViewModel(application, savedStateHandle) as T
                }
            }
        }
    }
}
