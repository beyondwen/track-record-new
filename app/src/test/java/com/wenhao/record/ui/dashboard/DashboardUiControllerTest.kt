package com.wenhao.record.ui.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardUiControllerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `render uses a single human centered tracking message on the homepage`() {
        val controller = DashboardUiController(context)

        controller.render(
            runtimeSnapshot = null,
            state = AutoTrackUiState.TRACKING,
            todayHistoryItems = emptyList(),
        )

        assertEquals("正在记录这段移动", controller.panelState.autoTrackTitle)
        assertEquals("静止后会自动完成分段并写入历史。", controller.panelState.autoTrackMeta)
        assertEquals("记录中", controller.panelState.statusLabel)
    }

    @Test
    fun `render uses sanitized track distance instead of stale stored distance`() {
        val controller = DashboardUiController(context)

        controller.render(
            runtimeSnapshot = null,
            state = AutoTrackUiState.IDLE,
            todayHistoryItems = listOf(
                HistoryItem(
                    id = 1L,
                    timestamp = 1713420000000,
                    distanceKm = 99.0,
                    durationSeconds = 300,
                    averageSpeedKmh = 1188.0,
                    points = listOf(
                        TrackPoint(latitude = 30.0, longitude = 120.0, timestampMillis = 1713420000000),
                        TrackPoint(latitude = 30.001, longitude = 120.0, timestampMillis = 1713420060000),
                    ),
                )
            ),
        )

        assertEquals("0.11", controller.panelState.distanceText)
        assertNotEquals("99.00", controller.panelState.distanceText)
    }
}
