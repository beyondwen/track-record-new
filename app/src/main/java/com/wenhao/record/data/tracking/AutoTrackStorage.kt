package com.wenhao.record.data.tracking

import android.content.Context
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.auto.AutoTrackPointEntity
import com.wenhao.record.data.local.auto.AutoTrackSessionEntity
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

    @Volatile
    private var sessionCache: AutoTrackSession? = null

    @Volatile
    private var sessionCacheInitialized = false

    fun loadSession(context: Context): AutoTrackSession? {
        ensureSessionCache(context)
        return sessionCache?.copy(points = sessionCache?.points?.toList().orEmpty())
    }

    fun saveSession(context: Context, session: AutoTrackSession) {
        ensureSessionCache(context)
        val previousSession = synchronized(cacheLock) {
            val previous = sessionCache
            sessionCache = session.copy(points = session.points.toList())
            previous
        }
        ioExecutor.execute {
            persistSession(context, previousSession, session)
        }
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    fun clearSession(context: Context) {
        ensureSessionCache(context)
        synchronized(cacheLock) {
            sessionCache = null
        }
        ioExecutor.execute {
            val dao = TrackDatabase.getInstance(context).autoTrackDao()
            dao.deleteSessionPoints()
            dao.deleteSession()
        }
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    fun isAutoTrackingEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_ENABLED, false)
    }

    fun setAutoTrackingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    fun loadUiState(context: Context): AutoTrackUiState {
        val raw = prefs(context).getString(KEY_UI_STATE, AutoTrackUiState.IDLE.name)
        return raw?.let {
            runCatching { AutoTrackUiState.valueOf(it) }.getOrNull()
        } ?: AutoTrackUiState.IDLE
    }

    fun saveUiState(context: Context, state: AutoTrackUiState) {
        prefs(context).edit().putString(KEY_UI_STATE, state.name).apply()
        TrackDataChangeNotifier.notifyDashboardChanged()
    }

    private fun ensureSessionCache(context: Context) {
        if (sessionCacheInitialized) return

        synchronized(cacheLock) {
            if (sessionCacheInitialized) return
            migrateLegacySessionIfNeeded(context)
            val loadedSession = ioExecutor.submit<AutoTrackSession?> {
                val dao = TrackDatabase.getInstance(context).autoTrackDao()
                val entity = dao.getSessionEntity() ?: return@submit null
                entity.toModel(dao.getSessionPoints())
            }.get()
            sessionCache = loadedSession
            sessionCacheInitialized = true
        }
    }

    private fun persistSession(
        context: Context,
        previousSession: AutoTrackSession?,
        newSession: AutoTrackSession
    ) {
        val dao = TrackDatabase.getInstance(context).autoTrackDao()
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
        return previousSession.points == newSession.points.take(previousSession.points.size)
    }

    private fun migrateLegacySessionIfNeeded(context: Context) {
        val raw = prefs(context).getString(KEY_SESSION, null) ?: return
        val dao = TrackDatabase.getInstance(context).autoTrackDao()
        val hasRoomData = ioExecutor.submit<Int> { dao.getSessionCount() }.get() > 0
        if (hasRoomData) {
            prefs(context).edit().remove(KEY_SESSION).apply()
            return
        }

        val legacySession = parseLegacySession(raw) ?: return
        ioExecutor.submit {
            dao.replaceSession(
                entity = legacySession.toEntity(),
                points = legacySession.points.mapIndexed { index, point ->
                    point.toAutoTrackPointEntity(index)
                }
            )
        }.get()
        prefs(context).edit().remove(KEY_SESSION).apply()
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
                                ?.toFloat()
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
                    accuracyMeters = point.accuracyMeters
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
            accuracyMeters = accuracyMeters
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
