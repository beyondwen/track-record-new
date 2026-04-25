package com.wenhao.record.ui.main

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardOverviewSheetStateTest {

    @Test
    fun `downward drag past threshold collapses expanded sheet`() {
        assertFalse(
            resolveDashboardOverviewSheetExpanded(
                currentExpanded = true,
                totalDragDeltaPx = 72f,
            )
        )
    }

    @Test
    fun `upward drag past threshold expands collapsed sheet`() {
        assertTrue(
            resolveDashboardOverviewSheetExpanded(
                currentExpanded = false,
                totalDragDeltaPx = -72f,
            )
        )
    }

    @Test
    fun `small drag keeps current sheet state`() {
        assertTrue(
            resolveDashboardOverviewSheetExpanded(
                currentExpanded = true,
                totalDragDeltaPx = 18f,
            )
        )
        assertFalse(
            resolveDashboardOverviewSheetExpanded(
                currentExpanded = false,
                totalDragDeltaPx = -18f,
            )
        )
    }

    @Test
    fun `map interaction collapses expanded sheet`() {
        assertFalse(resolveDashboardOverviewSheetAfterMapInteraction())
    }
}
