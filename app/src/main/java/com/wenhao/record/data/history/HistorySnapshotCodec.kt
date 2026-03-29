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
                                            point.altitudeMeters?.let { put("altitudeMeters", it) }
                                            point.wgs84Latitude?.let { put("wgs84Latitude", it) }
                                            point.wgs84Longitude?.let { put("wgs84Longitude", it) }
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
                                        ?.toFloat(),
                                    altitudeMeters = point.optDouble("altitudeMeters")
                                        .takeUnless { point.isNull("altitudeMeters") || !point.has("altitudeMeters") },
                                    wgs84Latitude = point.optDouble("wgs84Latitude")
                                        .takeUnless { point.isNull("wgs84Latitude") || !point.has("wgs84Latitude") },
                                    wgs84Longitude = point.optDouble("wgs84Longitude")
                                        .takeUnless { point.isNull("wgs84Longitude") || !point.has("wgs84Longitude") }
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
