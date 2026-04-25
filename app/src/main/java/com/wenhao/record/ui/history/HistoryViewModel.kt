package com.wenhao.record.ui.history

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.wenhao.record.data.history.HistoryDaySummaryItem
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryProjectionRecovery
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.history.RemoteHistoryRepository
import com.wenhao.record.data.history.toSummaryItem
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.tracking.ContinuousPointStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val KEY_SELECTED_DAY = "selected_day_start_millis"
private const val REMOTE_HISTORY_REFRESH_THROTTLE_MS = 20_000L

class HistoryViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val remoteHistoryRepository: RemoteHistoryRepository = RemoteHistoryRepository(),
    private val historyProjectionRecovery: HistoryProjectionRecovery = HistoryProjectionRecovery(),
) : AndroidViewModel(application) {

    private var historyItems: List<HistoryDaySummaryItem> = emptyList()
    private var cachedTotalDistanceKm = 0.0
    private var cachedTotalDurationSeconds = 0
    private var reloadGeneration = 0L
    private var hasRemoteSummaryCache = false
    private var lastRemoteSummaryRefreshElapsed = 0L

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
        val localItems = HistoryStorage.peekDaily(getApplication()).map { it.toSummaryItem() }
        val hasFreshRemoteCache = hasRemoteSummaryCache &&
            (SystemClock.elapsedRealtime() - lastRemoteSummaryRefreshElapsed) < REMOTE_HISTORY_REFRESH_THROTTLE_MS
        historyItems = if (hasRemoteSummaryCache) {
            mergeLocalWithCached(localItems)
        } else {
            localItems
        }
        val hasImmediateLocalItems = historyItems.isNotEmpty()
        recalculateTotals()
        updateContent()

        viewModelScope.launch(Dispatchers.IO) {
            val application = getApplication<Application>()
            if (!hasImmediateLocalItems && !hasRemoteSummaryCache) {
                val existingHistories = HistoryStorage.load(application)
                if (existingHistories.isEmpty()) {
                    val recoveredItems = historyProjectionRecovery.rebuildProjectedItems(
                        existingHistories = existingHistories,
                        rawPoints = ContinuousPointStorage(
                            TrackDatabase.getInstance(application).continuousTrackDao()
                        ).loadCurrentSessionPoints(limit = Int.MAX_VALUE),
                    )
                    if (recoveredItems.isNotEmpty()) {
                        HistoryStorage.upsertProjectedItems(
                            context = application,
                            projectedItems = recoveredItems,
                        )
                    }
                }
            }
            if (hasFreshRemoteCache) {
                return@launch
            }

            val loadResult = try {
                remoteHistoryRepository.loadDailySummaryState(application)
            } catch (_: Exception) {
                RemoteHistoryRepository.DailySummaryLoadResult(
                    items = localItems,
                    remoteStatus = RemoteHistoryRepository.RemoteStatus.FAILURE,
                )
            }
            withContext(Dispatchers.Main) {
                if (generation != reloadGeneration) return@withContext
                when (loadResult.remoteStatus) {
                    RemoteHistoryRepository.RemoteStatus.SUCCESS -> {
                        historyItems = loadResult.items
                        hasRemoteSummaryCache = true
                        lastRemoteSummaryRefreshElapsed = SystemClock.elapsedRealtime()
                        recalculateTotals()
                        updateContent()
                    }

                    RemoteHistoryRepository.RemoteStatus.DISABLED -> {
                        hasRemoteSummaryCache = false
                        lastRemoteSummaryRefreshElapsed = 0L
                        historyItems = loadResult.items
                        recalculateTotals()
                        updateContent()
                    }

                    RemoteHistoryRepository.RemoteStatus.FAILURE -> {
                        hasRemoteSummaryCache = false
                        lastRemoteSummaryRefreshElapsed = 0L
                        if (loadResult.items.isNotEmpty()) {
                            historyItems = loadResult.items
                            recalculateTotals()
                            updateContent()
                        }
                    }
                }
            }
        }
    }

    fun updateContent() {
        if (selectedDayStartMillis != null && historyItems.none { it.dayStartMillis == selectedDayStartMillis }) {
            selectedDayStartMillis = historyItems.firstOrNull()?.dayStartMillis
        }
        pushState()
    }

    fun selectItem(item: HistoryDaySummaryItem) {
        selectedDayStartMillis = item.dayStartMillis
        pushState()
    }

    suspend fun deleteHistory(item: HistoryDaySummaryItem): Boolean {
        val deleted = withContext(Dispatchers.IO) {
            remoteHistoryRepository.deleteDay(getApplication(), item)
        }
        if (!deleted) {
            return false
        }

        val updated = historyItems.toMutableList()
        val index = updated.indexOfFirst { it.dayStartMillis == item.dayStartMillis }
        if (index != -1) {
            updated.removeAt(index)
        }
        historyItems = updated
        if (selectedDayStartMillis == item.dayStartMillis) {
            selectedDayStartMillis = historyItems.firstOrNull()?.dayStartMillis
        }
        hasRemoteSummaryCache = false
        lastRemoteSummaryRefreshElapsed = 0L
        recalculateTotals()
        pushState()
        return true
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

    private fun mergeLocalWithCached(
        localItems: List<HistoryDaySummaryItem>,
    ): List<HistoryDaySummaryItem> {
        val mergedByDay = historyItems.associateBy { item -> item.dayStartMillis }.toMutableMap()
        localItems.forEach { localItem ->
            val cachedItem = mergedByDay[localItem.dayStartMillis]
            mergedByDay[localItem.dayStartMillis] = if (cachedItem == null) {
                localItem
            } else {
                cachedItem.copy(
                    sessionCount = maxOf(cachedItem.sessionCount, localItem.sessionCount),
                    sourceIds = if (localItem.sourceIds.isNotEmpty()) localItem.sourceIds else cachedItem.sourceIds,
                    routeTitle = cachedItem.routeTitle ?: localItem.routeTitle,
                )
            }
        }
        return mergedByDay.values.sortedByDescending { it.dayStartMillis }
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
