package com.wenhao.record.data.tracking

import com.baidu.mapapi.model.LatLng

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long = 0L,
    val accuracyMeters: Float? = null,
    val wgs84Latitude: Double? = null,
    val wgs84Longitude: Double? = null
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)

    fun getLatitudeForDistance(): Double = wgs84Latitude ?: latitude
    fun getLongitudeForDistance(): Double = wgs84Longitude ?: longitude
}
