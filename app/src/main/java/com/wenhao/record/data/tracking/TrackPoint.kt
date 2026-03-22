package com.wenhao.record.data.tracking

import com.amap.api.maps.model.LatLng

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long = 0L,
    val accuracyMeters: Float? = null
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}
