package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.TrackDatabase
import java.util.Calendar

data class SyncDiagnosticsSnapshot(
    val rawPointCount: Int,
    val todayDisplayPointCount: Int,
    val outboxPendingCount: Int,
    val outboxInProgressCount: Int,
    val outboxFailedCount: Int,
    val lastError: String?,
)

class SyncDiagnosticsRepository(
    private val database: TrackDatabase,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend fun load(): SyncDiagnosticsSnapshot {
        val continuousDao = database.continuousTrackDao()
        val todayDao = database.todayTrackDisplayDao()
        val outboxDao = database.syncOutboxDao()
        val todayStart = dayStartMillis(nowProvider())
        return SyncDiagnosticsSnapshot(
            rawPointCount = continuousDao.countRawPoints(),
            todayDisplayPointCount = todayDao.countPointsForDay(todayStart),
            outboxPendingCount = outboxDao.countByStatus(SyncOutboxStatus.PENDING.name),
            outboxInProgressCount = outboxDao.countByStatus(SyncOutboxStatus.IN_PROGRESS.name),
            outboxFailedCount = outboxDao.countByStatus(SyncOutboxStatus.FAILED.name),
            lastError = outboxDao.loadLatestError(SyncOutboxStatus.FAILED.name),
        )
    }

    private fun dayStartMillis(timestampMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
