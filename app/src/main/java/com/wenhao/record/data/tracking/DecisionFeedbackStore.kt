package com.wenhao.record.data.tracking

import android.content.Context
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.decision.DecisionFeedbackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object DecisionFeedbackStore {

    fun save(
        context: Context,
        eventId: Long,
        type: DecisionFeedbackType,
        createdAt: Long = System.currentTimeMillis(),
    ): Long {
        val feedbackId = runBlockingIo {
            TrackDatabase.getInstance(context.applicationContext)
                .decisionDao()
                .insertFeedback(
                    DecisionFeedbackEntity(
                        eventId = eventId,
                        feedbackType = type.name,
                        createdAt = createdAt,
                    )
                )
        }
        TrackDataChangeNotifier.notifyHistoryChanged()
        return feedbackId
    }

    fun markStartTooEarly(context: Context, eventId: Long): Long {
        return save(context, eventId, DecisionFeedbackType.START_TOO_EARLY)
    }

    private fun <T> runBlockingIo(block: () -> T): T {
        return runBlocking {
            withContext(Dispatchers.IO) {
                block()
            }
        }
    }
}
