package com.wenhao.record.data.tracking

import android.content.Context
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.stream.TodayDisplayPointEntity
import com.wenhao.record.data.local.stream.TodayTrackDisplayDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

object TodayTrackDisplayCache {
    const val MAX_DISPLAY_POINTS = 2_048

    suspend fun append(
        context: Context,
        rawPoint: RawTrackPoint,
        nowMillis: Long = rawPoint.timestampMillis,
    ) {
        storage(context).append(rawPoint, nowMillis)
    }

    suspend fun loadToday(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<TrackPoint> {
        return storage(context).loadToday(nowMillis)
    }

    suspend fun clearIfExpired(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        storage(context).clearIfExpired(nowMillis)
    }

    fun observeToday(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Flow<List<TrackPoint>> {
        return storage(context).observeToday(nowMillis)
    }

    private fun storage(context: Context): TodayTrackDisplayStorage {
        return TodayTrackDisplayStorage(
            TrackDatabase.getInstance(context.applicationContext).todayTrackDisplayDao(),
        )
    }
}

class TodayTrackDisplayStorage(
    private val dao: TodayTrackDisplayDao,
) {
    private companion object {
        const val DAY_SWITCH_CHECK_INTERVAL_MILLIS = 60_000L
    }

    suspend fun append(
        rawPoint: RawTrackPoint,
        nowMillis: Long = rawPoint.timestampMillis,
    ) {
        val todayStart = dayStartMillis(nowMillis)
        val pointDayStart = dayStartMillis(rawPoint.timestampMillis)
        dao.deleteExceptDay(todayStart)
        if (pointDayStart != todayStart) return

        dao.upsertPoint(rawPoint.toEntity(todayStart))
        dao.trimDayToNewest(todayStart, TodayTrackDisplayCache.MAX_DISPLAY_POINTS)
    }

    suspend fun loadToday(nowMillis: Long = System.currentTimeMillis()): List<TrackPoint> {
        val todayStart = dayStartMillis(nowMillis)
        dao.deleteExceptDay(todayStart)
        return dao.loadPointsForDay(todayStart).toTrackPoints()
    }

    suspend fun clearIfExpired(nowMillis: Long = System.currentTimeMillis()) {
        dao.deleteExceptDay(dayStartMillis(nowMillis))
    }

    fun observeToday(nowMillis: Long = System.currentTimeMillis()): Flow<List<TrackPoint>> {
        return observeToday(dayStartTicker(initialMillis = nowMillis))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeToday(dayStartMillisFlow: Flow<Long>): Flow<List<TrackPoint>> {
        return dayStartMillisFlow
            .distinctUntilChanged()
            .flatMapLatest { todayStart ->
                flow {
                    dao.deleteExceptDay(todayStart)
                    emitAll(
                        dao.observePointsForDay(todayStart)
                            .map { points -> points.toTrackPoints() }
                            .distinctUntilChanged()
                    )
                }
            }
    }

    private fun dayStartTicker(initialMillis: Long): Flow<Long> {
        return flow {
            var currentDayStart = dayStartMillis(initialMillis)
            emit(currentDayStart)
            while (true) {
                delay(DAY_SWITCH_CHECK_INTERVAL_MILLIS)
                val nextDayStart = dayStartMillis(System.currentTimeMillis())
                if (nextDayStart != currentDayStart) {
                    currentDayStart = nextDayStart
                    emit(currentDayStart)
                }
            }
        }
    }

    private fun List<TodayDisplayPointEntity>.toTrackPoints(): List<TrackPoint> {
        return sortedBy { it.timestampMillis }
            .takeLast(TodayTrackDisplayCache.MAX_DISPLAY_POINTS)
            .map { it.toTrackPoint() }
    }

    private fun RawTrackPoint.toEntity(dayStartMillis: Long): TodayDisplayPointEntity {
        return TodayDisplayPointEntity(
            timestampMillis = timestampMillis,
            dayStartMillis = dayStartMillis,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            altitudeMeters = altitudeMeters,
        )
    }

    private fun TodayDisplayPointEntity.toTrackPoint(): TrackPoint {
        return TrackPoint(
            latitude = latitude,
            longitude = longitude,
            timestampMillis = timestampMillis,
            accuracyMeters = accuracyMeters,
            altitudeMeters = altitudeMeters,
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
