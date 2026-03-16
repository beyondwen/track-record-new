package com.example.helloworld

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private val historyList: List<HistoryItem>,
    private val onHistoryClick: (HistoryItem, Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = historyList[position]
        holder.tvHistoryLabel.text = if (position == 0) "最近" else "历史"
        holder.tvHistoryTitle.text = item.displayTitle
        holder.tvHistorySummary.text = if (position == 0) {
            "最新完成的一条轨迹记录"
        } else {
            "已保存的轨迹回顾"
        }
        holder.tvHistoryTime.text = item.formattedDateTitle
        holder.tvHistoryDistance.text = item.formattedDistance
        holder.tvHistoryDuration.text = item.formattedDuration
        holder.tvHistorySpeed.text = item.formattedSpeed
        holder.itemView.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onHistoryClick(historyList[adapterPosition], adapterPosition)
            }
        }
    }

    override fun getItemCount(): Int = historyList.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvHistoryLabel: TextView = itemView.findViewById(R.id.tvHistoryLabel)
        val tvHistoryTitle: TextView = itemView.findViewById(R.id.tvHistoryTitle)
        val tvHistorySummary: TextView = itemView.findViewById(R.id.tvHistorySummary)
        val tvHistoryTime: TextView = itemView.findViewById(R.id.tvHistoryTime)
        val tvHistoryDistance: TextView = itemView.findViewById(R.id.tvHistoryDistance)
        val tvHistoryDuration: TextView = itemView.findViewById(R.id.tvHistoryDuration)
        val tvHistorySpeed: TextView = itemView.findViewById(R.id.tvHistorySpeed)
    }
}
