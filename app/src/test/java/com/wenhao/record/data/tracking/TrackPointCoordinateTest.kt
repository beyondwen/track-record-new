package com.wenhao.record.data.tracking

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackPointCoordinateTest {

    @Test
    fun `toGeoCoordinate falls back to raw coordinates when wgs84 is absent`() {
        val point = TrackPoint(
            latitude = 30.6803975,
            longitude = 103.9849013,
        )

        val coordinate = point.toGeoCoordinate()

        assertEquals(point.latitude, coordinate.latitude, 1e-6)
        assertEquals(point.longitude, coordinate.longitude, 1e-6)
    }
}
