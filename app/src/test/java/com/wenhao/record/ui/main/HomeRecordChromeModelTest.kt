package com.wenhao.record.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRecordChromeModelTest {

    @Test
    fun `home chrome picks single blocking spotlight item before warnings`() {
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
            RecordingHealthItemKey.BACKGROUND_LOCATION,
            buildHomeRecordChromeModel(state).spotlightItem?.key,
        )
    }
}
