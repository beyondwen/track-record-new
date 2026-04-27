package com.wenhao.record.ui.dashboard

data class DashboardOverlayUiState(
    val gpsLabel: String = "",
    val gpsTone: DashboardTone = DashboardTone.MUTED,
    val diagnosticsTitle: String = "",
    val diagnosticsCompactBody: String = "",
    val locateVisible: Boolean = true,
)
