package com.wenhao.record.tracking

import android.location.Location
import android.location.LocationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationSelectionUtilsTest {

    @Test
    fun `prefers fresher gps fix over stale coarse cache`() {
        val context = RuntimeEnvironment.getApplication()
        val locationManager = context.getSystemService(LocationManager::class.java)
        val shadowLocationManager = shadowOf(locationManager) as ShadowLocationManager
        val now = System.currentTimeMillis()

        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

        shadowLocationManager.setLastKnownLocation(
            LocationManager.NETWORK_PROVIDER,
            location(
                provider = LocationManager.NETWORK_PROVIDER,
                latitude = 30.0,
                longitude = 120.0,
                timestampMillis = now - 2_000L,
                accuracyMeters = 80f,
            ),
        )
        shadowLocationManager.setLastKnownLocation(
            LocationManager.GPS_PROVIDER,
            location(
                provider = LocationManager.GPS_PROVIDER,
                latitude = 30.0001,
                longitude = 120.0001,
                timestampMillis = now - 6_000L,
                accuracyMeters = 12f,
            ),
        )

        val selected = LocationSelectionUtils.loadBestLastKnownLocation(
            locationManager = locationManager,
            hasFineLocation = true,
            maxAgeMs = 15_000L,
        )

        assertEquals(LocationManager.GPS_PROVIDER, selected?.provider)
    }

    private fun location(
        provider: String,
        latitude: Double,
        longitude: Double,
        timestampMillis: Long,
        accuracyMeters: Float,
    ): Location {
        return Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.time = timestampMillis
            this.accuracy = accuracyMeters
        }
    }
}
