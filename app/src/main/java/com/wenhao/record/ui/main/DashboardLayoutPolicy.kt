package com.wenhao.record.ui.main

import androidx.compose.ui.unit.dp
import com.wenhao.record.ui.map.TrackMapViewportPadding

internal object DashboardLayoutPolicy {

    fun mapViewportPadding(showInlineInfo: Boolean): TrackMapViewportPadding {
        return if (showInlineInfo) {
            TrackMapViewportPadding(
                top = 116.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 186.dp,
            )
        } else {
            TrackMapViewportPadding(
                top = 24.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 118.dp,
            )
        }
    }
}
