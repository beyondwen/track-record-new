package com.wenhao.record.ui.map

import com.wenhao.record.map.GeoCoordinate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackHeatmapBuilderTest {

    @Test
    fun `build returns single hotspot for single point`() {
        val point = GeoCoordinate(latitude = 30.2741, longitude = 120.1551)

        val result = TrackHeatmapBuilder.build(
            points = listOf(point),
            idPrefix = "single",
        )

        assertEquals(1, result.size)
        assertEquals(point, result.first().coordinate)
        assertEquals(1.0, result.first().intensity)
    }

    @Test
    fun `build aggregates nearby coordinates into denser hotspot`() {
        val result = TrackHeatmapBuilder.build(
            points = listOf(
                GeoCoordinate(30.27410, 120.15510),
                GeoCoordinate(30.27411, 120.15511),
                GeoCoordinate(30.27412, 120.15512),
                GeoCoordinate(30.27940, 120.16820),
            ),
            idPrefix = "cluster",
        )

        assertTrue(result.isNotEmpty())
        assertTrue(result.size < 4)
        assertTrue(result.maxOf { it.intensity } > result.minOf { it.intensity })
    }

    @Test
    fun `buildPath creates corridor hotspots between sparse route points`() {
        val result = TrackHeatmapBuilder.buildPath(
            segments = listOf(
                listOf(
                    GeoCoordinate(30.27410, 120.15510),
                    GeoCoordinate(30.27410, 120.15990),
                )
            ),
            idPrefix = "corridor",
            corridorStepMeters = 12.0,
        )

        assertTrue(result.size >= 2)
    }
}
