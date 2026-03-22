package com.wenhao.record.data.history

import android.content.Context
import com.wenhao.record.data.tracking.TrackPoint
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
            val pointsArray = obj.optJSONArray("points") ?: JSONArray()
            val points = mutableListOf<TrackPoint>()
            for (pointIndex in 0 until pointsArray.length()) {
                val point = pointsArray.getJSONObject(pointIndex)
                points += TrackPoint(
                    latitude = point.getDouble("latitude"),
                    longitude = point.getDouble("longitude"),
                    timestampMillis = point.optLong("timestampMillis"),
                    accuracyMeters = point.optDouble("accuracyMeters")
                        .takeUnless { point.isNull("accuracyMeters") }
                        ?.toFloat()
                )
            }
            items += HistoryItem(
                id = obj.getLong("id"),
                timestamp = obj.getLong("timestamp"),
                distanceKm = obj.getDouble("distanceKm"),
                durationSeconds = obj.getInt("durationSeconds"),
                averageSpeedKmh = obj.getDouble("averageSpeedKmh"),
                title = obj.optString("title").takeIf { it.isNotBlank() },
                points = points
            )
        }
        return items
    }

    fun save(context: Context, items: List<HistoryItem>) {
        val jsonArray = JSONArray()
        items.forEach { item ->
            val pointsArray = JSONArray()
            item.points.forEach { point ->
                pointsArray.put(
                    JSONObject().apply {
                        put("latitude", point.latitude)
                        put("longitude", point.longitude)
                        put("timestampMillis", point.timestampMillis)
                        put("accuracyMeters", point.accuracyMeters)
                    }
                )
            }

            jsonArray.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("distanceKm", item.distanceKm)
                    put("durationSeconds", item.durationSeconds)
                    put("averageSpeedKmh", item.averageSpeedKmh)
                    put("title", item.title ?: "")
                    put("points", pointsArray)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, jsonArray.toString())
            .apply()
    }
}
