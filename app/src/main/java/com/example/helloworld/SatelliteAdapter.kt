package com.example.helloworld

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SatelliteAdapter(private var satellites: List<SatelliteInfo>) :
    RecyclerView.Adapter<SatelliteAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tvType)
        val tvSvid: TextView = view.findViewById(R.id.tvSvid)
        val tvSignal: TextView = view.findViewById(R.id.tvSignal)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_satellite, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val satellite = satellites[position]
        
        holder.tvType.text = satellite.constellationName
        holder.tvSvid.text = satellite.svid.toString()
        holder.tvSignal.text = String.format("%.1f", satellite.cn0DbHz)
        
        if (satellite.usedInFix) {
            holder.tvStatus.text = "使用中"
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")) // 绿色
        } else {
            holder.tvStatus.text = "可见"
            holder.tvStatus.setTextColor(Color.parseColor("#9E9E9E")) // 灰色
        }
        
        // 根据信号强度设置颜色
        when {
            satellite.cn0DbHz > 30 -> holder.tvSignal.setTextColor(Color.parseColor("#4CAF50")) // 强信号，绿色
            satellite.cn0DbHz > 20 -> holder.tvSignal.setTextColor(Color.parseColor("#FF9800")) // 中等信号，橙色
            else -> holder.tvSignal.setTextColor(Color.parseColor("#F44336")) // 弱信号，红色
        }
    }

    override fun getItemCount() = satellites.size

    fun updateData(newSatellites: List<SatelliteInfo>) {
        satellites = newSatellites
        notifyDataSetChanged()
    }
}