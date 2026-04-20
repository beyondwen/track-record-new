package com.wenhao.record.ui.main

import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.tracking.SamplingTier
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.map.GeoCoordinate
import com.wenhao.record.tracking.TrackingPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeMapControllerTest {

    private val now = System.currentTimeMillis()

    @Test
    fun `render draws active session polyline while tracking`() {
        val controller = HomeMapController()

        controller.render(
            runtimeSnapshot = trackingSnapshot(
                phase = TrackingPhase.ACTIVE,
                latestPoint = point(
                    latitude = 37.002,
                    longitude = -122.002,
                    timestampMillis = now + 20_000L,
                ),
            ),
            previewLocation = GeoCoordinate(37.0, -122.0),
            todayHistoryItems = emptyList(),
            activeSessionPoints = listOf(
                point(latitude = 37.0, longitude = -122.0, timestampMillis = now),
                point(latitude = 37.001, longitude = -122.001, timestampMillis = now + 10_000L),
                point(latitude = 37.002, longitude = -122.002, timestampMillis = now + 20_000L),
            ),
        )

        assertTrue(controller.hasActiveTrack())
        assertTrue(controller.renderState.polylines.any { it.id.startsWith("active-") })
        assertEquals(2, controller.renderState.markers.size)
    }

    @Test
    fun `render clears active session polyline after track is saved into history`() {
        val controller = HomeMapController()
        val savedPoints = listOf(
            point(latitude = 37.0, longitude = -122.0, timestampMillis = now),
            point(latitude = 37.001, longitude = -122.001, timestampMillis = now + 10_000L),
            point(latitude = 37.002, longitude = -122.002, timestampMillis = now + 20_000L),
        )

        controller.render(
            runtimeSnapshot = trackingSnapshot(
                phase = TrackingPhase.ACTIVE,
                latestPoint = savedPoints.last(),
            ),
            previewLocation = GeoCoordinate(37.0, -122.0),
            todayHistoryItems = emptyList(),
            activeSessionPoints = savedPoints,
        )

        controller.render(
            runtimeSnapshot = trackingSnapshot(
                phase = TrackingPhase.IDLE,
                latestPoint = savedPoints.last(),
            ),
            previewLocation = GeoCoordinate(37.0, -122.0),
            todayHistoryItems = listOf(
                HistoryItem(
                    id = 1L,
                    timestamp = now,
                    distanceKm = 1.2,
                    durationSeconds = 420,
                    averageSpeedKmh = 10.3,
                    points = savedPoints,
                )
            ),
            activeSessionPoints = savedPoints,
        )

        assertFalse(controller.renderState.polylines.any { it.id.startsWith("active-") })
        assertTrue(controller.renderState.polylines.any { it.id.startsWith("today-") })
    }

    private fun trackingSnapshot(
        phase: TrackingPhase,
        latestPoint: TrackPoint?,
    ) = TrackingRuntimeSnapshot(
        isEnabled = true,
        phase = phase,
        samplingTier = SamplingTier.ACTIVE,
        latestPoint = latestPoint,
        lastAnalysisAt = null,
    )

    private fun point(
        latitude: Double,
        longitude: Double,
        timestampMillis: Long,
    ) = TrackPoint(
        latitude = latitude,
        longitude = longitude,
        timestampMillis = timestampMillis,
    )
}
