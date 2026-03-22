package com.example.helloworld.data.tracking

import com.amap.api.maps.model.LatLng

data class TrackPoint(
    val latitude: Double,
    val longitude: Double
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}
