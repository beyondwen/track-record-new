package com.wenhao.record.ui.main

private const val DASHBOARD_OVERVIEW_SHEET_DRAG_THRESHOLD_PX = 56f

fun resolveDashboardOverviewSheetExpanded(
    currentExpanded: Boolean,
    totalDragDeltaPx: Float,
    thresholdPx: Float = DASHBOARD_OVERVIEW_SHEET_DRAG_THRESHOLD_PX,
): Boolean {
    return when {
        totalDragDeltaPx <= -thresholdPx -> true
        totalDragDeltaPx >= thresholdPx -> false
        else -> currentExpanded
    }
}

fun resolveDashboardOverviewSheetAfterMapInteraction(): Boolean = false
