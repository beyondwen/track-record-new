package com.wenhao.record.tracking.analysis

data class AnalyzedPoint(
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val activityType: String?,
    val activityConfidence: Float?,
    val wifiFingerprintDigest: String?,
)

data class PointSignalScore(
    val staticScore: Float,
    val dynamicScore: Float,
)

enum class SegmentKind {
    STATIC,
    DYNAMIC,
    UNCERTAIN,
}

data class ScoredPoint(
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val kind: SegmentKind,
    val staticScore: Float,
    val dynamicScore: Float,
)

data class SegmentCandidate(
    val kind: SegmentKind,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val pointCount: Int,
)

val SegmentCandidate.durationMillis: Long
    get() = (endTimestamp - startTimestamp).coerceAtLeast(0L)

data class StayClusterCandidate(
    val centerLat: Double,
    val centerLng: Double,
    val radiusMeters: Float,
    val arrivalTime: Long,
    val departureTime: Long,
    val confidence: Float,
)

data class AnalysisContext(
    val trailingPoints: List<AnalyzedPoint> = emptyList(),
)

data class TrackAnalysisResult(
    val scoredPoints: List<ScoredPoint>,
    val segments: List<SegmentCandidate>,
    val stayClusters: List<StayClusterCandidate>,
)
