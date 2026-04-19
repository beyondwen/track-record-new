package com.wenhao.record.data.tracking

import org.json.JSONArray
import org.json.JSONObject

object RawPointUploadPayloadCodec {

    fun encode(deviceId: String, appVersion: String, rows: List<RawPointUploadRow>): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("appVersion", appVersion)
            put("points", JSONArray().apply {
                rows.forEach { row ->
                    put(encodeRow(row))
                }
            })
        }.toString()
    }

    private fun encodeRow(row: RawPointUploadRow): JSONObject {
        return JSONObject().apply {
            put("pointId", row.pointId)
            put("timestampMillis", row.timestampMillis)
            put("latitude", safeJsonDouble(row.latitude))
            put("longitude", safeJsonDouble(row.longitude))
            put("accuracyMeters", row.accuracyMeters?.let(::safeJsonDouble) ?: JSONObject.NULL)
            put("altitudeMeters", row.altitudeMeters?.let(::safeJsonDouble) ?: JSONObject.NULL)
            put("speedMetersPerSecond", row.speedMetersPerSecond?.let(::safeJsonDouble) ?: JSONObject.NULL)
            put("bearingDegrees", row.bearingDegrees?.let(::safeJsonDouble) ?: JSONObject.NULL)
            put("provider", row.provider)
            put("sourceType", row.sourceType)
            put("isMock", row.isMock)
            put("wifiFingerprintDigest", row.wifiFingerprintDigest ?: JSONObject.NULL)
            put("activityType", row.activityType ?: JSONObject.NULL)
            put("activityConfidence", row.activityConfidence?.let(::safeJsonDouble) ?: JSONObject.NULL)
            put("samplingTier", row.samplingTier)
        }
    }

    private fun safeJsonDouble(value: Double): Any {
        return if (value.isFinite()) value else JSONObject.NULL
    }
}
