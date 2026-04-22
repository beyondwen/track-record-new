package com.wenhao.record.ui.main

internal fun calculateDashboardPanelProgress(
    currentProgress: Float,
    dragDeltaY: Float,
    panelHeightPx: Float,
): Float {
    if (panelHeightPx <= 0f) {
        return currentProgress.coerceIn(0f, 1f)
    }

    val clampedProgress = currentProgress.coerceIn(0f, 1f)
    val currentOffset = panelHeightPx * (1f - clampedProgress)
    val newOffset = (currentOffset + dragDeltaY).coerceIn(0f, panelHeightPx)
    return 1f - (newOffset / panelHeightPx)
}
