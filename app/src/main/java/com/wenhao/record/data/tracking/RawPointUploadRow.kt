package com.wenhao.record.data.tracking

data class RawPointUploadRow(
    val pointId: Long,
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Double?,
    val bearingDegrees: Double?,
    val provider: String,
    val sourceType: String,
    val isMock: Boolean,
    val wifiFingerprintDigest: String?,
    val activityType: String?,
    val activityConfidence: Double?,
    val samplingTier: String,
) {
    companion object {
        fun from(point: RawTrackPoint): RawPointUploadRow {
            return RawPointUploadRow(
                pointId = point.pointId,
                timestampMillis = point.timestampMillis,
                latitude = point.latitude,
                longitude = point.longitude,
                accuracyMeters = point.accuracyMeters?.toDouble(),
                altitudeMeters = point.altitudeMeters,
                speedMetersPerSecond = point.speedMetersPerSecond?.toDouble(),
                bearingDegrees = point.bearingDegrees?.toDouble(),
                provider = point.provider,
                sourceType = point.sourceType,
                isMock = point.isMock,
                wifiFingerprintDigest = point.wifiFingerprintDigest,
                activityType = point.activityType,
                activityConfidence = point.activityConfidence?.toDouble(),
                samplingTier = point.samplingTier.name,
            )
        }
    }
}
