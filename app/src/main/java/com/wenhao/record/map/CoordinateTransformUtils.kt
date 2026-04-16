package com.wenhao.record.map

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double
)

object CoordinateTransformUtils {
    private const val PI = 3.1415926535897932384626
    private const val AXIS = 6378245.0
    private const val EE = 0.00669342162296594323

    fun wgs84ToGcj02(latitude: Double, longitude: Double): GeoCoordinate {
        if (isOutOfChina(latitude, longitude)) {
            return GeoCoordinate(latitude, longitude)
        }

        var dLat = transformLatitude(longitude - 105.0, latitude - 35.0)
        var dLng = transformLongitude(longitude - 105.0, latitude - 35.0)
        val radLat = latitude / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((AXIS * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (AXIS / sqrtMagic * cos(radLat) * PI)
        return GeoCoordinate(
            latitude = latitude + dLat,
            longitude = longitude + dLng
        )
    }

    fun gcj02ToWgs84(latitude: Double, longitude: Double): GeoCoordinate {
        if (isOutOfChina(latitude, longitude)) {
            return GeoCoordinate(latitude, longitude)
        }

        var currentLatitude = latitude
        var currentLongitude = longitude
        repeat(6) {
            val gcj = wgs84ToGcj02(currentLatitude, currentLongitude)
            currentLatitude -= (gcj.latitude - latitude)
            currentLongitude -= (gcj.longitude - longitude)
        }
        return GeoCoordinate(
            latitude = currentLatitude,
            longitude = currentLongitude
        )
    }

    fun isOutOfChina(latitude: Double, longitude: Double): Boolean {
        return longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271
    }

    private fun transformLatitude(longitude: Double, latitude: Double): Double {
        var result = -100.0 + 2.0 * longitude + 3.0 * latitude + 0.2 * latitude * latitude
        result += 0.1 * longitude * latitude + 0.2 * sqrt(abs(longitude))
        result += (20.0 * sin(6.0 * longitude * PI) + 20.0 * sin(2.0 * longitude * PI)) * 2.0 / 3.0
        result += (20.0 * sin(latitude * PI) + 40.0 * sin(latitude / 3.0 * PI)) * 2.0 / 3.0
        result += (160.0 * sin(latitude / 12.0 * PI) + 320 * sin(latitude * PI / 30.0)) * 2.0 / 3.0
        return result
    }

    private fun transformLongitude(longitude: Double, latitude: Double): Double {
        var result = 300.0 + longitude + 2.0 * latitude + 0.1 * longitude * longitude
        result += 0.1 * longitude * latitude + 0.1 * sqrt(abs(longitude))
        result += (20.0 * sin(6.0 * longitude * PI) + 20.0 * sin(2.0 * longitude * PI)) * 2.0 / 3.0
        result += (20.0 * sin(longitude * PI) + 40.0 * sin(longitude / 3.0 * PI)) * 2.0 / 3.0
        result += (150.0 * sin(longitude / 12.0 * PI) + 300.0 * sin(longitude / 30.0 * PI)) * 2.0 / 3.0
        return result
    }
}
