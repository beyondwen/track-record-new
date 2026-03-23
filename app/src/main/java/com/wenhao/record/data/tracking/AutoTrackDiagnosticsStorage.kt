package com.wenhao.record.data.tracking

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.wenhao.record.tracking.TrackingTextSanitizer

data class AutoTrackDiagnostics(
    val serviceStatus: String = "\u540e\u53f0\u5f85\u547d\u4e2d",
    val lastEvent: String = "\u5e94\u7528\u5df2\u542f\u52a8\uff0c\u7b49\u5f85\u4f4e\u529f\u8017\u63a2\u6d4b",
    val lastEventAt: Long = 0L,
    val lastLocationDecision: String = "\u6682\u672a\u6536\u5230\u5b9a\u4f4d",
    val lastLocationAt: Long = 0L,
    val lastLocationAccuracyMeters: Float? = null,
    val acceptedPointCount: Int = 0,
    val lastSavedSummary: String? = null,
    val lastSavedAt: Long = 0L
)

object AutoTrackDiagnosticsStorage {
    private const val PREFS_NAME = "track_record_diagnostics"
    private const val LOCATION_WRITE_INTERVAL_MS = 4_000L
    private const val KEY_SERVICE_STATUS = "service_status"
    private const val KEY_LAST_EVENT = "last_event"
    private const val KEY_LAST_EVENT_AT = "last_event_at"
    private const val KEY_LAST_LOCATION_DECISION = "last_location_decision"
    private const val KEY_LAST_LOCATION_AT = "last_location_at"
    private const val KEY_LAST_LOCATION_ACCURACY = "last_location_accuracy"
    private const val KEY_ACCEPTED_POINT_COUNT = "accepted_point_count"
    private const val KEY_LAST_SAVED_SUMMARY = "last_saved_summary"
    private const val KEY_LAST_SAVED_AT = "last_saved_at"

    private val cacheLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val delayedFlushRunnable = Runnable {
        flushPendingSnapshot()
    }

    @Volatile
    private var diagnosticsCache: AutoTrackDiagnostics? = null

    @Volatile
    private var lastPersistedAt = 0L

    @Volatile
    private var pendingPersistPreferences: SharedPreferences? = null

    @Volatile
    private var pendingPersistDiagnostics: AutoTrackDiagnostics? = null

    fun load(context: Context): AutoTrackDiagnostics {
        diagnosticsCache?.let { return it }
        val prefs = prefs(context)
        val diagnostics = AutoTrackDiagnostics(
            serviceStatus = TrackingTextSanitizer.normalize(
                prefs.getString(KEY_SERVICE_STATUS, "\u540e\u53f0\u5f85\u547d\u4e2d").orEmpty()
            ),
            lastEvent = TrackingTextSanitizer.normalize(
                prefs.getString(
                KEY_LAST_EVENT,
                "\u5e94\u7528\u5df2\u542f\u52a8\uff0c\u7b49\u5f85\u4f4e\u529f\u8017\u63a2\u6d4b"
                ).orEmpty()
            ),
            lastEventAt = prefs.getLong(KEY_LAST_EVENT_AT, 0L),
            lastLocationDecision = TrackingTextSanitizer.normalize(
                prefs.getString(
                KEY_LAST_LOCATION_DECISION,
                "\u6682\u672a\u6536\u5230\u5b9a\u4f4d"
                ).orEmpty()
            ),
            lastLocationAt = prefs.getLong(KEY_LAST_LOCATION_AT, 0L),
            lastLocationAccuracyMeters = if (prefs.contains(KEY_LAST_LOCATION_ACCURACY)) {
                prefs.getFloat(KEY_LAST_LOCATION_ACCURACY, 0f)
            } else {
                null
            },
            acceptedPointCount = prefs.getInt(KEY_ACCEPTED_POINT_COUNT, 0),
            lastSavedSummary = TrackingTextSanitizer.normalize(prefs.getString(KEY_LAST_SAVED_SUMMARY, null)),
            lastSavedAt = prefs.getLong(KEY_LAST_SAVED_AT, 0L)
        )
        diagnosticsCache = diagnostics
        lastPersistedAt = maxOf(
            diagnostics.lastEventAt,
            diagnostics.lastLocationAt,
            diagnostics.lastSavedAt
        )
        return diagnostics
    }

    fun markServiceStatus(context: Context, status: String, event: String? = null) {
        update(context) { current ->
            current.copy(
                serviceStatus = status,
                lastEvent = event ?: current.lastEvent,
                lastEventAt = if (event != null) System.currentTimeMillis() else current.lastEventAt
            )
        }
    }

    fun markEvent(context: Context, event: String) {
        update(context) { current ->
            current.copy(
                lastEvent = event,
                lastEventAt = System.currentTimeMillis()
            )
        }
    }

    fun markLocationDecision(
        context: Context,
        decision: String,
        acceptedPointCount: Int,
        accuracyMeters: Float?
    ) {
        update(
            context = context,
            throttleWrites = true
        ) { current ->
            val now = System.currentTimeMillis()
            val sameAccuracy = current.lastLocationAccuracyMeters == accuracyMeters
            if (current.lastLocationDecision == decision &&
                current.acceptedPointCount == acceptedPointCount &&
                sameAccuracy &&
                now - current.lastLocationAt < LOCATION_WRITE_INTERVAL_MS
            ) {
                return@update current
            }
            current.copy(
                lastLocationDecision = decision,
                lastLocationAt = now,
                lastLocationAccuracyMeters = accuracyMeters,
                acceptedPointCount = acceptedPointCount
            )
        }
    }

    fun markSessionSaved(context: Context, summary: String) {
        update(context) { current ->
            current.copy(
                serviceStatus = "\u540e\u53f0\u5f85\u547d\u4e2d",
                lastEvent = "\u672c\u6b21\u884c\u7a0b\u5df2\u81ea\u52a8\u4fdd\u5b58",
                lastEventAt = System.currentTimeMillis(),
                lastSavedSummary = summary,
                lastSavedAt = System.currentTimeMillis()
            )
        }
    }

    fun markSessionDiscarded(context: Context, reason: String) {
        update(context) { current ->
            current.copy(
                serviceStatus = "\u540e\u53f0\u5f85\u547d\u4e2d",
                lastEvent = reason,
                lastEventAt = System.currentTimeMillis()
            )
        }
    }

    fun flush(context: Context? = null) {
        val preferences = context?.applicationContext?.let(::prefs)
        synchronized(cacheLock) {
            pendingPersistPreferences = preferences ?: pendingPersistPreferences
        }
        mainHandler.removeCallbacks(delayedFlushRunnable)
        flushPendingSnapshot()
    }

    private fun update(
        context: Context,
        throttleWrites: Boolean = false,
        transform: (AutoTrackDiagnostics) -> AutoTrackDiagnostics
    ) {
        val current = load(context)
        val updated = transform(current)
        if (updated == current) {
            return
        }

        diagnosticsCache = updated

        val appContext = context.applicationContext
        val preferences = prefs(appContext)
        val shouldPersistImmediately = synchronized(cacheLock) {
            val now = System.currentTimeMillis()
            if (!throttleWrites || now - lastPersistedAt >= LOCATION_WRITE_INTERVAL_MS) {
                pendingPersistPreferences = null
                pendingPersistDiagnostics = null
                mainHandler.removeCallbacks(delayedFlushRunnable)
                lastPersistedAt = now
                true
            } else {
                pendingPersistPreferences = preferences
                pendingPersistDiagnostics = updated
                val delayMs = LOCATION_WRITE_INTERVAL_MS - (now - lastPersistedAt)
                mainHandler.removeCallbacks(delayedFlushRunnable)
                mainHandler.postDelayed(delayedFlushRunnable, delayMs)
                false
            }
        }

        if (shouldPersistImmediately) {
            persist(preferences, updated)
        }
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    private fun flushPendingSnapshot() {
        val preferences: SharedPreferences
        val diagnostics: AutoTrackDiagnostics
        synchronized(cacheLock) {
            preferences = pendingPersistPreferences ?: return
            diagnostics = pendingPersistDiagnostics ?: diagnosticsCache ?: return
            pendingPersistPreferences = null
            pendingPersistDiagnostics = null
            lastPersistedAt = System.currentTimeMillis()
        }
        persist(preferences, diagnostics)
    }

    private fun persist(preferences: SharedPreferences, diagnostics: AutoTrackDiagnostics) {
        preferences.edit().apply {
            putString(KEY_SERVICE_STATUS, TrackingTextSanitizer.normalize(diagnostics.serviceStatus))
            putString(KEY_LAST_EVENT, TrackingTextSanitizer.normalize(diagnostics.lastEvent))
            putLong(KEY_LAST_EVENT_AT, diagnostics.lastEventAt)
            putString(KEY_LAST_LOCATION_DECISION, TrackingTextSanitizer.normalize(diagnostics.lastLocationDecision))
            putLong(KEY_LAST_LOCATION_AT, diagnostics.lastLocationAt)
            if (diagnostics.lastLocationAccuracyMeters != null) {
                putFloat(KEY_LAST_LOCATION_ACCURACY, diagnostics.lastLocationAccuracyMeters)
            } else {
                remove(KEY_LAST_LOCATION_ACCURACY)
            }
            putInt(KEY_ACCEPTED_POINT_COUNT, diagnostics.acceptedPointCount)
            if (diagnostics.lastSavedSummary != null) {
                putString(KEY_LAST_SAVED_SUMMARY, TrackingTextSanitizer.normalize(diagnostics.lastSavedSummary))
                putLong(KEY_LAST_SAVED_AT, diagnostics.lastSavedAt)
            } else {
                remove(KEY_LAST_SAVED_SUMMARY)
                remove(KEY_LAST_SAVED_AT)
            }
            apply()
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
