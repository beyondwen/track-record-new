package com.wenhao.record.tracking

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager

object LocationSelectionUtils {

    @SuppressLint("MissingPermission")
    fun loadBestLastKnownLocation(
        locationManager: LocationManager,
        hasFineLocation: Boolean,
        maxAgeMs: Long
    ): Location? {
        val now = System.currentTimeMillis()
        val candidates = buildList {
            if (hasFineLocation && locationManager.isProviderEnabledSafe(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::add)
            }
            if (locationManager.isProviderEnabledSafe(LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let(::add)
            }
            if (locationManager.isProviderEnabledSafe(LocationManager.PASSIVE_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)?.let(::add)
            }
        }.filter { location ->
            val timestamp = location.time.takeIf { it > 0L } ?: now
            now - timestamp <= maxAgeMs
        }

        return candidates.maxByOrNull { location -> scoreLocation(location, now) }
    }

    private fun scoreLocation(location: Location, now: Long): Int {
        val ageMs = now - (location.time.takeIf { it > 0L } ?: now)
        val freshnessScore = (10_000 - (ageMs / 1000L)).toInt()
        val accuracyPenalty = ((location.accuracy.takeIf { location.hasAccuracy() } ?: 80f) * 10f).toInt()
        val providerBonus = when (location.provider) {
            LocationManager.GPS_PROVIDER -> 220
            LocationManager.NETWORK_PROVIDER -> 120
            else -> 60
        }
        val speedBonus = if (location.hasSpeed()) (location.speed * 20f).toInt() else 0
        return freshnessScore + providerBonus + speedBonus - accuracyPenalty
    }

    private fun LocationManager.isProviderEnabledSafe(provider: String): Boolean {
        return runCatching { isProviderEnabled(provider) }.getOrDefault(false)
    }
}
