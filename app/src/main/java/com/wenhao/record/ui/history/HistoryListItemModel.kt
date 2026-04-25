package com.wenhao.record.ui.history

import com.wenhao.record.data.history.HistoryDaySummaryItem

data class HistoryListItemModel(
    val title: String,
    val subtitle: String,
    val distanceText: String,
    val distanceUnit: String,
)

fun buildHistoryListItemModel(item: HistoryDaySummaryItem): HistoryListItemModel {
    val distanceParts = item.formattedDistance.split(" ", limit = 2)
    val routeName = item.routeTitle
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: item.sourceIds.singleOrNull()?.let { id -> "行程 #${id}" }
        ?: "${item.sessionCountLabel}行程"
    return HistoryListItemModel(
        title = routeName,
        subtitle = "${item.formattedDateTitle} · 最近 ${item.formattedLatestTime} · ${item.sessionCountLabel} · ${item.formattedDuration}",
        distanceText = distanceParts.first(),
        distanceUnit = distanceParts.getOrElse(1) { "公里" },
    )
}
