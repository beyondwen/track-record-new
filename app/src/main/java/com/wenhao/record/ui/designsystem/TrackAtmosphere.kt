package com.wenhao.record.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme

/**
 * 简洁的页面背景。
 * 旧版的复杂大气发光效果已替换为 Material 3 标准的背景色。
 */
@Composable
fun TrackAtmosphericBackground(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    )
}
