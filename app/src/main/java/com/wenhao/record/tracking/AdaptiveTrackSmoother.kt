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
        val smoothedWgs84Latitude = candidatePoint.wgs84Latitude?.let { wgs84Lat ->
            previousPoint.wgs84Latitude?.let { prevWgs84Lat ->
                prevWgs84Lat + (wgs84Lat - prevWgs84Lat) * alpha
            }
        }
        val smoothedWgs84Longitude = candidatePoint.wgs84Longitude?.let { wgs84Lon ->
            previousPoint.wgs84Longitude?.let { prevWgs84Lon ->
                prevWgs84Lon + (wgs84Lon - prevWgs84Lon) * alpha
            }
        }
        return TrackPoint(
            latitude = previousPoint.latitude + (candidatePoint.latitude - previousPoint.latitude) * alpha,
            longitude = previousPoint.longitude + (candidatePoint.longitude - previousPoint.longitude) * alpha,
            timestampMillis = candidatePoint.timestampMillis,
            accuracyMeters = candidatePoint.accuracyMeters,
            altitudeMeters = candidatePoint.altitudeMeters?.let { candidateAltitude ->
                previousPoint.altitudeMeters?.let { previousAltitude ->
                    previousAltitude + (candidateAltitude - previousAltitude) * alpha
                }
            } ?: candidatePoint.altitudeMeters,
            wgs84Latitude = smoothedWgs84Latitude ?: candidatePoint.wgs84Latitude,
            wgs84Longitude = smoothedWgs84Longitude ?: candidatePoint.wgs84Longitude
        )
    }
}
