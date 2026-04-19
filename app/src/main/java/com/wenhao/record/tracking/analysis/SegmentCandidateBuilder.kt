package com.wenhao.record.tracking.analysis

class SegmentCandidateBuilder {
    fun build(points: List<ScoredPoint>): List<SegmentCandidate> {
        if (points.isEmpty()) return emptyList()

        val segments = mutableListOf<SegmentCandidate>()
        var currentKind = points.first().kind
        var startTimestamp = points.first().timestampMillis
        var lastTimestamp = points.first().timestampMillis
        var pointCount = 1

        for (point in points.drop(1)) {
            if (point.kind == currentKind) {
                lastTimestamp = point.timestampMillis
                pointCount += 1
                continue
            }

            segments += SegmentCandidate(
                kind = currentKind,
                startTimestamp = startTimestamp,
                endTimestamp = lastTimestamp,
                pointCount = pointCount,
            )

            currentKind = point.kind
            startTimestamp = point.timestampMillis
            lastTimestamp = point.timestampMillis
            pointCount = 1
        }

        segments += SegmentCandidate(
            kind = currentKind,
            startTimestamp = startTimestamp,
            endTimestamp = lastTimestamp,
            pointCount = pointCount,
        )

        return segments
    }
}
