package com.wenhao.record.ui.main

import com.wenhao.record.ui.designsystem.TrackBottomTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainBottomNavigationChromeTest {

    @Test
    fun `record tab selects record and disables current destination`() {
        val chrome = buildMainBottomNavigationChrome(MainTab.RECORD)

        assertEquals(TrackBottomTab.RECORD, chrome.selectedTab)
        assertFalse(chrome.recordEnabled)
        assertTrue(chrome.historyEnabled)
        assertTrue(chrome.settingsEnabled)
    }

    @Test
    fun `history tab selects history and disables current destination`() {
        val chrome = buildMainBottomNavigationChrome(MainTab.HISTORY)

        assertEquals(TrackBottomTab.HISTORY, chrome.selectedTab)
        assertTrue(chrome.recordEnabled)
        assertFalse(chrome.historyEnabled)
        assertTrue(chrome.settingsEnabled)
    }

    @Test
    fun `about tab selects settings and disables current destination`() {
        val chrome = buildMainBottomNavigationChrome(MainTab.ABOUT)

        assertEquals(TrackBottomTab.SETTINGS, chrome.selectedTab)
        assertTrue(chrome.recordEnabled)
        assertTrue(chrome.historyEnabled)
        assertFalse(chrome.settingsEnabled)
    }
}
