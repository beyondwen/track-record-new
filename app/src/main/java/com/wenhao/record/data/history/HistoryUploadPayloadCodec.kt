package com.wenhao.record.data.history

import org.json.JSONArray
import org.json.JSONObject

object HistoryUploadPayloadCodec {

    fun encode(deviceId: String, appVersion: String, rows: List<HistoryUploadRow>): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("appVersion", appVersion)
            put("histories", JSONArray().apply {
                rows.forEach { row ->
                    put(encodeRow(row))
                }
            })
        }.toString()
    }

    private fun encodeRow(row: HistoryUploadRow): JSONObject {
        return JSONObject().apply {
            put("historyId", row.historyId)
            put("timestampMillis", row.timestampMillis)
            put("distanceKm", safeJsonDouble(row.distanceKm))
            put("durationSeconds", row.durationSeconds)
            put("averageSpeedKmh", safeJsonDouble(row.averageSpeedKmh))
            put("title", row.title ?: JSONObject.NULL)
            put("startSource", row.startSource ?: JSONObject.NULL)
            put("stopSource", row.stopSource ?: JSONObject.NULL)
            put("manualStartAt", row.manualStartAt ?: JSONObject.NULL)
            put("manualStopAt", row.manualStopAt ?: JSONObject.NULL)
            put("points", JSONArray().apply {
                row.points.forEach { point ->
                    put(encodePoint(point))
                }
            })
        }
    }

    private fun encodePoint(point: HistoryUploadPointRow): JSONObject {
        return JSONObject().apply {
            put("latitude", safeJsonDouble(point.latitude))
            put("longitude", safeJsonDouble(point.longitude))
            put("timestampMillis", point.timestampMillis)
            put("accuracyMeters", point.accuracyMeters?.let(::safeJsonDouble) ?: JSONObject.NULL)
            put("altitudeMeters", point.altitudeMeters?.let(::safeJsonDouble) ?: JSONObject.NULL)
            put("wgs84Latitude", point.wgs84Latitude?.let(::safeJsonDouble) ?: JSONObject.NULL)
            put("wgs84Longitude", point.wgs84Longitude?.let(::safeJsonDouble) ?: JSONObject.NULL)
        }
    }

    private fun safeJsonDouble(value: Double): Any {
        return if (value.isFinite()) value else JSONObject.NULL
    }
}
