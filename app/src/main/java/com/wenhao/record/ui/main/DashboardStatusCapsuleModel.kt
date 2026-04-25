package com.wenhao.record.ui.main

import com.wenhao.record.ui.dashboard.DashboardScreenUiState

data class DashboardStatusCapsuleModel(
    val statusText: String,
    val titleText: String,
    val summaryText: String,
    val distanceText: String,
    val durationText: String,
    val speedText: String,
    val healthStatus: RecordingHealthOverallStatus,
)

fun buildDashboardStatusCapsuleModel(
    dashboardState: DashboardScreenUiState,
    recordingHealthState: RecordingHealthUiState,
): DashboardStatusCapsuleModel {
    val title = dashboardState.autoTrackTitle.ifBlank { "记录状态" }
    return DashboardStatusCapsuleModel(
        statusText = dashboardState.statusLabel.ifBlank { title },
        titleText = title,
        summaryText = dashboardState.autoTrackMeta.ifBlank { recordingHealthState.summaryText },
        distanceText = dashboardState.distanceText,
        durationText = dashboardState.durationText,
        speedText = dashboardState.speedText.substringBefore(" ").ifBlank { dashboardState.speedText },
        healthStatus = recordingHealthState.overallStatus,
    )
}
