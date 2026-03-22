package com.example.helloworld.data.tracking

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    fun loadSession(context: Context): AutoTrackSession? {
        val raw = prefs(context).getString(KEY_SESSION, null) ?: return null
        val obj = JSONObject(raw)
        val pointsArray = obj.optJSONArray("points") ?: JSONArray()
        val points = buildList {
            for (index in 0 until pointsArray.length()) {
                val point = pointsArray.getJSONObject(index)
                add(
                    TrackPoint(
                        latitude = point.getDouble("latitude"),
                        longitude = point.getDouble("longitude")
                    )
                )
            }
        }
        return AutoTrackSession(
            startTimestamp = obj.getLong("startTimestamp"),
            lastMotionTimestamp = obj.optLong("lastMotionTimestamp"),
            totalDistanceKm = obj.optDouble("totalDistanceKm"),
            lastLatitude = obj.optDouble("lastLatitude").takeUnless { obj.isNull("lastLatitude") },
            lastLongitude = obj.optDouble("lastLongitude").takeUnless { obj.isNull("lastLongitude") },
            points = points
        )
    }

    fun saveSession(context: Context, session: AutoTrackSession) {
        val pointsArray = JSONArray()
        session.points.forEach { point ->
            pointsArray.put(
                JSONObject().apply {
                    put("latitude", point.latitude)
                    put("longitude", point.longitude)
                }
            )
        }
        val payload = JSONObject().apply {
            put("startTimestamp", session.startTimestamp)
            put("lastMotionTimestamp", session.lastMotionTimestamp)
            put("totalDistanceKm", session.totalDistanceKm)
            put("lastLatitude", session.lastLatitude)
            put("lastLongitude", session.lastLongitude)
            put("points", pointsArray)
        }
        prefs(context).edit().putString(KEY_SESSION, payload.toString()).apply()
    }

    fun clearSession(context: Context) {
        prefs(context).edit().remove(KEY_SESSION).apply()
    }

    fun isAutoTrackingEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_ENABLED, false)
    }

    fun setAutoTrackingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
    }

    fun loadUiState(context: Context): AutoTrackUiState {
        val raw = prefs(context).getString(KEY_UI_STATE, AutoTrackUiState.IDLE.name)
        return raw?.let {
            runCatching { AutoTrackUiState.valueOf(it) }.getOrNull()
        } ?: AutoTrackUiState.IDLE
    }

    fun saveUiState(context: Context, state: AutoTrackUiState) {
        prefs(context).edit().putString(KEY_UI_STATE, state.name).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
