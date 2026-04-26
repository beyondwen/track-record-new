package com.wenhao.record.tracking

import android.location.Location
import android.location.LocationManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackingLowPowerSignalsTest {

    @Test
    fun `passive fresh fix with displacement marks suspect movement`() {
        val previous = Location(LocationManager.PASSIVE_PROVIDER).apply {
            latitude = 30.0
            longitude = 120.0
            time = 1_000L
            accuracy = 18f
        }
        val current = Location(LocationManager.PASSIVE_PROVIDER).apply {
            latitude = 30.0005
            longitude = 120.0005
            time = 8_000L
            accuracy = 16f
        }

        val snapshot = TrackingLowPowerSignals.fromLocations(previous = previous, current = current, nowMillis = 8_500L)

        assertTrue(snapshot.hasFreshLocation)
        assertTrue(snapshot.hasMeaningfulDisplacement)
        assertTrue(snapshot.shouldEnterSuspectMoving)
    }

    @Test
    fun `stale coarse fix does not trigger suspect movement`() {
        val current = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 30.0005
            longitude = 120.0005
            time = 1_000L
            accuracy = 90f
        }

        val snapshot = TrackingLowPowerSignals.fromLocations(previous = null, current = current, nowMillis = 30_000L)

        assertFalse(snapshot.hasFreshLocation)
        assertFalse(snapshot.shouldEnterSuspectMoving)
    }
}
