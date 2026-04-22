package com.wenhao.record.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardPanelDragTest {

    @Test
    fun `dragging down from expanded collapses the dashboard panel`() {
        assertEquals(
            expected = 0.75f,
            actual = calculateDashboardPanelProgress(
                currentProgress = 1f,
                dragDeltaY = 80f,
                panelHeightPx = 320f,
            ),
        )
    }

    @Test
    fun `dragging up from collapsed expands the dashboard panel`() {
        assertEquals(
            expected = 0.25f,
            actual = calculateDashboardPanelProgress(
                currentProgress = 0f,
                dragDeltaY = -80f,
                panelHeightPx = 320f,
            ),
        )
    }

    @Test
    fun `dragging is clamped within collapsed and expanded bounds`() {
        assertEquals(
            expected = 0f,
            actual = calculateDashboardPanelProgress(
                currentProgress = 0.2f,
                dragDeltaY = 999f,
                panelHeightPx = 320f,
            ),
        )
        assertEquals(
            expected = 1f,
            actual = calculateDashboardPanelProgress(
                currentProgress = 0.8f,
                dragDeltaY = -999f,
                panelHeightPx = 320f,
            ),
        )
    }

    @Test
    fun `dragging only updates visual progress until panel is settled`() {
        val dragged = dragDashboardPanel(
            state = DashboardPanelPresentation(
                visualProgress = 1f,
                committedProgress = 1f,
            ),
            dragDeltaY = 80f,
            panelHeightPx = 320f,
        )

        assertEquals(
            DashboardPanelPresentation(
                visualProgress = 0.75f,
                committedProgress = 1f,
            ),
            dragged,
        )
    }

    @Test
    fun `settling commits the nearest panel state for map padding updates`() {
        val settled = settleDashboardPanel(
            DashboardPanelPresentation(
                visualProgress = 0.2f,
                committedProgress = 1f,
            ),
        )

        assertEquals(
            DashboardPanelPresentation(
                visualProgress = 0f,
                committedProgress = 0f,
            ),
            settled,
        )
    }
}
