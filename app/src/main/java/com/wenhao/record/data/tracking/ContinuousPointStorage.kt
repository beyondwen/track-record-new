package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.RawLocationPointEntity

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
