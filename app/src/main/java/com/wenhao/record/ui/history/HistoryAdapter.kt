package com.wenhao.record.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryItem
import com.google.android.material.card.MaterialCardView
import java.util.Calendar

class HistoryAdapter(
    private val historyList: List<HistoryItem>,
    private val onHistoryClick: (HistoryItem, Int) -> Unit,
    private val onHistoryLongClick: (HistoryItem, Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var selectedHistoryId: Long? = null

    fun setSelectedHistoryId(id: Long?) {
        selectedHistoryId = id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = historyList[position]
        val isSelected = item.id == selectedHistoryId
        val context = holder.itemView.context
        val groupLabel = buildGroupLabel(context, item.timestamp)
        val previousGroupLabel = historyList.getOrNull(position - 1)?.let {
            buildGroupLabel(context, it.timestamp)
        }
        val showGroupHeader = position == 0 || groupLabel != previousGroupLabel

        holder.tvGroupHeader.visibility = if (showGroupHeader) View.VISIBLE else View.GONE
        holder.tvGroupHeader.text = groupLabel
        holder.tvHistoryLabel.text = context.getString(
            if (position == 0) R.string.history_card_latest_label else R.string.history_card_saved_label
        )
        holder.tvHistoryTitle.text = item.displayTitle
        holder.tvHistorySummary.text = context.getString(
            if (isSelected) R.string.history_summary_viewing else R.string.history_summary_default
        )
        holder.tvHistoryTime.text = item.formattedDateTitle
        holder.tvHistoryDistance.text = item.formattedDistance
        holder.tvHistoryDuration.text = item.formattedDuration
        holder.tvHistorySpeed.text = item.formattedSpeed
        holder.tvHistoryAction.text = context.getString(
            if (isSelected) R.string.history_action_viewing else R.string.history_action_view
        )
        holder.routePreview.setPoints(item.points)
        holder.card.strokeWidth = if (isSelected) 2 else 1
        holder.card.strokeColor = if (isSelected) 0x668B5CF6 else 0x164A6267
        holder.card.setCardBackgroundColor(
            ContextCompat.getColor(
                context,
                if (isSelected) R.color.md_theme_light_primaryContainer else android.R.color.transparent
            )
        )
        holder.card.alpha = if (isSelected) 1f else 0.97f

        holder.itemView.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onHistoryClick(historyList[adapterPosition], adapterPosition)
            }
        }
        holder.itemView.setOnLongClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onHistoryLongClick(historyList[adapterPosition], adapterPosition)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int = historyList.size

    private fun buildGroupLabel(context: android.content.Context, timestamp: Long): String {
        val now = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }

        return when {
            isSameDay(target, now) -> context.getString(R.string.history_group_today)
            isSameDay(target, yesterday) -> context.getString(R.string.history_group_yesterday)
            target.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> {
                context.getString(R.string.history_group_month, target.get(Calendar.MONTH) + 1)
            }

            else -> {
                context.getString(
                    R.string.history_group_year_month,
                    target.get(Calendar.YEAR),
                    target.get(Calendar.MONTH) + 1
                )
            }
        }
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val tvGroupHeader: TextView = itemView.findViewById(R.id.tvGroupHeader)
        val tvHistoryLabel: TextView = itemView.findViewById(R.id.tvHistoryLabel)
        val tvHistoryTitle: TextView = itemView.findViewById(R.id.tvHistoryTitle)
        val tvHistorySummary: TextView = itemView.findViewById(R.id.tvHistorySummary)
        val tvHistoryTime: TextView = itemView.findViewById(R.id.tvHistoryTime)
        val tvHistoryDistance: TextView = itemView.findViewById(R.id.tvHistoryDistance)
        val tvHistoryDuration: TextView = itemView.findViewById(R.id.tvHistoryDuration)
        val tvHistorySpeed: TextView = itemView.findViewById(R.id.tvHistorySpeed)
        val tvHistoryAction: TextView = itemView.findViewById(R.id.tvHistoryAction)
        val routePreview: HistoryRoutePreviewView = itemView.findViewById(R.id.historyRoutePreview)
    }
}
