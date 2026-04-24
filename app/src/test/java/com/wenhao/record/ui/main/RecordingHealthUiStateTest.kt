package com.wenhao.record.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingHealthUiStateTest {

    @Test
    fun `missing location permission yields blocked status and repair action`() {
        val state = buildRecordingHealthUiState(
            RecordingHealthInputs(
                hasLocationPermission = false,
                hasActivityRecognitionPermission = true,
                hasBackgroundLocationPermission = true,
                hasNotificationPermission = true,
                ignoresBatteryOptimizations = true,
                trackingEnabled = false,
                trackingActive = false,
                diagnosticsStatus = "后台待命中",
                diagnosticsEvent = "后台采点已停止",
                latestPointTimestampMillis = null,
            )
        )

        assertEquals(RecordingHealthOverallStatus.BLOCKED, state.overallStatus)
        assertEquals(RecordingHealthAction.REQUEST_LOCATION_PERMISSION, state.primaryAction)
        assertEquals(
            "未授权",
            state.items.first { it.key == RecordingHealthItemKey.LOCATION }.statusText,
        )
    }

    @Test
    fun `battery optimization risk yields degraded status`() {
        val state = buildRecordingHealthUiState(
            RecordingHealthInputs(
                hasLocationPermission = true,
                hasActivityRecognitionPermission = true,
                hasBackgroundLocationPermission = true,
                hasNotificationPermission = true,
                ignoresBatteryOptimizations = false,
                trackingEnabled = false,
                trackingActive = false,
                diagnosticsStatus = "后台待命中",
                diagnosticsEvent = "后台采点已停止",
                latestPointTimestampMillis = null,
            )
        )

        assertEquals(RecordingHealthOverallStatus.DEGRADED, state.overallStatus)
        assertEquals(RecordingHealthAction.START_BACKGROUND_TRACKING, state.primaryAction)
    }

    @Test
    fun `tracking active yields diagnostics primary action`() {
        val state = buildRecordingHealthUiState(
            RecordingHealthInputs(
                hasLocationPermission = true,
                hasActivityRecognitionPermission = true,
                hasBackgroundLocationPermission = true,
                hasNotificationPermission = true,
                ignoresBatteryOptimizations = true,
                trackingEnabled = true,
                trackingActive = true,
                diagnosticsStatus = "记录中",
                diagnosticsEvent = "后台采点服务已保持运行",
                latestPointTimestampMillis = 1713897600000,
            )
        )

        assertEquals(RecordingHealthOverallStatus.READY, state.overallStatus)
        assertEquals(RecordingHealthAction.SHOW_DIAGNOSTICS, state.primaryAction)
        assertEquals("查看诊断", state.primaryActionText)
    }

    @Test
    fun `repair action prefers background location before notification and battery`() {
        val state = buildRecordingHealthUiState(
            RecordingHealthInputs(
                hasLocationPermission = true,
                hasActivityRecognitionPermission = true,
                hasBackgroundLocationPermission = false,
                hasNotificationPermission = false,
                ignoresBatteryOptimizations = false,
                trackingEnabled = false,
                trackingActive = false,
                diagnosticsStatus = "后台待命中",
                diagnosticsEvent = "后台采点已停止",
                latestPointTimestampMillis = null,
            )
        )

        assertEquals(
            RecordingHealthAction.OPEN_APP_SETTINGS_FOR_BACKGROUND_LOCATION,
            state.primaryAction,
        )
    }
}
