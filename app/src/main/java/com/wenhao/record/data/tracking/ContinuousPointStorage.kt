package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.AnalysisCursorEntity
import com.wenhao.record.data.local.stream.AnalysisSegmentEntity
import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.RawLocationPointEntity
import com.wenhao.record.data.local.stream.StayClusterEntity

enum class SamplingTier {
    IDLE,
    SUSPECT,
    ACTIVE,
}

data class RawTrackPoint(
    val pointId: Long = 0,
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val provider: String,
    val sourceType: String,
    val isMock: Boolean,
    val wifiFingerprintDigest: String?,
    val activityType: String?,
    val activityConfidence: Float?,
    val samplingTier: SamplingTier,
)

class ContinuousPointStorage(
    private val dao: ContinuousTrackDao,
) {
    fun appendRawPoint(point: RawTrackPoint): Long {
        return dao.insertRawPoint(point.toEntity())
    }

    fun loadPendingWindow(afterPointId: Long, limit: Int): List<RawTrackPoint> {
        return dao.loadRawPoints(afterPointId = afterPointId, limit = limit).map { it.toModel() }
    }

    fun loadCurrentSessionPoints(limit: Int): List<RawTrackPoint> {
        return loadPendingWindow(afterPointId = 0L, limit = limit)
    }

    fun loadPendingRawUploadRows(afterPointId: Long, limit: Int): List<RawPointUploadRow> {
        return loadPendingWindow(afterPointId = afterPointId, limit = limit).map(RawPointUploadRow::from)
    }

    fun loadPendingAnalysisUploadRows(afterSegmentId: Long, limit: Int): List<AnalysisUploadRow> {
        val segments = dao.loadAnalysisSegments(afterSegmentId = afterSegmentId, limit = limit)
        if (segments.isEmpty()) return emptyList()

        val stayClustersBySegmentId = dao.loadStayClustersForSegments(
            segmentIds = segments.map { it.segmentId }
        ).groupBy { it.segmentId }

        return segments.map { segment ->
            AnalysisUploadRow.from(
                segment = segment,
                stayClusters = stayClustersBySegmentId[segment.segmentId].orEmpty(),
            )
        }
    }

    fun saveAnalysisResult(
        analyzedUpToPointId: Long,
        segments: List<AnalysisSegmentEntity>,
        stayClusters: List<StayClusterEntity>,
    ) {
        dao.insertAnalysisSegments(segments)
        dao.insertStayClusters(stayClusters)
        dao.upsertAnalysisCursor(
            AnalysisCursorEntity(
                lastAnalyzedPointId = analyzedUpToPointId,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun loadAnalysisCursor(): AnalysisCursorEntity? {
        return dao.loadAnalysisCursor()
    }

    fun deleteRawPointsUpTo(upToPointId: Long) {
        dao.deleteRawPointsUpTo(upToPointId)
    }
}

private fun RawTrackPoint.toEntity(): RawLocationPointEntity {
    return RawLocationPointEntity(
        pointId = pointId,
        timestampMillis = timestampMillis,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        altitudeMeters = altitudeMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
        provider = provider,
        sourceType = sourceType,
        isMock = isMock,
        wifiFingerprintDigest = wifiFingerprintDigest,
        activityType = activityType,
        activityConfidence = activityConfidence,
        samplingTier = samplingTier.name,
    )
}

private fun RawLocationPointEntity.toModel(): RawTrackPoint {
    return RawTrackPoint(
        pointId = pointId,
        timestampMillis = timestampMillis,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        altitudeMeters = altitudeMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
        provider = provider,
        sourceType = sourceType,
        isMock = isMock,
        wifiFingerprintDigest = wifiFingerprintDigest,
        activityType = activityType,
        activityConfidence = activityConfidence,
        samplingTier = runCatching { SamplingTier.valueOf(samplingTier) }.getOrDefault(SamplingTier.IDLE),
    )
}
