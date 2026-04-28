package com.wenhao.record.ui.main

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.tracking.SamplingTier
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.map.GeoCoordinate
import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.ui.map.TrackMapViewportRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapViewModelCoordinateTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `render centers active track using normalized latest coordinate`() {
        val viewModel = MapViewModel(application, SavedStateHandle())
        val activePointA = TrackPoint(
            latitude = 30.635995,
            longitude = 104.0260895,
            timestampMillis = 1_000L,
            wgs84Latitude = 30.63851753684334,
            wgs84Longitude = 104.02369844431439,
        )
        val activePointB = TrackPoint(
            latitude = 30.636595,
            longitude = 104.0266895,
            timestampMillis = 2_000L,
            wgs84Latitude = 30.63911753684334,
            wgs84Longitude = 104.02429844431439,
        )

        viewModel.render(
            runtimeSnapshot = activeSnapshot(activePointB),
            previewLocation = null,
            activeSessionPoints = listOf(activePointA, activePointB),
        )

        waitUntil(timeoutMs = 2_000L) {
            viewModel.renderState.value.viewportRequest is TrackMapViewportRequest.Center
        }

        val viewportRequest = viewModel.renderState.value.viewportRequest
        assertTrue(viewportRequest is TrackMapViewportRequest.Center)
        assertEquals(activePointB.wgs84Latitude ?: Double.NaN, viewportRequest.coordinate.latitude, 1e-6)
        assertEquals(activePointB.wgs84Longitude ?: Double.NaN, viewportRequest.coordinate.longitude, 1e-6)
    }

    @Test
    fun `render compacts active route overlays to keep map tab responsive`() {
        val viewModel = MapViewModel(application, SavedStateHandle())
        val now = System.currentTimeMillis()
        val points = List(600) { index ->
            TrackPoint(
                latitude = 30.0 + index * 0.00001,
                longitude = 104.0 + index * 0.00001,
                timestampMillis = now + index * 1_000L,
            )
        }

        viewModel.render(
            runtimeSnapshot = activeSnapshot(points.last()),
            previewLocation = null,
            activeSessionPoints = points,
        )

        waitUntil(timeoutMs = 2_000L) {
            viewModel.renderState.value.polylines.isNotEmpty()
        }

        assertTrue(viewModel.renderState.value.polylines.sumOf { it.points.size } <= 240)
    }

    @Test
    fun `tiny non centered location movement is ignored to avoid puck jitter`() {
        val viewModel = MapViewModel(application, SavedStateHandle())
        val first = GeoCoordinate(latitude = 30.0, longitude = 104.0)

        viewModel.updateCurrentLocation(first, shouldCenter = false)
        val initialState = viewModel.renderState.value

        viewModel.updateCurrentLocation(
            GeoCoordinate(latitude = 30.000005, longitude = 104.000005),
            shouldCenter = false,
        )

        assertEquals(initialState, viewModel.renderState.value)
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
        check(condition()) { "Condition was not met within ${timeoutMs}ms" }
    }

    private fun activeSnapshot(latestPoint: TrackPoint): TrackingRuntimeSnapshot {
        return TrackingRuntimeSnapshot(
            isEnabled = true,
            phase = TrackingPhase.ACTIVE,
            samplingTier = SamplingTier.ACTIVE,
            latestPoint = latestPoint,
            lastAnalysisAt = null,
        )
    }
}
