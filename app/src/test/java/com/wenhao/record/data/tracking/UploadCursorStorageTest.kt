package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.AnalysisCursorEntity
import com.wenhao.record.data.local.stream.AnalysisSegmentEntity
import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.RawLocationPointEntity
import com.wenhao.record.data.local.stream.StayClusterEntity
import com.wenhao.record.data.local.stream.UploadCursorEntity
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadCursorStorageTest {

    @Test
    fun `load returns zero cursor when storage is empty`() = runBlocking {
        val storage = UploadCursorStorage(FakeContinuousTrackDao())

        val cursor = storage.load(UploadCursorType.RAW_POINT)

        assertEquals(UploadCursorType.RAW_POINT, cursor.type)
        assertEquals(0L, cursor.lastUploadedId)
        assertEquals(0L, cursor.updatedAt)
    }

    @Test
    fun `mark uploaded updates cursor and preserves latest id`() = runBlocking {
        val storage = UploadCursorStorage(FakeContinuousTrackDao())

        storage.markUploaded(UploadCursorType.RAW_POINT, lastUploadedId = 12L, updatedAt = 1_000L)
        storage.markUploaded(UploadCursorType.RAW_POINT, lastUploadedId = 9L, updatedAt = 2_000L)

        val cursor = storage.load(UploadCursorType.RAW_POINT)
        assertEquals(12L, cursor.lastUploadedId)
        assertEquals(2_000L, cursor.updatedAt)
    }

    private class FakeContinuousTrackDao : ContinuousTrackDao {
        private val uploadCursors = linkedMapOf<String, UploadCursorEntity>()

        override suspend fun insertRawPoint(entity: RawLocationPointEntity): Long {
            throw UnsupportedOperationException()
        }

        override suspend fun loadRawPoints(afterPointId: Long, limit: Int): List<RawLocationPointEntity> {
            return emptyList()
        }

        override suspend fun loadRawPointsBetween(startMillis: Long, endMillis: Long, limit: Int): List<RawLocationPointEntity> {
            return emptyList()
        }

        override suspend fun countRawPoints(): Int {
            return 0
        }

        override suspend fun loadAnalysisSegments(afterSegmentId: Long, limit: Int): List<AnalysisSegmentEntity> {
            return emptyList()
        }

        override suspend fun countAnalysisSegments(): Int {
            return 0
        }

        override suspend fun loadStayClustersForSegments(segmentIds: List<Long>): List<StayClusterEntity> {
            return emptyList()
        }

        override suspend fun loadAnalysisCursor(): AnalysisCursorEntity? {
            return null
        }

        override suspend fun upsertAnalysisCursor(entity: AnalysisCursorEntity) {
        }

        override suspend fun insertAnalysisSegments(entities: List<AnalysisSegmentEntity>) {
        }

        override suspend fun insertStayClusters(entities: List<StayClusterEntity>) {
        }

        override suspend fun loadUploadCursor(cursorType: String): UploadCursorEntity? {
            return uploadCursors[cursorType]
        }

        override suspend fun upsertUploadCursor(entity: UploadCursorEntity) {
            uploadCursors[entity.cursorType] = entity
        }

        override suspend fun deleteRawPointsUpTo(upToPointId: Long) {}
    }
}
