package com.example.helloworld

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HistoryStorage {
    private const val PREFS_NAME = "track_record_history"
    private const val KEY_ITEMS = "items"

    fun load(context: Context): MutableList<HistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ITEMS, null) ?: return mutableListOf()
        val jsonArray = JSONArray(raw)
        val items = mutableListOf<HistoryItem>()
        for (index in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(index)
            items += HistoryItem(
                id = obj.getLong("id"),
                timestamp = obj.getLong("timestamp"),
                distanceKm = obj.getDouble("distanceKm"),
                durationSeconds = obj.getInt("durationSeconds"),
                averageSpeedKmh = obj.getDouble("averageSpeedKmh"),
                title = obj.optString("title").takeIf { it.isNotBlank() }
            )
        }
        return items
    }

    fun save(context: Context, items: List<HistoryItem>) {
        val jsonArray = JSONArray()
        items.forEach { item ->
            jsonArray.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("distanceKm", item.distanceKm)
                    put("durationSeconds", item.durationSeconds)
                    put("averageSpeedKmh", item.averageSpeedKmh)
                    put("title", item.title ?: "")
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, jsonArray.toString())
            .apply()
    }
}
