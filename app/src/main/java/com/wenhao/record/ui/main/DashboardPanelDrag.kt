package com.wenhao.record.ui.main

internal data class DashboardPanelPresentation(
    val visualProgress: Float,
    val committedProgress: Float,
)

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

internal fun dragDashboardPanel(
    state: DashboardPanelPresentation,
    dragDeltaY: Float,
    panelHeightPx: Float,
): DashboardPanelPresentation {
    return state.copy(
        visualProgress = calculateDashboardPanelProgress(
            currentProgress = state.visualProgress,
            dragDeltaY = dragDeltaY,
            panelHeightPx = panelHeightPx,
        ),
    )
}

internal fun settleDashboardPanel(state: DashboardPanelPresentation): DashboardPanelPresentation {
    val target = if (state.visualProgress > 0.5f) 1f else 0f
    return DashboardPanelPresentation(
        visualProgress = target,
        committedProgress = target,
    )
}
