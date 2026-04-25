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
        assertTrue(controller.renderState.markers.isEmpty())
    }

    @Test
    fun `idle preview location uses map puck instead of a dedicated marker`() {
        val controller = HomeMapController()

        controller.render(
            runtimeSnapshot = null,
            previewLocation = GeoCoordinate(37.0, -122.0),
            todayHistoryItems = emptyList(),
            activeSessionPoints = emptyList(),
        )

        assertTrue(controller.renderState.markers.isEmpty())
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


    @Test
    fun `render sanitizes active session spike before drawing polyline`() {
        val controller = HomeMapController()
        val points = listOf(
            point(latitude = 30.0, longitude = 120.0, timestampMillis = now, accuracyMeters = 8f),
            point(latitude = 30.0011, longitude = 120.0011, timestampMillis = now + 10_000L, accuracyMeters = 55f),
            point(latitude = 30.00002, longitude = 120.00002, timestampMillis = now + 20_000L, accuracyMeters = 8f),
        )

        controller.render(
            runtimeSnapshot = trackingSnapshot(
                phase = TrackingPhase.ACTIVE,
                latestPoint = points.last(),
            ),
            previewLocation = GeoCoordinate(30.0, 120.0),
            todayHistoryItems = emptyList(),
            activeSessionPoints = points,
        )

        val renderedPoints = controller.renderState.polylines
            .filter { it.id.startsWith("active-") }
            .flatMap { it.points }
        assertTrue(renderedPoints.isNotEmpty())
        assertFalse(renderedPoints.any { it.latitude == 30.0011 && it.longitude == 120.0011 })
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
        accuracyMeters: Float? = null,
    ) = TrackPoint(
        latitude = latitude,
        longitude = longitude,
        timestampMillis = timestampMillis,
        accuracyMeters = accuracyMeters,
    )
}
