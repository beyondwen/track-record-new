package com.wenhao.record.data.tracking

import org.json.JSONArray
import org.json.JSONObject

object AnalysisUploadPayloadCodec {

    fun encode(deviceId: String, appVersion: String, rows: List<AnalysisUploadRow>): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("appVersion", appVersion)
            put("segments", JSONArray().apply {
                rows.forEach { row ->
                    put(encodeRow(row))
                }
            })
        }.toString()
    }

    private fun encodeRow(row: AnalysisUploadRow): JSONObject {
        return JSONObject().apply {
            put("segmentId", row.segmentId)
            put("startPointId", row.startPointId)
            put("endPointId", row.endPointId)
            put("startTimestamp", row.startTimestamp)
            put("endTimestamp", row.endTimestamp)
            put("segmentType", row.segmentType)
            put("confidence", safeJsonDouble(row.confidence))
            put("distanceMeters", safeJsonDouble(row.distanceMeters))
            put("durationMillis", row.durationMillis)
            put("avgSpeedMetersPerSecond", safeJsonDouble(row.avgSpeedMetersPerSecond))
            put("maxSpeedMetersPerSecond", safeJsonDouble(row.maxSpeedMetersPerSecond))
            put("analysisVersion", row.analysisVersion)
            put("stayClusters", JSONArray().apply {
                row.stayClusters.forEach { stay ->
                    put(encodeStayCluster(stay))
                }
            })
        }
    }

    private fun encodeStayCluster(row: AnalysisStayClusterUploadRow): JSONObject {
        return JSONObject().apply {
            put("stayId", row.stayId)
            put("centerLat", safeJsonDouble(row.centerLat))
            put("centerLng", safeJsonDouble(row.centerLng))
            put("radiusMeters", safeJsonDouble(row.radiusMeters))
            put("arrivalTime", row.arrivalTime)
            put("departureTime", row.departureTime)
            put("confidence", safeJsonDouble(row.confidence))
            put("analysisVersion", row.analysisVersion)
        }
    }

    private fun safeJsonDouble(value: Double): Any {
        return if (value.isFinite()) value else JSONObject.NULL
    }
}
