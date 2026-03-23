package com.wenhao.record.ui.history

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.history.TrackQualityLevel

class HistoryController(
    private val activity: AppCompatActivity,
    rootView: View,
    private val onHistoryOpen: (HistoryDayItem) -> Unit
) {
    private val historyScreen: View = rootView.findViewById(R.id.historyScreen)
    private val rvHistoryPage: RecyclerView = rootView.findViewById(R.id.rvHistoryPage)
    private val layoutHistoryEmptyPage: View = rootView.findViewById(R.id.layoutHistoryEmptyPage)
    private val tvHistoryPageCount: TextView = rootView.findViewById(R.id.tvHistoryPageCount)
    private val tvHistoryPageSubtitle: TextView = rootView.findViewById(R.id.tvHistoryPageSubtitle)
    private val tvHistoryTotalDistance: TextView = rootView.findViewById(R.id.tvHistoryTotalDistance)
    private val tvHistoryTotalDuration: TextView = rootView.findViewById(R.id.tvHistoryTotalDuration)
    private val tvHistoryExport: TextView = rootView.findViewById(R.id.tvHistoryExport)
    private val tvHistoryImport: TextView = rootView.findViewById(R.id.tvHistoryImport)
    private val ivHistoryPageTabRecord: ImageView = rootView.findViewById(R.id.ivHistoryPageTabRecord)
    private val ivHistoryPageTabHistory: ImageView = rootView.findViewById(R.id.ivHistoryPageTabHistory)
    private val tvHistoryPageTabRecord: TextView = rootView.findViewById(R.id.tvHistoryPageTabRecord)
    private val tvHistoryPageTabHistory: TextView = rootView.findViewById(R.id.tvHistoryPageTabHistory)
    private val historyPageTabRecord: View = rootView.findViewById(R.id.historyPageTabRecord)

    private var historyItems: List<HistoryDayItem> = emptyList()
    private var selectedDayStartMillis: Long? = null
    private var transferBusy = false

    private val historyAdapter = HistoryAdapter(
        onHistoryClick = { item, _ -> handleHistoryClick(item) },
        onHistoryLongClick = { item, _ -> showHistoryActions(item) }
    )

    init {
        rvHistoryPage.layoutManager = LinearLayoutManager(activity)
        rvHistoryPage.adapter = historyAdapter
    }

    fun bindNavigation(
        onRecordClick: () -> Unit,
        onExportClick: () -> Unit,
        onImportClick: () -> Unit
    ) {
        historyPageTabRecord.setOnClickListener { onRecordClick() }
        tvHistoryExport.setOnClickListener { onExportClick() }
        tvHistoryImport.setOnClickListener { onImportClick() }
    }

    fun setVisible(isVisible: Boolean) {
        historyScreen.isVisible = isVisible
    }

    fun setTabSelected(isRecord: Boolean) {
        val selectedColor = ContextCompat.getColor(activity, R.color.md_theme_light_primary)
        val defaultColor = ContextCompat.getColor(activity, R.color.dashboard_nav_inactive)

        ivHistoryPageTabRecord.setColorFilter(if (isRecord) selectedColor else defaultColor)
        tvHistoryPageTabRecord.setTextColor(if (isRecord) selectedColor else defaultColor)
        ivHistoryPageTabHistory.setColorFilter(if (isRecord) defaultColor else selectedColor)
        tvHistoryPageTabHistory.setTextColor(if (isRecord) defaultColor else selectedColor)
    }

    fun setTransferBusy(isBusy: Boolean) {
        transferBusy = isBusy
        updateTransferActionState(historyItems.isNotEmpty())
    }

    fun reload() {
        historyItems = HistoryStorage.peekDaily(activity)
    }

    fun updateContent() {
        if (selectedDayStartMillis != null &&
            historyItems.none { it.dayStartMillis == selectedDayStartMillis }
        ) {
            selectedDayStartMillis = historyItems.firstOrNull()?.dayStartMillis
        }
        historyAdapter.setSelectedDayStartMillis(selectedDayStartMillis)
        historyAdapter.submitHistoryList(historyItems)

        val count = historyItems.size
        val hasItems = count > 0
        val latest = historyItems.firstOrNull()
        val totalDistance = historyItems.sumOf { it.totalDistanceKm }
        val totalDurationSeconds = historyItems.sumOf { it.totalDurationSeconds }
        val lowQualityCount = historyItems.count { item ->
            item.quality.level == TrackQualityLevel.LOW
        }

        tvHistoryPageCount.text = activity.getString(R.string.dashboard_history_count_value, count)
        tvHistoryTotalDistance.text = activity.getString(R.string.history_total_distance, totalDistance)
        tvHistoryTotalDuration.text = activity.getString(
            R.string.history_total_duration,
            formatHistoryTotalDuration(totalDurationSeconds)
        )
        updateTransferActionState(hasItems)
        layoutHistoryEmptyPage.visibility = if (hasItems) View.GONE else View.VISIBLE
        rvHistoryPage.visibility = if (hasItems) View.VISIBLE else View.GONE

        val subtitle = latest?.let {
            activity.getString(R.string.dashboard_history_latest, it.displayTitle)
        } ?: activity.getString(R.string.dashboard_history_default_subtitle)
        tvHistoryPageSubtitle.text = if (lowQualityCount > 0) {
            activity.getString(R.string.dashboard_history_low_quality_hint, subtitle, lowQualityCount)
        } else {
            subtitle
        }
    }

    private fun handleHistoryClick(item: HistoryDayItem) {
        if (item.points.isEmpty()) {
            Toast.makeText(activity, R.string.dashboard_history_no_route, Toast.LENGTH_SHORT).show()
            return
        }

        selectedDayStartMillis = item.dayStartMillis
        historyAdapter.setSelectedDayStartMillis(item.dayStartMillis)
        historyAdapter.submitHistoryList(historyItems)
        onHistoryOpen(item)
    }

    private fun showHistoryActions(item: HistoryDayItem) {
        val options = arrayOf(activity.getString(R.string.history_option_delete))
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.history_list_options_title)
            .setItems(options) { _, which ->
                if (which == 0) {
                    confirmDeleteHistory(item)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmDeleteHistory(item: HistoryDayItem) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.history_delete_day_title)
            .setMessage(activity.getString(R.string.history_delete_day_message, item.displayTitle))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteHistory(item)
            }
            .show()
    }

    private fun deleteHistory(item: HistoryDayItem) {
        val updated = historyItems.toMutableList()
        val index = updated.indexOfFirst { it.dayStartMillis == item.dayStartMillis }
        if (index == -1) return

        updated.removeAt(index)
        historyItems = updated
        if (selectedDayStartMillis == item.dayStartMillis) {
            selectedDayStartMillis = historyItems.firstOrNull()?.dayStartMillis
        }
        HistoryStorage.deleteMany(activity, item.sourceIds)
        updateContent()
        Toast.makeText(activity, R.string.history_saved_day_deleted, Toast.LENGTH_SHORT).show()
    }

    private fun formatHistoryTotalDuration(totalSeconds: Int): String {
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

    private fun updateTransferActionState(hasItems: Boolean) {
        tvHistoryExport.isEnabled = hasItems && !transferBusy
        tvHistoryImport.isEnabled = !transferBusy
        tvHistoryExport.alpha = if (tvHistoryExport.isEnabled) 1f else 0.45f
        tvHistoryImport.alpha = if (tvHistoryImport.isEnabled) 1f else 0.45f
    }
}
