package com.wenhao.record.data.tracking

import org.json.JSONObject

object TrainingSampleExportCodec {

    fun encodeJsonLines(rows: List<TrainingSampleRow>): String {
        return buildString {
            rows.forEach { row ->
                appendLine(encodeRow(row).toString())
            }
        }
    }

    private fun encodeRow(row: TrainingSampleRow): JSONObject {
        return JSONObject().apply {
            put("eventId", row.eventId)
            put("timestampMillis", row.timestampMillis)
            put("phase", row.phase)
            put("isRecording", row.isRecording)
            put("startScore", row.startScore)
            put("stopScore", row.stopScore)
            put("finalDecision", row.finalDecision)
            put("features", JSONObject(row.features))
            put("feedbackLabel", row.feedbackLabel)
        }
    }
}
