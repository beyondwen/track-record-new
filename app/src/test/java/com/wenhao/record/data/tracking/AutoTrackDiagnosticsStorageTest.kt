package com.wenhao.record.data.tracking

import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoTrackDiagnosticsStorageTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        context.getSharedPreferences("track_record_diagnostics", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("track_record_diagnostics", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    @Test
    fun `location decision updates do not trigger dashboard refresh notifications`() {
        val listener = CountingListener()
        TrackDataChangeNotifier.addListener(listener)

        try {
            AutoTrackDiagnosticsStorage.markLocationDecision(
                context = context,
                decision = "已接收：活跃采样，第 1 个点，精度 8m",
                acceptedPointCount = 1,
                accuracyMeters = 8f,
            )

            shadowOf(android.os.Looper.getMainLooper()).idle()

            assertEquals(0, listener.dashboardChangedCount)
            assertEquals(1, listener.diagnosticsChangedCount)
        } finally {
            TrackDataChangeNotifier.removeListener(listener)
        }
    }

    private class CountingListener : TrackDataChangeNotifier.Listener {
        var dashboardChangedCount = 0
        var diagnosticsChangedCount = 0

        override fun onDashboardDataChanged() {
            dashboardChangedCount += 1
        }

        override fun onDiagnosticsChanged() {
            diagnosticsChangedCount += 1
        }
    }
}
