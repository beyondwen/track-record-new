package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrackPoint
import org.json.JSONArray
import org.json.JSONObject

object HistorySnapshotCodec {

    fun encode(items: List<HistoryItem>): String {
        return JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("timestamp", item.timestamp)
                        put("distanceKm", item.distanceKm)
                        put("durationSeconds", item.durationSeconds)
                        put("averageSpeedKmh", item.averageSpeedKmh)
                        put("title", item.title)
                        put(
                            "points",
                            JSONArray().apply {
                                item.points.forEach { point ->
                                    put(
                                        JSONObject().apply {
                                            put("latitude", point.latitude)
                                            put("longitude", point.longitude)
                                            put("timestampMillis", point.timestampMillis)
                                            put("accuracyMeters", point.accuracyMeters)
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            }
        }.toString()
    }

    fun decode(raw: String): List<HistoryItem> {
        return runCatching {
            val jsonArray = JSONArray(raw)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(index)
                    val pointsArray = obj.optJSONArray("points") ?: JSONArray()
                    val points = buildList {
                        for (pointIndex in 0 until pointsArray.length()) {
                            val point = pointsArray.getJSONObject(pointIndex)
                            add(
                                TrackPoint(
                                    latitude = point.getDouble("latitude"),
                                    longitude = point.getDouble("longitude"),
                                    timestampMillis = point.optLong("timestampMillis"),
                                    accuracyMeters = point.optDouble("accuracyMeters")
                                        .takeUnless { point.isNull("accuracyMeters") }
                                        ?.toFloat()
                                )
                            )
                        }
                    }
                    add(
                        HistoryItem(
                            id = obj.getLong("id"),
                            timestamp = obj.getLong("timestamp"),
                            distanceKm = obj.getDouble("distanceKm"),
                            durationSeconds = obj.getInt("durationSeconds"),
                            averageSpeedKmh = obj.getDouble("averageSpeedKmh"),
                            title = obj.optString("title").takeIf { it.isNotBlank() },
                            points = points
                        )
                    )
                }
            }
        }.getOrDefault(emptyList()).sortedWith(
            compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id }
        )
    }
}
