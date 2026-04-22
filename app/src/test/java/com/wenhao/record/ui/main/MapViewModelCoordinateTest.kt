package com.wenhao.record.ui.main

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.ui.map.TrackMapViewportRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapViewModelCoordinateTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `render fits today tracks using normalized map coordinates`() {
        val viewModel = MapViewModel(application, SavedStateHandle())
        val historyPointA = TrackPoint(
            latitude = 30.635995,
            longitude = 104.0260895,
            timestampMillis = 1_000L,
            wgs84Latitude = 30.63851753684334,
            wgs84Longitude = 104.02369844431439,
        )
        val historyPointB = TrackPoint(
            latitude = 30.636595,
            longitude = 104.0266895,
            timestampMillis = 2_000L,
            wgs84Latitude = 30.63911753684334,
            wgs84Longitude = 104.02429844431439,
        )

        viewModel.render(
            runtimeSnapshot = null,
            previewLocation = null,
            todayHistoryItems = listOf(
                HistoryItem(
                    id = 1L,
                    timestamp = System.currentTimeMillis(),
                    distanceKm = 0.5,
                    durationSeconds = 60,
                    averageSpeedKmh = 30.0,
                    points = listOf(historyPointA, historyPointB),
                )
            ),
            activeSessionPoints = emptyList(),
        )

        waitUntil(timeoutMs = 2_000L) {
            viewModel.renderState.value.viewportRequest is TrackMapViewportRequest.Fit
        }

        val viewportRequest = viewModel.renderState.value.viewportRequest
        assertTrue(viewportRequest is TrackMapViewportRequest.Fit)
        assertEquals(historyPointA.wgs84Latitude ?: Double.NaN, viewportRequest.coordinates.first().latitude, 1e-6)
        assertEquals(historyPointA.wgs84Longitude ?: Double.NaN, viewportRequest.coordinates.first().longitude, 1e-6)
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        check(condition()) { "Condition was not met within ${timeoutMs}ms" }
    }
}
