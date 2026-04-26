package com.wenhao.record.data.tracking

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class TodaySessionSyncCoordinator(
    private val prefs: SharedPreferences,
) {
    fun shouldSync(nowMillis: Long, pendingPointCount: Int, force: Boolean): Boolean {
        if (force) return true
        val lastSyncedAt = prefs.getLong(KEY_LAST_TODAY_SESSION_SYNC_AT, 0L)
        return pendingPointCount >= POINT_THRESHOLD || nowMillis - lastSyncedAt >= MIN_SYNC_INTERVAL_MILLIS
    }

    fun markTriggered(nowMillis: Long) {
        prefs.edit {
            putLong(KEY_LAST_TODAY_SESSION_SYNC_AT, nowMillis)
        }
    }

    companion object {
        internal const val PREFS_NAME = "today_session_sync"
        internal const val KEY_LAST_TODAY_SESSION_SYNC_AT = "last_today_session_sync_at"
        private const val POINT_THRESHOLD = 20
        private const val MIN_SYNC_INTERVAL_MILLIS = 30_000L

        fun create(context: Context): TodaySessionSyncCoordinator {
            return TodaySessionSyncCoordinator(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
        }
    }
}
