package com.wenhao.record.tracking

import android.location.Location
import android.location.LocationManager
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundTrackingServiceSignalLossTest {

    @Test
    fun `signal lost blocks persistence until good fixes recover`() {
        val service = Robolectric.buildService(BackgroundTrackingService::class.java).get()
        setField(service, "currentPhase", TrackingPhase.ACTIVE)
        setField(service, "signalLost", true)

        val blocked = invokeShouldPersist(
            service = service,
            point = TrackPoint(latitude = 30.0, longitude = 120.0, timestampMillis = 1_000L, accuracyMeters = 80f),
            goodFixReady = false,
        )
        val recovered = invokeShouldPersist(
            service = service,
            point = TrackPoint(latitude = 30.0002, longitude = 120.0002, timestampMillis = 6_000L, accuracyMeters = 10f),
            goodFixReady = true,
        )

        assertEquals(false, blocked)
        assertEquals(true, recovered)
    }

    @Test
    fun `active location update persists raw point when signal is healthy`() {
        clearDatabase()
        val service = Robolectric.buildService(BackgroundTrackingService::class.java).create().get()
        setField(service, "enabled", true)
        setField(service, "currentPhase", TrackingPhase.ACTIVE)
        setField(service, "signalLost", false)

        invokeHandleLocationUpdate(service, goodLocation())

        assertRawPointCountEventually(service, expected = 1)
    }


    @Test
    fun `signal lost recovers only after three good fixes`() {
        clearDatabase()
        val service = Robolectric.buildService(BackgroundTrackingService::class.java).create().get()
        val baseTime = System.currentTimeMillis()
        setField(service, "enabled", true)
        setField(service, "currentPhase", TrackingPhase.ACTIVE)
        setField(service, "signalLost", true)

        invokeHandleLocationUpdate(service, goodLocation(latitude = 30.0, longitude = 120.0, timestampMillis = baseTime))
        assertRawPointCountStays(service, expected = 0)

        invokeHandleLocationUpdate(service, goodLocation(latitude = 30.00012, longitude = 120.00012, timestampMillis = baseTime + 2_000L))
        assertRawPointCountStays(service, expected = 0)

        invokeHandleLocationUpdate(service, goodLocation(latitude = 30.00026, longitude = 120.00026, timestampMillis = baseTime + 4_000L))
        assertRawPointCountEventually(service, expected = 1)
    }

    private fun goodLocation(
        latitude: Double = 30.0,
        longitude: Double = 120.0,
        timestampMillis: Long = System.currentTimeMillis(),
    ): Location {
        return Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.time = timestampMillis
            this.accuracy = 10f
            this.speed = 1.2f
        }
    }

    private fun invokeShouldPersist(service: BackgroundTrackingService, point: TrackPoint, goodFixReady: Boolean): Boolean {
        val method = BackgroundTrackingService::class.java.getDeclaredMethod(
            "shouldPersistPoint",
            TrackPoint::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(service, point, goodFixReady) as Boolean
    }

    private fun invokeHandleLocationUpdate(service: BackgroundTrackingService, location: Location) {
        val method = BackgroundTrackingService::class.java.getDeclaredMethod("handleLocationUpdate", Location::class.java)
        method.isAccessible = true
        method.invoke(service, location)
    }

    private fun assertRawPointCountEventually(service: BackgroundTrackingService, expected: Int) {
        val deadline = System.currentTimeMillis() + 1_000L
        var lastCount = -1
        while (System.currentTimeMillis() < deadline) {
            lastCount = runBlocking {
                TrackDatabase.getInstance(service.applicationContext).continuousTrackDao().countRawPoints()
            }
            if (lastCount == expected) {
                return
            }
            Thread.sleep(20L)
        }
        assertEquals(expected, lastCount)
    }

    private fun assertRawPointCountStays(service: BackgroundTrackingService, expected: Int) {
        val deadline = System.currentTimeMillis() + 500L
        while (System.currentTimeMillis() < deadline) {
            val count = runBlocking {
                TrackDatabase.getInstance(service.applicationContext).continuousTrackDao().countRawPoints()
            }
            assertEquals(expected, count)
            Thread.sleep(20L)
        }
    }

    private fun clearDatabase() {
        TrackDatabase.closeInstance()
        RuntimeEnvironment.getApplication().deleteDatabase("track_record.db")
        TrackDatabase.closeInstance()
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        val field = BackgroundTrackingService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
