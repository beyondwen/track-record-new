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
        val pathDistanceMeters = points.zipWithNext { first, second ->
            GeoMath.distanceMeters(
                first.latitude,
                first.longitude,
                second.latitude,
                second.longitude,
            )
        }.sum()
        val observedSpeed = points.mapNotNull { it.speedMetersPerSecond }.averageOrNull() ?: 0f
        val inferredSpeed = when {
            durationMillis <= 0L -> 0f
            else -> pathDistanceMeters / (durationMillis / 1_000f)
        }
        val blendedSpeed = maxOf(observedSpeed, inferredSpeed)
        val averageAccuracy = points.mapNotNull { it.accuracyMeters }.averageOrNull() ?: 20f

        val stillConfidence = points.mapNotNull { point ->
            point.activityConfidence?.takeIf { point.activityType == "STILL" }
        }.averageOrNull() ?: 0f

        val movingConfidence = points.mapNotNull { point ->
            point.activityConfidence?.takeIf {
                point.activityType == "WALKING" ||
                point.activityType == "ON_BICYCLE" ||
                point.activityType == "IN_VEHICLE"
            }
        }.averageOrNull() ?: 0f

        val wifiDigests = points.mapNotNull { it.wifiFingerprintDigest }
        val stableWifi = wifiDigests.isNotEmpty() && wifiDigests.distinct().size <= 1
        val lowAccuracyPenalty = when {
            averageAccuracy >= 80f -> 0.32f
            averageAccuracy >= 60f -> 0.2f
            averageAccuracy >= 40f -> 0.08f
            else -> 0f
        }

        val dynamicScoreBase = when {
            durationMillis >= 90_000L &&
                pathDistanceMeters >= 160f &&
                netDistanceMeters >= 120f &&
                blendedSpeed >= 0.8f &&
                movingConfidence >= 0.72f -> 0.92f
            durationMillis >= 60_000L &&
                pathDistanceMeters >= 90f &&
                netDistanceMeters >= 60f &&
                blendedSpeed >= 0.55f -> 0.76f
            pathDistanceMeters >= 45f &&
                netDistanceMeters >= 30f &&
                blendedSpeed >= 0.4f &&
                movingConfidence >= 0.5f -> 0.56f
            else -> 0.12f
        }
        val dynamicSuppression = when {
            stableWifi && stillConfidence >= 0.75f && netDistanceMeters <= 60f -> 0.28f
            stableWifi && netDistanceMeters <= 45f -> 0.15f
            else -> 0f
        }
        val dynamicScore = (dynamicScoreBase - lowAccuracyPenalty - dynamicSuppression)
            .coerceIn(0.05f, 1f)

        val staticScoreBase = when {
            netDistanceMeters <= 25f &&
                pathDistanceMeters <= 40f &&
                blendedSpeed <= 0.25f &&
                stableWifi &&
                stillConfidence >= 0.8f -> 0.92f
            netDistanceMeters <= 40f &&
                pathDistanceMeters <= 55f &&
                blendedSpeed <= 0.35f &&
                (stableWifi || stillConfidence >= 0.75f) -> 0.78f
            netDistanceMeters <= 60f &&
                pathDistanceMeters <= 85f &&
                blendedSpeed <= 0.5f -> 0.58f
            else -> 0.18f
        }
        val staticBoost = when {
            averageAccuracy >= 60f && netDistanceMeters <= 80f -> 0.18f
            averageAccuracy >= 40f && netDistanceMeters <= 60f -> 0.08f
            else -> 0f
        }
        val staticScore = (staticScoreBase + staticBoost).coerceIn(0.05f, 1f)

        return PointSignalScore(staticScore = staticScore, dynamicScore = dynamicScore)
    }

    private fun List<Float>.averageOrNull(): Float? {
        if (isEmpty()) return null
        val average = average().toFloat()
        return average.takeIf { it.isFinite() }
    }
}
