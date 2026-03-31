package com.wenhao.record.data.tracking

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.auto.AutoTrackDao
import com.wenhao.record.data.local.auto.AutoTrackPointEntity
import com.wenhao.record.data.local.auto.AutoTrackSessionEntity
import com.wenhao.record.util.AppTaskExecutor
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class AutoTrackSession(
    val startTimestamp: Long,
    val lastMotionTimestamp: Long,
    val totalDistanceKm: Double,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val points: List<TrackPoint> = emptyList()
)

enum class AutoTrackUiState {
    DISABLED,
    WAITING_PERMISSION,
    IDLE,
    PREPARING,
    TRACKING,
    PAUSED_STILL,
    SAVED_RECENTLY
}

object AutoTrackStorage {
    private const val PREFS_NAME = "track_record_auto"
    private const val KEY_SESSION = "ongoing_session"
    private const val KEY_AUTO_ENABLED = "auto_enabled"
    private const val KEY_UI_STATE = "ui_state"

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cacheLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val delayedFlushRunnable = Runnable {
        flushPendingSession()
    }

    @Volatile
    private var sessionCache: AutoTrackSession? = null

    @Volatile
    private var sessionCacheInitialized = false

    @Volatile
    private var sessionCacheLoading = false

    @Volatile
    private var lastPersistedSession: AutoTrackSession? = null

    @Volatile
    private var lastPersistedAt = 0L

    @Volatile
    private var pendingPersistDao: AutoTrackDao? = null

    @Volatile
    private var pendingPersistPreviousSession: AutoTrackSession? = null

    @Volatile
    private var pendingPersistSession: AutoTrackSession? = null

    private val readyCallbacks = mutableListOf<(AutoTrackSession?) -> Unit>()

    fun loadSession(context: Context): AutoTrackSession? {
        ensureSessionCache(context)
        return sessionCache
    }

    fun peekSession(context: Context): AutoTrackSession? {
        ensureSessionCacheAsync(context)
        return sessionCache
    }

    fun warmUp(context: Context) {
        ensureSessionCacheAsync(context)
    }

    fun isReady(): Boolean = sessionCacheInitialized

    fun whenReady(context: Context, callback: (AutoTrackSession?) -> Unit) {
        val readySnapshot = synchronized(cacheLock) {
            if (sessionCacheInitialized) {
                sessionCache
            } else {
                readyCallbacks += callback
                PendingReady
            }
        }
        if (readySnapshot !== PendingReady) {
            AppTaskExecutor.runOnMain {
                @Suppress("UNCHECKED_CAST")
                callback(readySnapshot as AutoTrackSession?)
            }
            return
        }
        ensureSessionCacheAsync(context)
    }

    fun saveSession(context: Context, session: AutoTrackSession) {
        ensureSessionCache(context)
        val appContext = context.applicationContext
        val immediatePersistRequest = synchronized(cacheLock) {
            sessionCache = session
            sessionCacheInitialized = true
            sessionCacheLoading = false
            val persistedSession = lastPersistedSession
            val now = System.currentTimeMillis()
            val shouldPersistImmediately = AutoTrackSessionPersistPolicy.shouldPersistImmediately(
                persistedSession = persistedSession,
                newSession = session,
                lastPersistedAt = lastPersistedAt,
                nowMillis = now
            )
            if (shouldPersistImmediately) {
                mainHandler.removeCallbacks(delayedFlushRunnable)
                pendingPersistDao = null
                pendingPersistPreviousSession = null
                pendingPersistSession = null
                lastPersistedAt = now
                PersistRequest(
                    previousSession = persistedSession,
                    newSession = session
                )
            } else {
                pendingPersistDao = TrackDatabase.getInstance(appContext).autoTrackDao()
                pendingPersistPreviousSession = pendingPersistPreviousSession ?: persistedSession
                pendingPersistSession = session
                val delayMs = AutoTrackSessionPersistPolicy.nextFlushDelayMillis(
                    lastPersistedAt = lastPersistedAt,
                    nowMillis = now
                )
                mainHandler.removeCallbacks(delayedFlushRunnable)
                mainHandler.postDelayed(delayedFlushRunnable, delayMs)
                null
            }
        }
        if (immediatePersistRequest != null) {
            ioExecutor.execute {
                val dao = TrackDatabase.getInstance(appContext).autoTrackDao()
                persistSession(
                    dao = dao,
                    previousSession = immediatePersistRequest.previousSession,
                    newSession = immediatePersistRequest.newSession
                )
                synchronized(cacheLock) {
                    lastPersistedSession = immediatePersistRequest.newSession
                }
            }
        }
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    fun clearSession(context: Context) {
        synchronized(cacheLock) {
            sessionCache = null
            sessionCacheInitialized = true
            sessionCacheLoading = false
            pendingPersistDao = null
            pendingPersistPreviousSession = null
            pendingPersistSession = null
            lastPersistedSession = null
        }
        mainHandler.removeCallbacks(delayedFlushRunnable)
        ioExecutor.execute {
            val dao = TrackDatabase.getInstance(context).autoTrackDao()
            dao.deleteSessionPoints()
            dao.deleteSession()
        }
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    fun flush(context: Context? = null) {
        synchronized(cacheLock) {
            if (context != null && pendingPersistSession != null && pendingPersistDao == null) {
                pendingPersistDao = TrackDatabase.getInstance(context.applicationContext).autoTrackDao()
            }
        }
        mainHandler.removeCallbacks(delayedFlushRunnable)
        flushPendingSession()
    }

    fun isAutoTrackingEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_ENABLED, false)
    }

    fun setAutoTrackingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_AUTO_ENABLED, enabled)
        }
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    fun loadUiState(context: Context): AutoTrackUiState {
        val raw = prefs(context).getString(KEY_UI_STATE, AutoTrackUiState.IDLE.name)
        return raw?.let {
            runCatching { AutoTrackUiState.valueOf(it) }.getOrNull()
        } ?: AutoTrackUiState.IDLE
    }

    fun saveUiState(context: Context, state: AutoTrackUiState) {
        prefs(context).edit {
            putString(KEY_UI_STATE, state.name)
        }
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    private fun ensureSessionCache(context: Context) {
        if (sessionCacheInitialized) return

        synchronized(cacheLock) {
            if (sessionCacheInitialized) return
            val loadedSession = ioExecutor.submit<AutoTrackSession?> {
                loadSessionFromDisk(context)
            }.get()
            sessionCache = loadedSession
            sessionCacheInitialized = true
            lastPersistedSession = loadedSession
            lastPersistedAt = System.currentTimeMillis()
        }
    }

    private fun ensureSessionCacheAsync(context: Context) {
        if (sessionCacheInitialized || sessionCacheLoading) return

        synchronized(cacheLock) {
            if (sessionCacheInitialized || sessionCacheLoading) return
            sessionCacheLoading = true
        }

        ioExecutor.execute {
            val loadedSession = loadSessionFromDisk(context)
            val callbacksToDispatch = synchronized(cacheLock) {
                if (sessionCacheInitialized) {
                    sessionCacheLoading = false
                    return@execute
                }
                sessionCache = loadedSession
                sessionCacheInitialized = true
                sessionCacheLoading = false
                lastPersistedSession = loadedSession
                lastPersistedAt = System.currentTimeMillis()
                readyCallbacks.toList().also { readyCallbacks.clear() }
            }
            TrackDataChangeNotifier.notifyDashboardChanged()
            callbacksToDispatch.forEach { callback ->
                AppTaskExecutor.runOnMain {
                    callback(loadedSession)
                }
            }
        }
    }

    private fun flushPendingSession() {
        val dao: AutoTrackDao
        val previousSession: AutoTrackSession?
        val newSession: AutoTrackSession
        synchronized(cacheLock) {
            dao = pendingPersistDao ?: return
            previousSession = pendingPersistPreviousSession
            newSession = pendingPersistSession ?: return
            pendingPersistDao = null
            pendingPersistPreviousSession = null
            pendingPersistSession = null
            lastPersistedAt = System.currentTimeMillis()
        }
        ioExecutor.execute {
            persistSession(dao, previousSession, newSession)
            synchronized(cacheLock) {
                lastPersistedSession = newSession
            }
        }
    }

    private fun loadSessionFromDisk(context: Context): AutoTrackSession? {
        migrateLegacySessionIfNeeded(context)
        val dao = TrackDatabase.getInstance(context).autoTrackDao()
        val entity = dao.getSessionEntity() ?: return null
        return entity.toModel(dao.getSessionPoints())
    }

    private fun persistSession(
        dao: AutoTrackDao,
        previousSession: AutoTrackSession?,
        newSession: AutoTrackSession
    ) {
        val sessionEntity = newSession.toEntity()

        if (canAppendPoints(previousSession, newSession)) {
            dao.upsertSession(sessionEntity)
            val newPointEntities = newSession.points
                .drop(previousSession?.points?.size ?: 0)
                .mapIndexed { index, point ->
                    point.toAutoTrackPointEntity((previousSession?.points?.size ?: 0) + index)
                }
            if (newPointEntities.isNotEmpty()) {
                dao.insertSessionPoints(newPointEntities)
            }
        } else {
            dao.replaceSession(
                entity = sessionEntity,
                points = newSession.points.mapIndexed { index, point ->
                    point.toAutoTrackPointEntity(index)
                }
            )
        }
    }

    private fun canAppendPoints(
        previousSession: AutoTrackSession?,
        newSession: AutoTrackSession
    ): Boolean {
        if (previousSession == null) return false
        if (previousSession.startTimestamp != newSession.startTimestamp) return false
        if (previousSession.points.size > newSession.points.size) return false
        if (previousSession.points.isEmpty()) return true

        val stablePrefixSize = previousSession.points.size - 1
        if (stablePrefixSize <= 0) return true
        if (newSession.points.size < stablePrefixSize) return false

        for (index in 0 until stablePrefixSize) {
            if (previousSession.points[index] != newSession.points[index]) {
                return false
            }
        }
        return true
    }

    private fun migrateLegacySessionIfNeeded(context: Context) {
        val raw = prefs(context).getString(KEY_SESSION, null) ?: return
        val dao = TrackDatabase.getInstance(context).autoTrackDao()
        val hasRoomData = dao.getSessionCount() > 0
        if (hasRoomData) {
            prefs(context).edit {
                remove(KEY_SESSION)
            }
            return
        }

        val legacySession = parseLegacySession(raw) ?: return
        dao.replaceSession(
            entity = legacySession.toEntity(),
            points = legacySession.points.mapIndexed { index, point ->
                point.toAutoTrackPointEntity(index)
            }
        )
        prefs(context).edit {
            remove(KEY_SESSION)
        }
    }

    private fun parseLegacySession(raw: String): AutoTrackSession? {
        return runCatching {
            val obj = JSONObject(raw)
            val pointsArray = obj.optJSONArray("points") ?: JSONArray()
            val points = buildList {
                for (index in 0 until pointsArray.length()) {
                    val point = pointsArray.getJSONObject(index)
                    add(
                        TrackPoint(
                            latitude = point.getDouble("latitude"),
                            longitude = point.getDouble("longitude"),
                            timestampMillis = point.optLong("timestampMillis"),
                            accuracyMeters = point.optDouble("accuracyMeters")
                                .takeUnless { point.isNull("accuracyMeters") }
                                ?.toFloat(),
                            altitudeMeters = point.optDouble("altitudeMeters")
                                .takeUnless { point.isNull("altitudeMeters") || !point.has("altitudeMeters") }
                        )
                    )
                }
            }
            AutoTrackSession(
                startTimestamp = obj.getLong("startTimestamp"),
                lastMotionTimestamp = obj.optLong("lastMotionTimestamp"),
                totalDistanceKm = obj.optDouble("totalDistanceKm"),
                lastLatitude = obj.optDouble("lastLatitude").takeUnless { obj.isNull("lastLatitude") },
                lastLongitude = obj.optDouble("lastLongitude").takeUnless { obj.isNull("lastLongitude") },
                points = points
            )
        }.getOrNull()
    }

    private fun AutoTrackSession.toEntity(): AutoTrackSessionEntity {
        return AutoTrackSessionEntity(
            startTimestamp = startTimestamp,
            lastMotionTimestamp = lastMotionTimestamp,
            totalDistanceKm = totalDistanceKm,
            lastLatitude = lastLatitude,
            lastLongitude = lastLongitude
        )
    }

    private fun AutoTrackSessionEntity.toModel(points: List<AutoTrackPointEntity>): AutoTrackSession {
        return AutoTrackSession(
            startTimestamp = startTimestamp,
            lastMotionTimestamp = lastMotionTimestamp,
            totalDistanceKm = totalDistanceKm,
            lastLatitude = lastLatitude,
            lastLongitude = lastLongitude,
            points = points.sortedBy { it.pointOrder }.map { point ->
                TrackPoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    timestampMillis = point.timestampMillis,
                    accuracyMeters = point.accuracyMeters,
                    altitudeMeters = point.altitudeMeters,
                    wgs84Latitude = point.wgs84Latitude,
                    wgs84Longitude = point.wgs84Longitude
                )
            }
        )
    }

    private fun TrackPoint.toAutoTrackPointEntity(order: Int): AutoTrackPointEntity {
        return AutoTrackPointEntity(
            pointOrder = order,
            latitude = latitude,
            longitude = longitude,
            timestampMillis = timestampMillis,
            accuracyMeters = accuracyMeters,
            altitudeMeters = altitudeMeters,
            wgs84Latitude = wgs84Latitude,
            wgs84Longitude = wgs84Longitude
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private data class PersistRequest(
        val previousSession: AutoTrackSession?,
        val newSession: AutoTrackSession
    )

    private object PendingReady
}
