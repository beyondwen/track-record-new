package com.wenhao.record.data.tracking

internal object AutoTrackSessionPersistPolicy {
    internal const val SESSION_PERSIST_INTERVAL_MS = 3_000L
    private const val MIN_PENDING_POINT_BATCH = 3

    fun shouldPersistImmediately(
        persistedSession: AutoTrackSession?,
        newSession: AutoTrackSession,
        lastPersistedAt: Long,
        nowMillis: Long
    ): Boolean {
        if (persistedSession == null) return true
        if (persistedSession.startTimestamp != newSession.startTimestamp) return true

        val persistedPointCount = persistedSession.points.size
        val newPointCount = newSession.points.size
        if (newPointCount <= 1) return true
        if (newPointCount < persistedPointCount) return true
        if (newPointCount - persistedPointCount >= MIN_PENDING_POINT_BATCH) return true

        return nowMillis - lastPersistedAt >= SESSION_PERSIST_INTERVAL_MS
    }

    fun nextFlushDelayMillis(lastPersistedAt: Long, nowMillis: Long): Long {
        return (SESSION_PERSIST_INTERVAL_MS - (nowMillis - lastPersistedAt))
            .coerceAtLeast(250L)
    }
}
