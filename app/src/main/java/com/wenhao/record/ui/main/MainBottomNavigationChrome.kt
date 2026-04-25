package com.wenhao.record.ui.main

import com.wenhao.record.ui.designsystem.TrackBottomTab

data class MainBottomNavigationChrome(
    val selectedTab: TrackBottomTab,
    val recordEnabled: Boolean,
    val historyEnabled: Boolean,
    val settingsEnabled: Boolean,
)

fun buildMainBottomNavigationChrome(currentTab: MainTab): MainBottomNavigationChrome {
    return when (currentTab) {
        MainTab.RECORD -> MainBottomNavigationChrome(
            selectedTab = TrackBottomTab.RECORD,
            recordEnabled = false,
            historyEnabled = true,
            settingsEnabled = true,
        )

        MainTab.HISTORY -> MainBottomNavigationChrome(
            selectedTab = TrackBottomTab.HISTORY,
            recordEnabled = true,
            historyEnabled = false,
            settingsEnabled = true,
        )

        MainTab.ABOUT -> MainBottomNavigationChrome(
            selectedTab = TrackBottomTab.SETTINGS,
            recordEnabled = true,
            historyEnabled = true,
            settingsEnabled = false,
        )
    }
}
