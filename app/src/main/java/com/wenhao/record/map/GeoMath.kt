package com.wenhao.record.map

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(first: TrackPoint, second: TrackPoint): Float {
        return distanceMeters(
            first.getLatitudeForDistance(),
            first.getLongitudeForDistance(),
            second.getLatitudeForDistance(),
            second.getLongitudeForDistance()
        )
    }

    fun distanceMeters(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double
    ): Float {
        val lat1 = Math.toRadians(firstLatitude)
        val lat2 = Math.toRadians(secondLatitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(secondLongitude - firstLongitude)

        val sinDLat2 = sin(dLat / 2)
        val sinDLon2 = sin(dLon / 2)
        val haversine = sinDLat2 * sinDLat2 +
            cos(lat1) * cos(lat2) * sinDLon2 * sinDLon2
        val arc = 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return (EARTH_RADIUS_METERS * arc).toFloat()
    }
}
