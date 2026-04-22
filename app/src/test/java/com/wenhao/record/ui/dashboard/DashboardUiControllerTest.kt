package com.wenhao.record.ui.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.tracking.AutoTrackUiState
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
