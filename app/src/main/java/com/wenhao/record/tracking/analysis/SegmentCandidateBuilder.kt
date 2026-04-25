package com.wenhao.record.tracking.analysis

import java.util.Calendar

class SegmentCandidateBuilder(
    private val maxPointGapMillis: Long = 30 * 60_000L,
) {
    fun build(points: List<ScoredPoint>): List<SegmentCandidate> {
        if (points.isEmpty()) return emptyList()

        val segments = mutableListOf<SegmentCandidate>()
        var currentKind = points.first().kind
        var startTimestamp = points.first().timestampMillis
        var lastTimestamp = points.first().timestampMillis
        var pointCount = 1

        for (point in points.drop(1)) {
            if (point.kind == currentKind && !shouldStartNewSegment(lastTimestamp, point.timestampMillis)) {
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

    private fun shouldStartNewSegment(previousTimestamp: Long, currentTimestamp: Long): Boolean {
        if (previousTimestamp <= 0L || currentTimestamp <= 0L) return false
        if (currentTimestamp <= previousTimestamp) return true
        if (currentTimestamp - previousTimestamp > maxPointGapMillis) return true
        return !isSameLocalDay(previousTimestamp, currentTimestamp)
    }

    private fun isSameLocalDay(firstTimestamp: Long, secondTimestamp: Long): Boolean {
        val first = Calendar.getInstance().apply { timeInMillis = firstTimestamp }
        val second = Calendar.getInstance().apply { timeInMillis = secondTimestamp }
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }
}
