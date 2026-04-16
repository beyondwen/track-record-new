package com.wenhao.record.ui.designsystem

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberVisibleSheetHeight(
    sheetState: SheetState,
    layoutHeight: Dp,
    peekHeight: Dp,
    expandedHeightPx: Float,
): Dp {
    val density = LocalDensity.current
    val layoutHeightPx = with(density) { layoutHeight.toPx() }
    val peekHeightPx = with(density) { peekHeight.toPx() }
    val visibleHeightPx by remember(sheetState, layoutHeightPx, peekHeightPx, expandedHeightPx) {
        derivedStateOf {
            val fallbackVisibleHeight = when (sheetState.currentValue) {
                SheetValue.Expanded -> expandedHeightPx.takeIf { it > 0f } ?: peekHeightPx
                SheetValue.PartiallyExpanded,
                SheetValue.Hidden -> peekHeightPx
            }
            val fallbackOffset = (layoutHeightPx - fallbackVisibleHeight).coerceAtLeast(0f)
            val currentOffset = runCatching { sheetState.requireOffset() }
                .getOrDefault(fallbackOffset)

            (layoutHeightPx - currentOffset).coerceAtLeast(peekHeightPx)
        }
    }

    return with(density) { visibleHeightPx.toDp() }
}

@Composable
fun rememberSheetAwareBottomPadding(
    sheetVisibleHeight: Dp,
    extraSpacing: Dp,
): Dp = remember(sheetVisibleHeight, extraSpacing) {
    sheetVisibleHeight + extraSpacing
}

@Composable
fun rememberSheetAwareViewportBottomPadding(
    sheetVisibleHeight: Dp,
    extraSpacing: Dp = 24.dp,
): Dp = remember(sheetVisibleHeight, extraSpacing) {
    sheetVisibleHeight + extraSpacing
}
