package com.wenhao.record.data.tracking

import android.content.Context
import com.wenhao.record.data.local.TrackDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object TodayTrackDisplayCache {
    const val MAX_DISPLAY_POINTS = 2_048

    suspend fun append(
        context: Context,
        sessionId: String,
        pointId: Long,
        rawPoint: RawTrackPoint,
        phase: String,
        nowMillis: Long = rawPoint.timestampMillis,
    ) {
        storage(context).appendPoint(
            sessionId = sessionId,
            pointId = pointId,
            rawPoint = rawPoint,
            phase = phase,
            nowMillis = nowMillis,
        )
    }

    suspend fun loadToday(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<TrackPoint> {
        val session = storage(context).loadOpenSession(nowMillis) ?: return emptyList()
        return TrackDatabase.getInstance(context.applicationContext)
            .todaySessionDao()
            .loadPoints(session.sessionId)
            .sortedBy { it.timestampMillis }
            .takeLast(MAX_DISPLAY_POINTS)
            .map {
                TrackPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    timestampMillis = it.timestampMillis,
                    accuracyMeters = it.accuracyMeters,
                    altitudeMeters = it.altitudeMeters,
                )
            }
    }

    suspend fun clearIfExpired(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        storage(context).loadOpenSession(nowMillis)
    }

    fun observeToday(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Flow<List<TrackPoint>> {
        return flow {
            emit(loadToday(context, nowMillis))
        }
    }

    private fun storage(context: Context): TodaySessionStorage {
        return TodaySessionStorage(
            TrackDatabase.getInstance(context.applicationContext).todaySessionDao(),
        )
    }
}
