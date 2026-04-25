package com.wenhao.record.ui.main

data class HomeRecordChromeModel(
    val spotlightItem: RecordingHealthItemUiState?,
    val secondaryActionText: String,
)

fun buildHomeRecordChromeModel(state: RecordingHealthUiState): HomeRecordChromeModel {
    val spotlightItem = compactRecordingHealthHighlights(
        state = state,
        maxItems = 1,
    ).firstOrNull()

    return HomeRecordChromeModel(
        spotlightItem = spotlightItem,
        secondaryActionText = if (spotlightItem == null) "查看状态" else "查看详情",
    )
}
