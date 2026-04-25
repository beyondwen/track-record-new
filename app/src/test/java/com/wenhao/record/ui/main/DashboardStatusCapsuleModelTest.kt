package com.wenhao.record.ui.main

import com.wenhao.record.ui.dashboard.DashboardScreenUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardStatusCapsuleModelTest {

    @Test
    fun `capsule uses current recording status and dashboard metrics`() {
        val model = buildDashboardStatusCapsuleModel(
            dashboardState = DashboardScreenUiState(
                distanceText = "1.23",
                durationText = "12:34",
                speedText = "5.6 km/h",
                autoTrackTitle = "正在记录这段移动",
                autoTrackMeta = "后台持续记录中",
                statusLabel = "记录中",
            ),
            recordingHealthState = RecordingHealthUiState.EMPTY.copy(
                overallStatus = RecordingHealthOverallStatus.READY,
                summaryText = "记录链路正常",
            ),
        )

        assertEquals("记录中", model.statusText)
        assertEquals("正在记录这段移动", model.titleText)
        assertEquals("后台持续记录中", model.summaryText)
        assertEquals("1.23", model.distanceText)
        assertEquals("12:34", model.durationText)
        assertEquals("5.6", model.speedText)
        assertEquals(RecordingHealthOverallStatus.READY, model.healthStatus)
    }

    @Test
    fun `capsule falls back to title when status is blank`() {
        val model = buildDashboardStatusCapsuleModel(
            dashboardState = DashboardScreenUiState(
                autoTrackTitle = "准备记录下一段移动",
                autoTrackMeta = "检测到明显移动后会自动开始记录",
                statusLabel = "",
            ),
            recordingHealthState = RecordingHealthUiState.EMPTY,
        )

        assertEquals("准备记录下一段移动", model.statusText)
    }
}
