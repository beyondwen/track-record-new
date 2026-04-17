package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrackPoint
import org.json.JSONArray
import org.json.JSONObject

object HistorySnapshotCodec {

    fun encode(items: List<HistoryItem>): String {
        if (items.isEmpty()) return "[]"
        val builder = StringBuilder(items.size * 2048)
        builder.append('[')
        for (i in items.indices) {
            val item = items[i]
            builder.append("""{"id":""").append(item.id)
                .append(""","timestamp":""").append(item.timestamp)
                .append(""","distanceKm":""").append(item.distanceKm)
                .append(""","durationSeconds":""").append(item.durationSeconds)
                .append(""","averageSpeedKmh":""").append(item.averageSpeedKmh)
                .append(""","startSource":""").append(JSONObject.quote(item.startSource.name))
                .append(""","stopSource":""").append(JSONObject.quote(item.stopSource.name))

            if (!item.title.isNullOrEmpty()) {
                builder.append(""","title":""").append(JSONObject.quote(item.title))
            }
            if (item.manualStartAt != null) {
                builder.append(""","manualStartAt":""").append(item.manualStartAt)
            }
            if (item.manualStopAt != null) {
                builder.append(""","manualStopAt":""").append(item.manualStopAt)
            }

            builder.append(""","points":[""")
            val points = item.points
            for (p in points.indices) {
                val point = points[p]
                builder.append("""{"latitude":""").append(point.latitude)
                    .append(""","longitude":""").append(point.longitude)
                    .append(""","timestampMillis":""").append(point.timestampMillis)
                
                if (point.accuracyMeters != null) {
                    builder.append(""","accuracyMeters":""").append(point.accuracyMeters.toDouble())
                }
                if (point.altitudeMeters != null) {
                    builder.append(""","altitudeMeters":""").append(point.altitudeMeters)
                }
                if (point.wgs84Latitude != null) {
                    builder.append(""","wgs84Latitude":""").append(point.wgs84Latitude)
                }
                if (point.wgs84Longitude != null) {
                    builder.append(""","wgs84Longitude":""").append(point.wgs84Longitude)
                }
                builder.append('}')
                if (p < points.size - 1) builder.append(',')
            }
            builder.append("]}")
            if (i < items.size - 1) builder.append(',')
        }
        builder.append(']')
        return builder.toString()
    }

    fun decode(raw: String): List<HistoryItem> {
        return runCatching {
            val jsonArray = JSONArray(raw)
            val itemsCount = jsonArray.length()
            val items = ArrayList<HistoryItem>(itemsCount)
            for (index in 0 until itemsCount) {
                val obj = jsonArray.getJSONObject(index)
                val pointsArray = obj.optJSONArray("points")
                val pointsCount = pointsArray?.length() ?: 0
                val points = ArrayList<TrackPoint>(pointsCount)
                
                if (pointsArray != null) {
                    for (pointIndex in 0 until pointsCount) {
                        val point = pointsArray.getJSONObject(pointIndex)
                        val accuracyMeters = if (point.isNull("accuracyMeters")) null else point.optDouble("accuracyMeters").toFloat()
                        val altitudeMeters = if (point.isNull("altitudeMeters") || !point.has("altitudeMeters")) null else point.optDouble("altitudeMeters")
                        val wgs84Latitude = if (point.isNull("wgs84Latitude") || !point.has("wgs84Latitude")) null else point.optDouble("wgs84Latitude")
                        val wgs84Longitude = if (point.isNull("wgs84Longitude") || !point.has("wgs84Longitude")) null else point.optDouble("wgs84Longitude")

                        points.add(
                            TrackPoint(
                                latitude = point.getDouble("latitude"),
                                longitude = point.getDouble("longitude"),
                                timestampMillis = point.optLong("timestampMillis"),
                                accuracyMeters = accuracyMeters,
                                altitudeMeters = altitudeMeters,
                                wgs84Latitude = wgs84Latitude,
                                wgs84Longitude = wgs84Longitude
                            )
                        )
                    }
                }
                items.add(
                    HistoryItem(
                        id = obj.getLong("id"),
                        timestamp = obj.getLong("timestamp"),
                        distanceKm = obj.getDouble("distanceKm"),
                        durationSeconds = obj.getInt("durationSeconds"),
                        averageSpeedKmh = obj.getDouble("averageSpeedKmh"),
                        title = obj.optString("title").takeIf { it.isNotBlank() },
                        points = points,
                        startSource = TrackRecordSource.fromStorage(obj.optStringOrNull("startSource")),
                        stopSource = TrackRecordSource.fromStorage(obj.optStringOrNull("stopSource")),
                        manualStartAt = obj.optLongOrNull("manualStartAt"),
                        manualStopAt = obj.optLongOrNull("manualStopAt"),
                    )
                )
            }
            items.sortWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
            items
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        return if (has(key) && !isNull(key)) optLong(key) else null
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }
}
