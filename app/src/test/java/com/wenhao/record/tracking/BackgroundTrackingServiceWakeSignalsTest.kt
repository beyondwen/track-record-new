package com.wenhao.record.tracking

import android.location.Location
import android.location.LocationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundTrackingServiceWakeSignalsTest {

    @Test
    fun `fresh passive displacement above low power threshold enters suspect moving`() {
        val service = Robolectric.buildService(BackgroundTrackingService::class.java).create().get()
        setField(service, "enabled", true)
        setField(service, "currentPhase", TrackingPhase.IDLE)

        invokeHandleLocationUpdate(
            service,
            passiveLocation(
                latitude = 30.0,
                longitude = 120.0,
                timestampMillis = 1_713_420_000_000L,
                accuracyMeters = 18f,
            ),
        )
        invokeHandleLocationUpdate(
            service,
            passiveLocation(
                latitude = 30.00014,
                longitude = 120.00014,
                timestampMillis = System.currentTimeMillis() - 2_000L,
                accuracyMeters = 16f,
            ),
        )

        assertEquals(TrackingPhase.SUSPECT_MOVING, currentPhase(service))
    }

    @Test
    fun `recent motion evidence can wake suspect moving without passive displacement`() {
        val service = Robolectric.buildService(BackgroundTrackingService::class.java).create().get()
        setField(service, "enabled", true)
        setField(service, "currentPhase", TrackingPhase.IDLE)
        val engine = getField(service, "motionConfidenceEngine") as MotionConfidenceEngine
        val now = System.currentTimeMillis()
        engine.noteAccelerationVariance(1.2f, now - 1_000L)
        engine.noteStepCount(10f, now - 3_000L)
        engine.noteStepCount(15f, now - 500L)

        invokeHandleLocationUpdate(
            service,
            passiveLocation(
                latitude = 30.0,
                longitude = 120.0,
                timestampMillis = now - 1_000L,
                accuracyMeters = 18f,
            ),
        )

        assertEquals(TrackingPhase.SUSPECT_MOVING, currentPhase(service))
    }

    private fun passiveLocation(
        latitude: Double,
        longitude: Double,
        timestampMillis: Long,
        accuracyMeters: Float,
    ): Location {
        return Location(LocationManager.PASSIVE_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.time = timestampMillis
            this.accuracy = accuracyMeters
        }
    }

    private fun invokeHandleLocationUpdate(service: BackgroundTrackingService, location: Location) {
        val method = BackgroundTrackingService::class.java.getDeclaredMethod("handleLocationUpdate", Location::class.java)
        method.isAccessible = true
        method.invoke(service, location)
    }

    private fun currentPhase(service: BackgroundTrackingService): TrackingPhase {
        val field = BackgroundTrackingService::class.java.getDeclaredField("currentPhase")
        field.isAccessible = true
        return field.get(service) as TrackingPhase
    }

    private fun getField(target: Any, fieldName: String): Any? {
        val field = BackgroundTrackingService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target)
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        val field = BackgroundTrackingService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
