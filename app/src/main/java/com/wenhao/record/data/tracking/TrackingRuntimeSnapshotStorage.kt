package com.wenhao.record.data.tracking

import android.content.Context
import androidx.core.content.edit
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.tracking.TrackingPhase
import kotlinx.coroutines.runBlocking

data class TrackingRuntimeSnapshot(
    val isEnabled: Boolean,
    val phase: TrackingPhase,
    val samplingTier: SamplingTier,
    val latestPoint: TrackPoint?,
    val lastAnalysisAt: Long?,
    val sessionId: String? = null,
    val dayStartMillis: Long? = null,
)

object TrackingRuntimeSnapshotStorage {
    private const val PREFS_NAME = "tracking_runtime_snapshot"
    private const val KEY_ENABLED = "enabled"

    @Volatile
    private var cached: TrackingRuntimeSnapshot? = null

    fun warmUp(context: Context) {
        val prefs = prefs(context)
        cached = cached ?: TrackingRuntimeSnapshot(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            phase = TrackingPhase.IDLE,
            samplingTier = SamplingTier.IDLE,
            latestPoint = null,
            lastAnalysisAt = null,
        )
    }

    fun save(context: Context, snapshot: TrackingRuntimeSnapshot) {
        cached = snapshot
        prefs(context).edit { putBoolean(KEY_ENABLED, snapshot.isEnabled) }
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    fun peek(context: Context): TrackingRuntimeSnapshot {
        warmUp(context)
        val enabled = prefs(context).getBoolean(KEY_ENABLED, false)
        val openSession = runBlocking {
            TodaySessionStorage(
                TrackDatabase.getInstance(context.applicationContext).todaySessionDao(),
            ).loadOpenSession(System.currentTimeMillis())
        }
        if (openSession == null) {
            return cached ?: TrackingRuntimeSnapshot(
                isEnabled = enabled,
                phase = TrackingPhase.IDLE,
                samplingTier = SamplingTier.IDLE,
                latestPoint = null,
                lastAnalysisAt = null,
            )
        }

        val latestPoint = runBlocking {
            TodaySessionStorage(
                TrackDatabase.getInstance(context.applicationContext).todaySessionDao(),
            ).latestPoint(System.currentTimeMillis())
        }
        return TrackingRuntimeSnapshot(
            isEnabled = enabled,
            phase = runCatching { TrackingPhase.valueOf(openSession.phase ?: TrackingPhase.IDLE.name) }
                .getOrDefault(TrackingPhase.IDLE),
            samplingTier = SamplingTier.ACTIVE,
            latestPoint = latestPoint,
            lastAnalysisAt = null,
            sessionId = openSession.sessionId,
            dayStartMillis = openSession.dayStartMillis,
        ).also { cached = it }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val current = peek(context)
        save(context, current.copy(isEnabled = enabled))
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
