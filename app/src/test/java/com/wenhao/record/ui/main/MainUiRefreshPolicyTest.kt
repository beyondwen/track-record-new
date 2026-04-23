package com.wenhao.record.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainUiRefreshPolicyTest {

    @Test
    fun `first resume on record tab only warms history cache`() {
        val decision = MainUiRefreshPolicy.forResume(
            currentTab = MainTab.RECORD,
            skipContentRefresh = true,
        )

        assertEquals(
            MainUiRefreshDecision(
                warmUpHistory = true,
            ),
            decision,
        )
    }

    @Test
    fun `resume on history tab refreshes history instead of dashboard`() {
        val decision = MainUiRefreshPolicy.forResume(
            currentTab = MainTab.HISTORY,
            skipContentRefresh = false,
        )

        assertEquals(
            MainUiRefreshDecision(
                warmUpHistory = true,
                reloadHistory = true,
            ),
            decision,
        )
    }

    @Test
    fun `resume on record tab refreshes dashboard without forcing map refit`() {
        val decision = MainUiRefreshPolicy.forResume(
            currentTab = MainTab.RECORD,
            skipContentRefresh = false,
        )

        assertEquals(
            MainUiRefreshDecision(
                warmUpHistory = true,
                refreshDashboard = true,
            ),
            decision,
        )
    }

    @Test
    fun `history change on record tab refreshes dashboard without forcing history reload`() {
        val decision = MainUiRefreshPolicy.forHistoryChanged(MainTab.RECORD)

        assertEquals(
            MainUiRefreshDecision(
                warmUpHistory = true,
                refreshDashboard = true,
                refitMap = true,
            ),
            decision,
        )
    }

    @Test
    fun `switching to history tab reloads history immediately`() {
        val decision = MainUiRefreshPolicy.forTabSelection(MainTab.HISTORY)

        assertEquals(
            MainUiRefreshDecision(
                reloadHistory = true,
            ),
            decision,
        )
    }

    @Test
    fun `switching back to record tab keeps the existing map viewport stable`() {
        val decision = MainUiRefreshPolicy.forTabSelection(MainTab.RECORD)

        assertEquals(
            MainUiRefreshDecision(
                warmUpHistory = true,
            ),
            decision,
        )
    }
}
