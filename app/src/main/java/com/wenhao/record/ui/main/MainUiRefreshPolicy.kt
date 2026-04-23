package com.wenhao.record.ui.main

internal data class MainUiRefreshDecision(
    val warmUpHistory: Boolean = false,
    val reloadHistory: Boolean = false,
    val refreshDashboard: Boolean = false,
    val refitMap: Boolean = false,
)

internal object MainUiRefreshPolicy {

    fun forResume(currentTab: MainTab, skipContentRefresh: Boolean): MainUiRefreshDecision {
        if (skipContentRefresh) {
            return MainUiRefreshDecision(
                warmUpHistory = true,
            )
        }

        return when (currentTab) {
            MainTab.HISTORY -> MainUiRefreshDecision(
                warmUpHistory = true,
                reloadHistory = true,
            )

            MainTab.RECORD -> MainUiRefreshDecision(
                warmUpHistory = true,
                refreshDashboard = true,
            )

            MainTab.ABOUT -> MainUiRefreshDecision(
                warmUpHistory = true,
            )
        }
    }

    fun forHistoryChanged(currentTab: MainTab): MainUiRefreshDecision {
        return when (currentTab) {
            MainTab.HISTORY -> MainUiRefreshDecision(
                reloadHistory = true,
                refreshDashboard = true,
                refitMap = true,
            )

            MainTab.RECORD -> MainUiRefreshDecision(
                warmUpHistory = true,
                refreshDashboard = true,
                refitMap = true,
            )

            MainTab.ABOUT -> MainUiRefreshDecision(
                warmUpHistory = true,
            )
        }
    }

    fun forTabSelection(tab: MainTab): MainUiRefreshDecision {
        return when (tab) {
            MainTab.HISTORY -> MainUiRefreshDecision(
                reloadHistory = true,
            )

            MainTab.RECORD -> MainUiRefreshDecision(
                warmUpHistory = true,
            )

            MainTab.ABOUT -> MainUiRefreshDecision()
        }
    }
}
