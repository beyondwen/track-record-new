package com.wenhao.record.data.history

import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.GeoMath
import kotlin.math.max
import kotlin.math.roundToInt

enum class TrackQualityLevel(val label: String) {
    EXCELLENT("优秀"),
    GOOD("良好"),
    FAIR("一般"),
    LOW("较弱")
}

data class TrackQuality(
    val score: Int,
    val level: TrackQualityLevel,
    val averageAccuracyMeters: Int?,
    val pointCount: Int,
    val detail: String
) {
    val badgeLabel: String
        get() = "${level.label} ${score}分"
}

object TrackQualityEvaluator {

    fun evaluate(item: HistoryItem): TrackQuality {
        return evaluateSegments(
            segments = listOf(item.points),
            totalDistanceKm = item.distanceKm,
            totalDurationSeconds = item.durationSeconds
        )
    }

    fun evaluateSegments(
        segments: List<List<TrackPoint>>,
        totalDistanceKm: Double,
        totalDurationSeconds: Int
    ): TrackQuality {
        val normalizedSegments = segments.filter { it.isNotEmpty() }
        val points = normalizedSegments.flatten()

        if (points.size < 2) {
            return TrackQuality(
                score = 32,
                level = TrackQualityLevel.LOW,
                averageAccuracyMeters = points.firstOrNull()?.accuracyMeters?.roundToInt(),
                pointCount = points.size,
                detail = "定位点较少，轨迹可能还不够完整。"
            )
        }

        if (normalizedSegments.none { it.size > 1 }) {
            return TrackQuality(
                score = 34,
                level = TrackQualityLevel.LOW,
                averageAccuracyMeters = points.mapNotNull { it.accuracyMeters?.roundToInt() }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.roundToInt(),
                pointCount = points.size,
                detail = "有效轨迹段不足，暂时无法还原完整路线。"
            )
        }

        val accuracySamples = points.mapNotNull { point -> point.accuracyMeters?.toDouble() }
        val averageAccuracyMeters = accuracySamples
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToInt()

        val durationMinutes = max(totalDurationSeconds / 60.0, 1.0)
        val pointDensity = points.size / durationMinutes

        var longGapCount = 0
        var abnormalJumpCount = 0
        var tinyMoveCount = 0
        var adjacentPairCount = 0

        normalizedSegments.forEach { segment ->
            for (i in 0 until segment.size - 1) {
                val previous = segment[i]
                val current = segment[i + 1]
                adjacentPairCount++
                val deltaSeconds =
                    ((current.timestampMillis - previous.timestampMillis) / 1000.0).coerceAtLeast(1.0)
                val distanceMeters = distanceBetween(previous, current)
                val inferredSpeedMetersPerSecond = distanceMeters / deltaSeconds

                if (deltaSeconds >= 90.0) {
                    longGapCount++
                }
                if (inferredSpeedMetersPerSecond >= 38.0) {
                    abnormalJumpCount++
                }
                if (distanceMeters <= 8f) {
                    tinyMoveCount++
                }
            }
        }

        var score = 100
        score -= when {
            averageAccuracyMeters == null -> 10
            averageAccuracyMeters >= 55 -> 34
            averageAccuracyMeters >= 35 -> 22
            averageAccuracyMeters >= 20 -> 12
            averageAccuracyMeters >= 12 -> 6
            else -> 0
        }
        score -= when {
            pointDensity < 0.7 -> 24
            pointDensity < 1.2 -> 14
            pointDensity < 2.0 -> 6
            else -> 0
        }
        if (pointDensity >= 4.0) {
            score += 3
        }

        score -= (longGapCount * 6).coerceAtMost(18)
        score -= (abnormalJumpCount * 5).coerceAtMost(18)

        val pairCount = adjacentPairCount.coerceAtLeast(1)
        val tinyMoveRatio = tinyMoveCount.toDouble() / pairCount
        if (tinyMoveRatio >= 0.55 && totalDistanceKm >= 0.4) {
            score -= 8
        }
        if (totalDistanceKm < 0.2 && points.size < 6) {
            score -= 8
        }

        score = score.coerceIn(28, 98)

        val level = when {
            score >= 85 -> TrackQualityLevel.EXCELLENT
            score >= 70 -> TrackQualityLevel.GOOD
            score >= 55 -> TrackQualityLevel.FAIR
            else -> TrackQualityLevel.LOW
        }

        val detail = buildString {
            append("轨迹质量")
            append(level.label)
            averageAccuracyMeters?.let { accuracy ->
                append("，平均精度约 ")
                append(accuracy)
                append(" 米")
            }
            append("，共 ")
            append(points.size)
            append(" 个定位点")
            if (normalizedSegments.size > 1) {
                append("，分 ")
                append(normalizedSegments.size)
                append(" 段记录")
            }
            when {
                abnormalJumpCount > 0 -> append("，检测到少量急跳风险")
                longGapCount > 0 -> append("，中间存在较长采样间隔")
                pointDensity >= 2.0 -> append("，采样密度比较稳定")
            }
        }

        return TrackQuality(
            score = score,
            level = level,
            averageAccuracyMeters = averageAccuracyMeters,
            pointCount = points.size,
            detail = detail
        )
    }

    private fun distanceBetween(first: TrackPoint, second: TrackPoint): Float {
        return GeoMath.distanceMeters(first, second)
    }
}
