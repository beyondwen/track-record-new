package com.wenhao.record.data.tracking

import com.wenhao.record.map.GeoCoordinate

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long = 0L,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val wgs84Latitude: Double? = null,
    val wgs84Longitude: Double? = null
) {
    fun toGeoCoordinate(): GeoCoordinate {
        val directWgs84 = if (wgs84Latitude != null && wgs84Longitude != null) {
            GeoCoordinate(latitude = wgs84Latitude, longitude = wgs84Longitude)
        } else {
            null
        }
        if (directWgs84 != null) return directWgs84

        return GeoCoordinate(latitude = latitude, longitude = longitude)
    }

    fun getLatitudeForDistance(): Double = wgs84Latitude ?: latitude
    fun getLongitudeForDistance(): Double = wgs84Longitude ?: longitude
}
