package com.wenhao.record.data.local.decision

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decision_event")
data class DecisionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val eventId: Long = 0L,
    val timestampMillis: Long,
    val phase: String,
    val isRecording: Boolean,
    val startScore: Double,
    val stopScore: Double,
    val finalDecision: String,
    val featureJson: String,
)

@Entity(tableName = "decision_feedback")
data class DecisionFeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val feedbackId: Long = 0L,
    val eventId: Long,
    val feedbackType: String,
    val createdAt: Long,
)

data class DecisionEventWithFeedbackRow(
    val eventId: Long,
    val timestampMillis: Long,
    val phase: String,
    val isRecording: Boolean,
    val startScore: Double,
    val stopScore: Double,
    val finalDecision: String,
    val feedbackLabel: String?,
)
