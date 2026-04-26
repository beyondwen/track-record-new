package com.wenhao.record.tracking

import android.location.Location
import com.wenhao.record.map.GeoMath

data class TrackingLowPowerSignalsSnapshot(
    val hasFreshLocation: Boolean,
    val hasMeaningfulDisplacement: Boolean,
    val shouldEnterSuspectMoving: Boolean,
)

object TrackingLowPowerSignals {
    private const val MAX_LOW_POWER_FIX_AGE_MS = 12_000L
    private const val MIN_SUSPECT_DISPLACEMENT_METERS = 20f
    private const val MAX_LOW_POWER_ACCURACY_METERS = 60f

    fun fromLocations(
        previous: Location?,
        current: Location?,
        nowMillis: Long,
    ): TrackingLowPowerSignalsSnapshot {
        if (current == null) {
            return TrackingLowPowerSignalsSnapshot(false, false, false)
        }

        val ageMs = nowMillis - current.time
        val accuracy = current.accuracy.takeIf { current.hasAccuracy() } ?: Float.MAX_VALUE
        val hasFreshLocation = current.time > 0L &&
            ageMs <= MAX_LOW_POWER_FIX_AGE_MS &&
            accuracy <= MAX_LOW_POWER_ACCURACY_METERS

        val hasMeaningfulDisplacement = if (previous == null) {
            false
        } else {
            GeoMath.distanceMeters(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude,
            ) >= MIN_SUSPECT_DISPLACEMENT_METERS
        }

        return TrackingLowPowerSignalsSnapshot(
            hasFreshLocation = hasFreshLocation,
            hasMeaningfulDisplacement = hasMeaningfulDisplacement,
            shouldEnterSuspectMoving = hasFreshLocation && hasMeaningfulDisplacement,
        )
    }
}
