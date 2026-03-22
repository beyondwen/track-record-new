package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint

object AdaptiveTrackSmoother {

    fun smooth(
        previousPoint: TrackPoint?,
        candidatePoint: TrackPoint,
        speedMetersPerSecond: Float,
        accuracyMeters: Float?
    ): TrackPoint {
        previousPoint ?: return candidatePoint

        val accuracy = accuracyMeters ?: candidatePoint.accuracyMeters ?: 25f
        val accuracyFactor = when {
            accuracy <= 10f -> 0.82
            accuracy <= 20f -> 0.7
            accuracy <= 35f -> 0.55
            else -> 0.42
        }
        val speedFactor = when {
            speedMetersPerSecond >= 10f -> 0.9
            speedMetersPerSecond >= 4f -> 0.78
            speedMetersPerSecond >= 1.6f -> 0.62
            else -> 0.48
        }
        val alpha = ((accuracyFactor + speedFactor) / 2.0).coerceIn(0.35, 0.9)
        return TrackPoint(
            latitude = previousPoint.latitude + (candidatePoint.latitude - previousPoint.latitude) * alpha,
            longitude = previousPoint.longitude + (candidatePoint.longitude - previousPoint.longitude) * alpha,
            timestampMillis = candidatePoint.timestampMillis,
            accuracyMeters = candidatePoint.accuracyMeters
        )
    }
}
