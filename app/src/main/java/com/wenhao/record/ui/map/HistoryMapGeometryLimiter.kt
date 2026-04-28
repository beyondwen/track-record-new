package com.wenhao.record.ui.map

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.math.roundToInt

internal object HistoryMapGeometryLimiter {
    const val MAX_RENDER_SEGMENTS = 24
    const val MAX_RENDER_POINTS = 960

    fun limitSegments(segments: List<List<TrackPoint>>): List<List<TrackPoint>> {
        val nonEmptySegments = segments.filter { segment -> segment.isNotEmpty() }
        if (nonEmptySegments.isEmpty()) return emptyList()

        val selectedSegments = nonEmptySegments.evenlySample(MAX_RENDER_SEGMENTS)
        val pointBudgetPerSegment = (MAX_RENDER_POINTS / selectedSegments.size)
            .coerceAtLeast(2)

        return selectedSegments
            .map { segment -> segment.downsample(pointBudgetPerSegment) }
            .filter { segment -> segment.isNotEmpty() }
    }

    private fun <T> List<T>.evenlySample(maxItems: Int): List<T> {
        if (size <= maxItems || maxItems < 2) return this

        val stride = lastIndex.toDouble() / (maxItems - 1).toDouble()
        val sampled = ArrayList<T>(maxItems)
        var previousIndex = -1
        repeat(maxItems) { sampleIndex ->
            val sourceIndex = when (sampleIndex) {
                0 -> 0
                maxItems - 1 -> lastIndex
                else -> (sampleIndex * stride).roundToInt().coerceIn(1, lastIndex - 1)
            }
            if (sourceIndex != previousIndex) {
                sampled += this[sourceIndex]
                previousIndex = sourceIndex
            }
        }
        return sampled
    }

    private fun List<TrackPoint>.downsample(maxPoints: Int): List<TrackPoint> {
        if (size <= maxPoints || maxPoints < 2) return this

        val stride = lastIndex.toDouble() / (maxPoints - 1).toDouble()
        val sampled = ArrayList<TrackPoint>(maxPoints)
        var previousIndex = -1
        repeat(maxPoints) { sampleIndex ->
            val sourceIndex = when (sampleIndex) {
                0 -> 0
                maxPoints - 1 -> lastIndex
                else -> (sampleIndex * stride).roundToInt().coerceIn(1, lastIndex - 1)
            }
            if (sourceIndex != previousIndex) {
                sampled += this[sourceIndex]
                previousIndex = sourceIndex
            }
        }
        return sampled
    }
}
