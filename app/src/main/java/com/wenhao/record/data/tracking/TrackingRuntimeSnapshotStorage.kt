package com.wenhao.record.data.tracking

import android.content.Context
import com.wenhao.record.tracking.TrackingPhase

data class TrackingRuntimeSnapshot(
    val isEnabled: Boolean,
    val phase: TrackingPhase,
    val samplingTier: SamplingTier,
    val latestPoint: TrackPoint?,
    val lastAnalysisAt: Long?,
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
        prefs(context).edit().putBoolean(KEY_ENABLED, snapshot.isEnabled).apply()
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    fun peek(context: Context): TrackingRuntimeSnapshot {
        warmUp(context)
        return cached ?: TrackingRuntimeSnapshot(
            isEnabled = false,
            phase = TrackingPhase.IDLE,
            samplingTier = SamplingTier.IDLE,
            latestPoint = null,
            lastAnalysisAt = null,
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val current = peek(context)
        save(context, current.copy(isEnabled = enabled))
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
