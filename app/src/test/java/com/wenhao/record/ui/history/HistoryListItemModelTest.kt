package com.wenhao.record.ui.history

import com.wenhao.record.data.history.HistoryDaySummaryItem
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryListItemModelTest {

    @Test
    fun `list item uses route title and compact metrics`() {
        val item = HistoryDaySummaryItem(
            dayStartMillis = 1_742_976_000_000L,
            latestTimestamp = 1_742_980_200_000L,
            sessionCount = 2,
            totalDistanceKm = 12.34,
            totalDurationSeconds = 754,
            averageSpeedKmh = 5.8,
            sourceIds = listOf(42L),
            routeTitle = "鸿运之星",
        )

        val model = buildHistoryListItemModel(item)

        assertEquals("鸿运之星", model.title)
        assertEquals("3月26日 星期三 · 最近 17:10 · 2 段 · 12:34", model.subtitle)
        assertEquals("12.34", model.distanceText)
        assertEquals("公里", model.distanceUnit)
    }

    @Test
    fun `list item falls back to trip number instead of date title`() {
        val item = HistoryDaySummaryItem(
            dayStartMillis = 1_742_976_000_000L,
            latestTimestamp = 1_742_980_200_000L,
            sessionCount = 1,
            totalDistanceKm = 12.34,
            totalDurationSeconds = 754,
            averageSpeedKmh = 5.8,
            sourceIds = listOf(42L),
        )

        val model = buildHistoryListItemModel(item)

        assertEquals("行程 #42", model.title)
    }
}
