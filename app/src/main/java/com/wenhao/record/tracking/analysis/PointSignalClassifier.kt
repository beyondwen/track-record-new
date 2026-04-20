package com.wenhao.record.tracking.analysis

import com.wenhao.record.map.GeoMath

class PointSignalClassifier {

    fun classify(points: List<AnalyzedPoint>): PointSignalScore {
        if (points.size < 2) {
            return PointSignalScore(staticScore = 0.6f, dynamicScore = 0.1f)
        }

        val durationMillis = points.last().timestampMillis - points.first().timestampMillis
        val netDistanceMeters = GeoMath.distanceMeters(
            points.first().latitude,
            points.first().longitude,
            points.last().latitude,
            points.last().longitude,
        )
        val avgSpeed = points.mapNotNull { it.speedMetersPerSecond }.average().toFloat()

        val stillConfidence = points.mapNotNull { point ->
            point.activityConfidence?.takeIf { point.activityType == "STILL" }
        }.average().toFloat()

        val movingConfidence = points.mapNotNull { point ->
            point.activityConfidence?.takeIf {
                point.activityType == "WALKING" ||
                point.activityType == "ON_BICYCLE" ||
                point.activityType == "IN_VEHICLE"
            }
        }.average().toFloat()

        val stableWifi = points.mapNotNull { it.wifiFingerprintDigest }.distinct().size <= 1

        val dynamicScore = when {
            durationMillis >= 90_000L && netDistanceMeters >= 120f && avgSpeed >= 0.8f && movingConfidence >= 0.72f -> 0.92f
            durationMillis >= 45_000L && netDistanceMeters >= 60f && avgSpeed >= 0.5f -> 0.75f
            netDistanceMeters >= 30f && avgSpeed >= 0.4f -> 0.55f
            else -> 0.12f
        }

        val staticScore = when {
            netDistanceMeters <= 30f && avgSpeed <= 0.35f && stableWifi && stillConfidence >= 0.8f -> 0.9f
            netDistanceMeters <= 45f && avgSpeed <= 0.5f && stableWifi -> 0.72f
            else -> 0.18f
        }

        return PointSignalScore(staticScore = staticScore, dynamicScore = dynamicScore)
    }
}
