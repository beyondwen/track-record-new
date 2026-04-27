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
    fun `active phase listens gps and network`() {
        assertEquals(
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
            providersForPhase(TrackingPhase.ACTIVE),
        )
    }

    private fun providersForPhase(phase: TrackingPhase): List<String> {
        val method = BackgroundTrackingService::class.java.getDeclaredMethod(
            "providersForPhase",
            TrackingPhase::class.java,
        )
        method.isAccessible = true
        val result = method.invoke(BackgroundTrackingService(), phase) as List<*>
        return result.map { it as String }
    }
}
