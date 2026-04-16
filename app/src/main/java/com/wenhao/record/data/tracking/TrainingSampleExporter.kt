package com.wenhao.record.data.tracking

import android.content.Context
import com.wenhao.record.data.local.TrackDatabase
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class TrainingSampleRow(
    val eventId: Long,
    val timestampMillis: Long,
    val phase: String,
    val isRecording: Boolean,
    val startScore: Double,
    val stopScore: Double,
    val finalDecision: String,
    val gpsQualityPass: Boolean,
    val motionEvidencePass: Boolean,
    val frequentPlaceClearPass: Boolean,
    val feedbackEligible: Boolean,
    val feedbackBlockedReason: String?,
    val features: Map<String, Double>,
    val feedbackLabel: String?,
)

object TrainingSampleExporter {

    fun exportRows(context: Context): List<TrainingSampleRow> {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val dao = TrackDatabase.getInstance(context.applicationContext).decisionDao()
                val feedbackByEventId = dao.getFeedback()
                    .associateBy({ it.eventId }, { it.feedbackType })

                dao.getEvents().map { event ->
                    TrainingSampleRow(
                        eventId = event.eventId,
                        timestampMillis = event.timestampMillis,
                        phase = event.phase,
                        isRecording = event.isRecording,
                        startScore = event.startScore,
                        stopScore = event.stopScore,
                        finalDecision = event.finalDecision,
                        gpsQualityPass = event.gpsQualityPass,
                        motionEvidencePass = event.motionEvidencePass,
                        frequentPlaceClearPass = event.frequentPlaceClearPass,
                        feedbackEligible = event.feedbackEligible,
                        feedbackBlockedReason = event.feedbackBlockedReason,
                        features = parseFeatures(event.featureJson),
                        feedbackLabel = feedbackByEventId[event.eventId],
                    )
                }
            }
        }
    }

    private fun parseFeatures(featureJson: String): Map<String, Double> {
        val json = JSONObject(featureJson)
        return buildMap {
            json.keys().forEach { key ->
                put(key, json.optDouble(key))
            }
        }
    }
}
