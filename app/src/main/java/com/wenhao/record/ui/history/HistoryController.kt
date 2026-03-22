package com.wenhao.record.ui.history

import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.HistoryStorage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class HistoryController(
    private val activity: AppCompatActivity,
    private val onHistoryOpen: (HistoryItem) -> Unit
) {
    private val historyScreen: View = activity.findViewById(R.id.historyScreen)
    private val rvHistoryPage: RecyclerView = activity.findViewById(R.id.rvHistoryPage)
    private val layoutHistoryEmptyPage: View = activity.findViewById(R.id.layoutHistoryEmptyPage)
    private val tvHistoryPageCount: TextView = activity.findViewById(R.id.tvHistoryPageCount)
    private val tvHistoryPageSubtitle: TextView = activity.findViewById(R.id.tvHistoryPageSubtitle)
    private val tvHistoryTotalDistance: TextView = activity.findViewById(R.id.tvHistoryTotalDistance)
    private val tvHistoryTotalDuration: TextView = activity.findViewById(R.id.tvHistoryTotalDuration)
    private val ivHistoryPageTabRecord: ImageView = activity.findViewById(R.id.ivHistoryPageTabRecord)
    private val ivHistoryPageTabHistory: ImageView = activity.findViewById(R.id.ivHistoryPageTabHistory)
    private val tvHistoryPageTabRecord: TextView = activity.findViewById(R.id.tvHistoryPageTabRecord)
    private val tvHistoryPageTabHistory: TextView = activity.findViewById(R.id.tvHistoryPageTabHistory)
    private val historyPageTabRecord: View = activity.findViewById(R.id.historyPageTabRecord)

    private val historyList = mutableListOf<HistoryItem>()
    private var selectedHistoryId: Long? = null

    private val historyAdapter = HistoryAdapter(
        historyList = historyList,
        onHistoryClick = { item, _ -> handleHistoryClick(item) },
        onHistoryLongClick = { item, _ -> showHistoryActions(item) }
    )

    init {
        rvHistoryPage.layoutManager = LinearLayoutManager(activity)
        rvHistoryPage.adapter = historyAdapter
    }

    fun bindNavigation(onRecordClick: () -> Unit) {
        historyPageTabRecord.setOnClickListener { onRecordClick() }
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

    fun reload() {
        historyList.clear()
        historyList.addAll(HistoryStorage.load(activity))
    }

    fun updateContent() {
        if (selectedHistoryId != null && historyList.none { it.id == selectedHistoryId }) {
            selectedHistoryId = historyList.firstOrNull()?.id
        }
        historyAdapter.setSelectedHistoryId(selectedHistoryId)
        historyAdapter.notifyDataSetChanged()

        val count = historyList.size
        val hasItems = count > 0
        val latest = historyList.firstOrNull()
        val totalDistance = historyList.sumOf { it.distanceKm }
        val totalDurationSeconds = historyList.sumOf { it.durationSeconds }

        tvHistoryPageCount.text = "${count} \u6761\u8bb0\u5f55"
        tvHistoryTotalDistance.text = String.format(Locale.getDefault(), "\u7d2f\u8ba1 %.1f \u516c\u91cc", totalDistance)
        tvHistoryTotalDuration.text = "\u7d2f\u8ba1 ${formatHistoryTotalDuration(totalDurationSeconds)}"
        layoutHistoryEmptyPage.visibility = if (hasItems) View.GONE else View.VISIBLE
        rvHistoryPage.visibility = if (hasItems) View.VISIBLE else View.GONE
        tvHistoryPageSubtitle.text = latest?.let {
            activity.getString(R.string.dashboard_history_latest, it.formattedDateTitle)
        } ?: "\u67e5\u770b\u5df2\u7ecf\u4fdd\u5b58\u7684\u8f68\u8ff9"
    }

    private fun handleHistoryClick(item: HistoryItem) {
        if (item.points.isEmpty()) {
            Toast.makeText(activity, R.string.dashboard_history_no_route, Toast.LENGTH_SHORT).show()
            return
        }

        selectedHistoryId = item.id
        historyAdapter.setSelectedHistoryId(item.id)
        historyAdapter.notifyDataSetChanged()
        onHistoryOpen(item)
    }

    private fun showHistoryActions(item: HistoryItem) {
        val options = arrayOf(
            activity.getString(R.string.history_option_rename),
            activity.getString(R.string.history_option_delete)
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.history_list_options_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameHistoryDialog(item)
                    1 -> confirmDeleteHistory(item)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showRenameHistoryDialog(item: HistoryItem) {
        val input = EditText(activity).apply {
            setText(item.title ?: item.displayTitle)
            setSelection(text.length)
            hint = activity.getString(R.string.history_rename_hint)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.history_rename_title)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                renameHistory(item, input.text?.toString().orEmpty().trim())
            }
            .show()
    }

    private fun confirmDeleteHistory(item: HistoryItem) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.history_delete_title)
            .setMessage(activity.getString(R.string.history_delete_message, item.displayTitle))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteHistory(item)
            }
            .show()
    }

    private fun renameHistory(item: HistoryItem, newTitle: String) {
        val index = historyList.indexOfFirst { it.id == item.id }
        if (index == -1) return

        historyList[index] = historyList[index].copy(title = newTitle.ifBlank { null })
        saveHistoryData()
        updateContent()
        Toast.makeText(activity, R.string.history_saved_name_updated, Toast.LENGTH_SHORT).show()
    }

    private fun deleteHistory(item: HistoryItem) {
        val index = historyList.indexOfFirst { it.id == item.id }
        if (index == -1) return

        historyList.removeAt(index)
        if (selectedHistoryId == item.id) {
            selectedHistoryId = historyList.firstOrNull()?.id
        }
        saveHistoryData()
        updateContent()
        Toast.makeText(activity, R.string.history_saved_deleted, Toast.LENGTH_SHORT).show()
    }

    private fun saveHistoryData() {
        HistoryStorage.save(activity, historyList)
    }

    private fun formatHistoryTotalDuration(totalSeconds: Int): String {
        val totalMinutes = totalSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}\u5c0f\u65f6${minutes}\u5206\u949f"
            hours > 0 -> "${hours}\u5c0f\u65f6"
            else -> "${totalMinutes}\u5206\u949f"
        }
    }
}
