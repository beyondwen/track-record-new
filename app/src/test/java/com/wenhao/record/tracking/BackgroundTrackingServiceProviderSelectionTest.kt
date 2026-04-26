package com.wenhao.record.tracking

import android.location.LocationManager
import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundTrackingServiceProviderSelectionTest {

    @Test
    fun `idle phase listens passive only`() {
        assertEquals(
            listOf(LocationManager.PASSIVE_PROVIDER),
            providersForPhase(TrackingPhase.IDLE),
        )
    }

    @Test
    fun `suspect moving listens passive and network`() {
        assertEquals(
            listOf(LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER),
            providersForPhase(TrackingPhase.SUSPECT_MOVING),
        )
    }

    @Test
    fun `active phase listens gps and network`() {
        assertEquals(
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
            providersForPhase(TrackingPhase.ACTIVE),
        )
    }

    @Test
    fun `suspect stopping listens passive and network`() {
        assertEquals(
            listOf(LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER),
            providersForPhase(TrackingPhase.SUSPECT_STOPPING),
        )
    }

    private fun providersForPhase(phase: TrackingPhase): List<String> {
        val method = BackgroundTrackingService::class.java.getDeclaredMethod(
            "providersForPhase",
            TrackingPhase::class.java,
        )
        method.isAccessible = true
        return method.invoke(BackgroundTrackingService(), phase) as List<String>
    }
}
