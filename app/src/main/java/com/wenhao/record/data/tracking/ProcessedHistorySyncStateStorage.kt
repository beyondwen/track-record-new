package com.wenhao.record.data.tracking

import android.content.Context
import androidx.core.content.edit

class ProcessedHistorySyncStateStorage(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(dayStartMillis: Long): Long {
        return prefs.getLong(keyFor(dayStartMillis), 0L)
    }

    fun markSynced(dayStartMillis: Long, maxPointId: Long) {
        prefs.edit {
            putLong(keyFor(dayStartMillis), maxPointId)
        }
    }

    private fun keyFor(dayStartMillis: Long): String = "day_$dayStartMillis"

    companion object {
        private const val PREFS_NAME = "processed_history_sync_state"
    }
}
