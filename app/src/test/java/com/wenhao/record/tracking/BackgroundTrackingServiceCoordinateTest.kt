package com.wenhao.record.tracking

import android.location.Location
import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundTrackingServiceCoordinateTest {

    @Test
    fun `location points keep system coordinates as wgs84`() {
        val service = BackgroundTrackingService()
        val location = Location("gps").apply {
            latitude = 30.635995
            longitude = 104.0260895
            time = 1_234_567L
            accuracy = 12f
            altitude = 501.0
        }

        val trackPoint = service.toTrackPointForTest(location)

        assertEquals(location.latitude, trackPoint.wgs84Latitude ?: Double.NaN, 1e-6)
        assertEquals(location.longitude, trackPoint.wgs84Longitude ?: Double.NaN, 1e-6)
    }

    private fun BackgroundTrackingService.toTrackPointForTest(location: Location): TrackPoint {
        val method = BackgroundTrackingService::class.java.getDeclaredMethod("toTrackPoint", Location::class.java)
        method.isAccessible = true
        return method.invoke(this, location) as TrackPoint
    }
}
