package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.TodayDisplayPointEntity
import com.wenhao.record.data.local.stream.TodayTrackDisplayDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodayTrackDisplayCacheTest {

    @Test
    fun `append and load today keeps current day points in timestamp order`() = runBlocking {
        val dayStart = 1_771_430_400_000L
        val storage = TodayTrackDisplayStorage(FakeTodayTrackDisplayDao())

        storage.append(rawPoint(timestampMillis = dayStart + 2_000L, latitude = 30.2), nowMillis = dayStart + 2_000L)
        storage.append(rawPoint(timestampMillis = dayStart + 1_000L, latitude = 30.1), nowMillis = dayStart + 1_000L)

        val points = storage.loadToday(nowMillis = dayStart + 3_000L)

        assertEquals(listOf(dayStart + 1_000L, dayStart + 2_000L), points.map { it.timestampMillis })
        assertEquals(listOf(30.1, 30.2), points.map { it.latitude })
    }

    @Test
    fun `load today clears previous day cache after midnight`() = runBlocking {
        val dayStart = 1_771_430_400_000L
        val nextDayStart = dayStart + 86_400_000L
        val dao = FakeTodayTrackDisplayDao()
        val storage = TodayTrackDisplayStorage(dao)

        storage.append(rawPoint(timestampMillis = dayStart + 10_000L), nowMillis = dayStart + 10_000L)

        assertTrue(storage.loadToday(nowMillis = nextDayStart + 1_000L).isEmpty())
        assertTrue(dao.items.isEmpty())
    }

    @Test
    fun `append caps display cache to newest points`() = runBlocking {
        val dayStart = 1_771_430_400_000L
        val storage = TodayTrackDisplayStorage(FakeTodayTrackDisplayDao())

        repeat(TodayTrackDisplayCache.MAX_DISPLAY_POINTS + 5) { index ->
            storage.append(
                rawPoint(timestampMillis = dayStart + index, latitude = 30.0 + index),
                nowMillis = dayStart + index,
            )
        }

        val points = storage.loadToday(nowMillis = dayStart + 10_000L)

        assertEquals(TodayTrackDisplayCache.MAX_DISPLAY_POINTS, points.size)
        assertEquals(dayStart + 5, points.first().timestampMillis)
        assertEquals(dayStart + TodayTrackDisplayCache.MAX_DISPLAY_POINTS + 4, points.last().timestampMillis)
    }

    @Test
    fun `dao exposes flow for current day display points`() {
        val method = Class.forName("com.wenhao.record.data.local.stream.TodayTrackDisplayDao")
            .getDeclaredMethod("observePointsForDay", Long::class.javaPrimitiveType)

        assertEquals(Flow::class.java.name, method.returnType.name)
    }

    @Test
    fun `storage exposes day switching observe flow`() {
        val method = TodayTrackDisplayStorage::class.java.getDeclaredMethod(
            "observeToday",
            Flow::class.java,
        )

        assertEquals(Flow::class.java.name, method.returnType.name)
    }

    private fun rawPoint(timestampMillis: Long, latitude: Double = 30.0): RawTrackPoint {
        return RawTrackPoint(
            timestampMillis = timestampMillis,
            latitude = latitude,
            longitude = 120.0,
            accuracyMeters = 8f,
            altitudeMeters = 18.0,
            speedMetersPerSecond = 1.2f,
            bearingDegrees = null,
            provider = "gps",
            sourceType = "LOCATION_MANAGER",
            isMock = false,
            wifiFingerprintDigest = null,
            activityType = "WALKING",
            activityConfidence = 0.9f,
            samplingTier = SamplingTier.ACTIVE,
        )
    }

    private class FakeTodayTrackDisplayDao : TodayTrackDisplayDao {
        val items = mutableListOf<TodayDisplayPointEntity>()
        private val stream = MutableStateFlow<List<TodayDisplayPointEntity>>(emptyList())

        override suspend fun upsertPoint(entity: TodayDisplayPointEntity) {
            items.removeAll { it.timestampMillis == entity.timestampMillis }
            items += entity
            stream.value = items.toList()
        }

        override suspend fun loadPointsForDay(dayStartMillis: Long): List<TodayDisplayPointEntity> {
            return items.filter { it.dayStartMillis == dayStartMillis }.sortedBy { it.timestampMillis }
        }

        override fun observePointsForDay(dayStartMillis: Long): Flow<List<TodayDisplayPointEntity>> {
            return stream
        }

        override suspend fun countPointsForDay(dayStartMillis: Long): Int {
            return items.count { it.dayStartMillis == dayStartMillis }
        }

        override suspend fun deleteExceptDay(dayStartMillis: Long) {
            items.removeAll { it.dayStartMillis != dayStartMillis }
            stream.value = items.toList()
        }

        override suspend fun trimDayToNewest(dayStartMillis: Long, maxPoints: Int) {
            val keep = items
                .filter { it.dayStartMillis == dayStartMillis }
                .sortedByDescending { it.timestampMillis }
                .take(maxPoints)
                .map { it.timestampMillis }
                .toSet()
            items.removeAll { it.dayStartMillis == dayStartMillis && it.timestampMillis !in keep }
            stream.value = items.toList()
        }
    }
}
