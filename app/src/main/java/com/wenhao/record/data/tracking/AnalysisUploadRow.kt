package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.AnalysisSegmentEntity
import com.wenhao.record.data.local.stream.StayClusterEntity

data class AnalysisUploadRow(
    val segmentId: Long,
    val startPointId: Long,
    val endPointId: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val segmentType: String,
    val confidence: Double,
    val distanceMeters: Double,
    val durationMillis: Long,
    val avgSpeedMetersPerSecond: Double,
    val maxSpeedMetersPerSecond: Double,
    val analysisVersion: Int,
    val stayClusters: List<AnalysisStayClusterUploadRow>,
) {
    companion object {
        fun from(
            segment: AnalysisSegmentEntity,
            stayClusters: List<StayClusterEntity>,
        ): AnalysisUploadRow {
            return AnalysisUploadRow(
                segmentId = segment.segmentId,
                startPointId = segment.startPointId,
                endPointId = segment.endPointId,
                startTimestamp = segment.startTimestamp,
                endTimestamp = segment.endTimestamp,
                segmentType = segment.segmentType,
                confidence = segment.confidence.toDouble(),
                distanceMeters = segment.distanceMeters.toDouble(),
                durationMillis = segment.durationMillis,
                avgSpeedMetersPerSecond = segment.avgSpeedMetersPerSecond.toDouble(),
                maxSpeedMetersPerSecond = segment.maxSpeedMetersPerSecond.toDouble(),
                analysisVersion = segment.analysisVersion,
                stayClusters = stayClusters.map(AnalysisStayClusterUploadRow::from),
            )
        }
    }
}

data class AnalysisStayClusterUploadRow(
    val stayId: Long,
    val centerLat: Double,
    val centerLng: Double,
    val radiusMeters: Double,
    val arrivalTime: Long,
    val departureTime: Long,
    val confidence: Double,
    val analysisVersion: Int,
) {
    companion object {
        fun from(entity: StayClusterEntity): AnalysisStayClusterUploadRow {
            return AnalysisStayClusterUploadRow(
                stayId = entity.stayId,
                centerLat = entity.centerLat,
                centerLng = entity.centerLng,
                radiusMeters = entity.radiusMeters.toDouble(),
                arrivalTime = entity.arrivalTime,
                departureTime = entity.departureTime,
                confidence = entity.confidence.toDouble(),
                analysisVersion = entity.analysisVersion,
            )
        }
    }
}
