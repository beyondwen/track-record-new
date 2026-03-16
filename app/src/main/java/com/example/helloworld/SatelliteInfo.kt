package com.example.helloworld

data class SatelliteInfo(
    val svid: Int,
    val constellationType: Int,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean
) {
    val constellationName: String
        get() = when (constellationType) {
            android.location.GnssStatus.CONSTELLATION_GPS -> "GPS"
            android.location.GnssStatus.CONSTELLATION_SBAS -> "SBAS"
            android.location.GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
            android.location.GnssStatus.CONSTELLATION_QZSS -> "QZSS"
            android.location.GnssStatus.CONSTELLATION_BEIDOU -> "北斗"
            android.location.GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
            android.location.GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
            else -> "未知"
        }
}