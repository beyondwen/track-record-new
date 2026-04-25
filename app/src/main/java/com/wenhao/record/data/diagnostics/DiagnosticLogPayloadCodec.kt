package com.wenhao.record.data.diagnostics

import org.json.JSONArray
import org.json.JSONObject

object DiagnosticLogPayloadCodec {
    private val PRECISE_LOCATION_KEYS = setOf(
        "latitude",
        "longitude",
        "lat",
        "lng",
        "wgs84Latitude",
        "wgs84Longitude",
    )

    fun encode(deviceId: String, appVersion: String, logs: List<DiagnosticLogEntry>): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("appVersion", appVersion)
            put("logs", JSONArray().apply {
                logs.forEach { log -> put(encodeLog(log)) }
            })
        }.toString()
    }

    private fun encodeLog(log: DiagnosticLogEntry): JSONObject {
        return JSONObject().apply {
            put("logId", log.logId)
            put("occurredAt", log.occurredAt)
            put("type", log.type.name)
            put("severity", log.severity.name)
            put("source", log.source.take(MAX_SOURCE_LENGTH))
            put("message", log.message.take(MAX_MESSAGE_LENGTH))
            put("fingerprint", log.fingerprint.take(MAX_FINGERPRINT_LENGTH))
            put("payload", sanitizedPayload(log.payloadJson))
        }
    }

    private fun sanitizedPayload(payloadJson: String?): Any {
        if (payloadJson.isNullOrBlank()) return JSONObject.NULL
        val payload = try {
            JSONObject(payloadJson)
        } catch (_: Exception) {
            return JSONObject.NULL
        }
        PRECISE_LOCATION_KEYS.forEach(payload::remove)
        return payload
    }

    private const val MAX_SOURCE_LENGTH = 80
    private const val MAX_MESSAGE_LENGTH = 300
    private const val MAX_FINGERPRINT_LENGTH = 120
}
