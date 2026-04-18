package com.wenhao.record.data.local.decision

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DecisionDao {

    @Insert
    fun insertEvent(entity: DecisionEventEntity): Long

    @Insert
    fun insertFeedback(entity: DecisionFeedbackEntity): Long

    @Query("SELECT * FROM decision_event ORDER BY eventId DESC")
    fun getEvents(): List<DecisionEventEntity>

    @Query("SELECT * FROM decision_feedback ORDER BY feedbackId ASC")
    fun getFeedback(): List<DecisionFeedbackEntity>

    @Query(
        """
        SELECT * FROM decision_event
        WHERE timestampMillis BETWEEN :startMillis AND :endMillis
        ORDER BY eventId DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getEventsBetweenPaged(
        startMillis: Long,
        endMillis: Long,
        limit: Int,
        offset: Int,
    ): List<DecisionEventEntity>

    @Query(
        """
        SELECT * FROM decision_feedback
        WHERE eventId IN (:eventIds)
        ORDER BY feedbackId ASC
        """
    )
    fun getFeedbackForEventIds(eventIds: List<Long>): List<DecisionFeedbackEntity>

    @Query("DELETE FROM decision_feedback WHERE eventId IN (:eventIds)")
    fun deleteFeedbackForEventIds(eventIds: List<Long>): Int

    @Query("DELETE FROM decision_event WHERE eventId IN (:eventIds)")
    fun deleteEventsByIds(eventIds: List<Long>): Int

    @Query(
        """
        SELECT
            e.eventId AS eventId,
            e.timestampMillis AS timestampMillis,
            e.phase AS phase,
            e.isRecording AS isRecording,
            e.startScore AS startScore,
            e.stopScore AS stopScore,
            e.finalDecision AS finalDecision,
            e.feedbackEligible AS feedbackEligible,
            e.feedbackBlockedReason AS feedbackBlockedReason,
            (
                SELECT df.feedbackType
                FROM decision_feedback df
                WHERE df.eventId = e.eventId
                ORDER BY df.feedbackId DESC
                LIMIT 1
            ) AS feedbackLabel
        FROM decision_event e
        WHERE e.finalDecision IN ('START', 'STOP')
          AND e.feedbackEligible = 1
        ORDER BY e.timestampMillis DESC
        LIMIT :limit
        """
    )
    fun getRecentDecisionEvents(limit: Int): List<DecisionEventWithFeedbackRow>
}
