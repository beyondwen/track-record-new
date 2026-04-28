package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.AnalysisCursorEntity
import com.wenhao.record.data.local.stream.AnalysisSegmentEntity
import com.wenhao.record.data.local.stream.RawLocationPointEntity
import com.wenhao.record.data.local.stream.StayClusterEntity
import com.wenhao.record.data.local.stream.UploadCursorEntity
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ContinuousPointStorageTest {

    @Test
    fun `continuous point storage exposes append and pending window apis`() {
        val storageClass = Class.forName("com.wenhao.record.data.tracking.ContinuousPointStorage")
        val continuationClass = Continuation::class.java

        assertNotNull(
            storageClass.getDeclaredMethod(
                "appendRawPoint",
                Class.forName("com.wenhao.record.data.tracking.RawTrackPoint"),
                continuationClass,
            )
        )
        assertNotNull(
            storageClass.getDeclaredMethod(
                "loadPendingWindow",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                continuationClass,
            )
        )
    }

    @Test
    fun `continuous point storage model classes exist`() {
        val rawTrackPointClass = Class.forName("com.wenhao.record.data.tracking.RawTrackPoint")
        val samplingTierClass = Class.forName("com.wenhao.record.data.tracking.SamplingTier")

        assertEquals("RawTrackPoint", rawTrackPointClass.simpleName)
        assertEquals("SamplingTier", samplingTierClass.simpleName)
    }

    @Test
    fun `append raw point and load pending window keeps point order and sampling tier`() = runBlocking {
        val dao = FakeContinuousTrackDao()
        val storage = ContinuousPointStorage(dao)

        storage.appendRawPoint(samplePoint(timestampMillis = 1_000L, samplingTier = SamplingTier.IDLE))
        storage.appendRawPoint(samplePoint(timestampMillis = 2_000L, samplingTier = SamplingTier.ACTIVE))

        val points = storage.loadPendingWindow(afterPointId = 0L, limit = 10)

        assertEquals(listOf(1_000L, 2_000L), points.map { it.timestampMillis })
        assertEquals(listOf(SamplingTier.IDLE, SamplingTier.ACTIVE), points.map { it.samplingTier })
    }

    @Test
    fun `load raw points between keeps newest database rows chronological`() = runBlocking {
        val dao = FakeContinuousTrackDao()
        val storage = ContinuousPointStorage(dao)

        repeat(650) { index ->
            storage.appendRawPoint(
                samplePoint(
                    timestampMillis = 1_000L + index,
                    samplingTier = SamplingTier.ACTIVE,
                )
            )
        }

        val points = storage.loadRawPointsBetween(startMillis = 1_000L, endMillis = 1_649L, limit = 500)

        assertEquals(500, points.size)
        assertEquals(1_150L, points.first().timestampMillis)
        assertEquals(1_649L, points.last().timestampMillis)
        assertEquals(500, dao.lastRawPointsBetweenLimit)
    }

    private fun samplePoint(timestampMillis: Long, samplingTier: SamplingTier): RawTrackPoint {
        return RawTrackPoint(
            timestampMillis = timestampMillis,
            latitude = 30.0,
            longitude = 120.0,
            accuracyMeters = 12f,
            altitudeMeters = 8.0,
            speedMetersPerSecond = 1.2f,
            bearingDegrees = 90f,
            provider = "gps",
            sourceType = "LOCATION",
            isMock = false,
            wifiFingerprintDigest = "wifi",
            activityType = "WALKING",
            activityConfidence = 0.8f,
            samplingTier = samplingTier,
        )
    }

    private class FakeContinuousTrackDao : ContinuousTrackDao {
        private val items = mutableListOf<RawLocationPointEntity>()
        private val segments = mutableListOf<AnalysisSegmentEntity>()
        private val stayClusters = mutableListOf<StayClusterEntity>()
        private var analysisCursor: AnalysisCursorEntity? = null
        private var nextPointId = 1L
        var lastRawPointsBetweenLimit: Int? = null
            private set

        override suspend fun insertRawPoint(entity: RawLocationPointEntity): Long {
            val stored = entity.copy(pointId = nextPointId++)
            items += stored
            return stored.pointId
        }

        override suspend fun loadRawPoints(afterPointId: Long, limit: Int): List<RawLocationPointEntity> {
            return items.filter { it.pointId > afterPointId }.sortedBy { it.pointId }.take(limit)
        }

        override suspend fun loadRawPointsBetween(startMillis: Long, endMillis: Long, limit: Int): List<RawLocationPointEntity> {
            lastRawPointsBetweenLimit = limit
            return items
                .filter { it.timestampMillis in startMillis..endMillis }
                .sortedByDescending { it.timestampMillis }
                .take(limit)
        }

        override suspend fun countRawPoints(): Int {
            return items.size
        }

        override suspend fun loadAnalysisSegments(afterSegmentId: Long, limit: Int): List<AnalysisSegmentEntity> {
            return segments.filter { it.segmentId > afterSegmentId }.sortedBy { it.segmentId }.take(limit)
        }

        override suspend fun countAnalysisSegments(): Int {
            return segments.size
        }

        override suspend fun loadStayClustersForSegments(segmentIds: List<Long>): List<StayClusterEntity> {
            return stayClusters.filter { segmentIds.contains(it.segmentId) }.sortedBy { it.segmentId }
        }

        override suspend fun loadAnalysisCursor(): AnalysisCursorEntity? {
            return analysisCursor
        }

        override suspend fun upsertAnalysisCursor(entity: AnalysisCursorEntity) {
            analysisCursor = entity
        }

        override suspend fun insertAnalysisSegments(entities: List<AnalysisSegmentEntity>) {
            segments.removeAll { existing -> entities.any { it.segmentId == existing.segmentId } }
            segments += entities
        }

        override suspend fun insertStayClusters(entities: List<StayClusterEntity>) {
            stayClusters.removeAll { existing -> entities.any { it.stayId == existing.stayId } }
            stayClusters += entities
        }

        override suspend fun loadUploadCursor(cursorType: String): UploadCursorEntity? {
            return null
        }

        override suspend fun upsertUploadCursor(entity: UploadCursorEntity) {
        }

        override suspend fun deleteRawPointsUpTo(upToPointId: Long) {}
    }
}
