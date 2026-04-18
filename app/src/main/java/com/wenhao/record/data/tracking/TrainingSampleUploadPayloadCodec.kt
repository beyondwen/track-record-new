package com.wenhao.record.data.tracking

import org.json.JSONArray
import org.json.JSONObject

object TrainingSampleUploadPayloadCodec {

    fun encode(deviceId: String, appVersion: String, rows: List<TrainingSampleRow>): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("appVersion", appVersion)
            put("samples", JSONArray().apply {
                rows.forEach { row ->
                    put(encodeRow(row))
                }
            })
        }.toString()
    }

    private fun encodeRow(row: TrainingSampleRow): JSONObject {
        return JSONObject().apply {
            put("eventId", row.eventId)
            put("recordId", row.recordId ?: JSONObject.NULL)
            put("timestampMillis", row.timestampMillis)
            put("phase", row.phase)
            put("isRecording", row.isRecording)
            putFiniteDouble("startScore", row.startScore)
            putFiniteDouble("stopScore", row.stopScore)
            put("finalDecision", row.finalDecision)
            put("gpsQualityPass", row.gpsQualityPass)
            put("motionEvidencePass", row.motionEvidencePass)
            put("frequentPlaceClearPass", row.frequentPlaceClearPass)
            put("feedbackEligible", row.feedbackEligible)
            put("feedbackBlockedReason", row.feedbackBlockedReason ?: JSONObject.NULL)
            put("features", encodeFeatures(row.features))
            put("feedbackLabel", row.feedbackLabel ?: JSONObject.NULL)
            put("startSource", row.startSource ?: JSONObject.NULL)
            put("stopSource", row.stopSource ?: JSONObject.NULL)
            put("manualStartAt", row.manualStartAt ?: JSONObject.NULL)
            put("manualStopAt", row.manualStopAt ?: JSONObject.NULL)
        }
    }

    private fun encodeFeatures(features: Map<String, Double>): JSONObject {
        return JSONObject().apply {
            features.forEach { (key, value) ->
                put(key, safeJsonDouble(value))
            }
        }
    }

    private fun JSONObject.putFiniteDouble(name: String, value: Double): JSONObject {
        put(name, safeJsonDouble(value))
        return this
    }

    private fun safeJsonDouble(value: Double): Any {
        return if (value.isFinite()) value else JSONObject.NULL
    }
}
