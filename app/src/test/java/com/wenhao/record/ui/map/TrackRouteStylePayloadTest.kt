package com.wenhao.record.ui.map

import com.wenhao.record.map.GeoCoordinate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackRouteStylePayloadTest {

    @Test
    fun `groups polylines by color and width for mapbox style layers`() {
        val payload = TrackRouteStylePayload.fromPolylines(
            listOf(
                route("a", color = 0xff0066ff.toInt(), width = 5.0),
                route("b", color = 0xff0066ff.toInt(), width = 5.0),
                route("c", color = 0xffff8800.toInt(), width = 4.0),
            )
        )

        assertEquals(2, payload.groups.size)
        assertEquals(2, payload.groups.first().featureCollection.features()?.size)
        assertEquals(1, payload.groups.last().featureCollection.features()?.size)
        assertTrue(payload.groups.all { group -> group.layerId.startsWith("track-route-layer-") })
    }

    @Test
    fun `drops invalid single point polylines before building geojson`() {
        val payload = TrackRouteStylePayload.fromPolylines(
            listOf(
                TrackMapPolyline(
                    id = "single",
                    points = listOf(GeoCoordinate(latitude = 30.0, longitude = 104.0)),
                    colorArgb = 0xff0066ff.toInt(),
                    width = 5.0,
                ),
                route("valid", color = 0xff0066ff.toInt(), width = 5.0),
            )
        )

        assertEquals(1, payload.groups.size)
        assertEquals(1, payload.groups.single().featureCollection.features()?.size)
    }

    private fun route(id: String, color: Int, width: Double): TrackMapPolyline {
        return TrackMapPolyline(
            id = id,
            points = listOf(
                GeoCoordinate(latitude = 30.0, longitude = 104.0),
                GeoCoordinate(latitude = 30.001, longitude = 104.001),
            ),
            colorArgb = color,
            width = width,
        )
    }
}
