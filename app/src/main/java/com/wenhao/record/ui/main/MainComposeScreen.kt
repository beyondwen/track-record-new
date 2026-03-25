package com.wenhao.record.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.MapView
import com.wenhao.record.R
import com.wenhao.record.ui.dashboard.DashboardComposeScreen
import com.wenhao.record.ui.dashboard.DashboardOverlayUiState
import com.wenhao.record.ui.dashboard.DashboardScreenUiState
import com.wenhao.record.ui.dashboard.DashboardTone
import com.wenhao.record.ui.designsystem.TrackFloatingMapButton
import com.wenhao.record.ui.designsystem.TrackStatChip
import com.wenhao.record.ui.designsystem.TrackTopOverlayColumn
import com.wenhao.record.ui.history.HistoryComposeScreen
import com.wenhao.record.ui.history.HistoryScreenUiState
import kotlinx.coroutines.launch

@Composable
fun MainComposeScreen(
    currentTab: MainTab,
    dashboardState: DashboardScreenUiState,
    dashboardOverlayState: DashboardOverlayUiState,
    historyState: HistoryScreenUiState,
    dashboardMapView: MapView,
    onRecordTabClick: () -> Unit,
    onHistoryTabClick: () -> Unit,
    onLocateClick: () -> Unit,
    onHistoryOpen: (Long) -> Unit,
    onHistoryDelete: (Long) -> Unit,
    onHistoryExport: () -> Unit,
    onHistoryImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (currentTab == MainTab.HISTORY) {
            HistoryComposeScreen(
                state = historyState,
                onRecordClick = onRecordTabClick,
                onExportClick = onHistoryExport,
                onImportClick = onHistoryImport,
                onHistoryClick = { onHistoryOpen(it.dayStartMillis) },
                onHistoryLongClick = { onHistoryDelete(it.dayStartMillis) },
            )
        } else {
            DashboardRoot(
                dashboardState = dashboardState,
                overlayState = dashboardOverlayState,
                mapView = dashboardMapView,
                onRecordTabClick = onRecordTabClick,
                onHistoryTabClick = onHistoryTabClick,
                onLocateClick = onLocateClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardRoot(
    dashboardState: DashboardScreenUiState,
    overlayState: DashboardOverlayUiState,
    mapView: MapView,
    onRecordTabClick: () -> Unit,
    onHistoryTabClick: () -> Unit,
    onLocateClick: () -> Unit,
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = androidx.compose.material3.SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState,
    )
    val coroutineScope = rememberCoroutineScope()

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 336.dp,
        sheetDragHandle = null,
        sheetContainerColor = Color.Transparent,
        sheetShadowElevation = 0.dp,
        sheetTonalElevation = 0.dp,
        sheetContent = {
            DashboardComposeScreen(
                state = dashboardState,
                onRecordClick = {
                    coroutineScope.launch {
                        if (sheetState.currentValue == androidx.compose.material3.SheetValue.Expanded) {
                            sheetState.partialExpand()
                        } else {
                            sheetState.expand()
                        }
                    }
                },
                onHistoryClick = onHistoryTabClick,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                factory = { mapView },
            )

            TrackTopOverlayColumn {
                TrackStatChip(
                    text = overlayState.gpsLabel,
                    containerColor = overlayChipContainerColor(overlayState.gpsTone),
                    contentColor = overlayChipContentColor(overlayState.gpsTone),
                )
            }

            TrackFloatingMapButton(
                icon = painterResource(R.drawable.ic_locate_dashboard),
                contentDescription = "定位到当前位置",
                onClick = onLocateClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 18.dp),
            )
        }
    }
}

@Composable
private fun overlayChipContainerColor(tone: DashboardTone): Color = when (tone) {
    DashboardTone.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
    DashboardTone.WARNING -> MaterialTheme.colorScheme.secondaryContainer
    DashboardTone.MUTED -> MaterialTheme.colorScheme.surface
    DashboardTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun overlayChipContentColor(tone: DashboardTone): Color = when (tone) {
    DashboardTone.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
    DashboardTone.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
    DashboardTone.MUTED -> MaterialTheme.colorScheme.onSurface
    DashboardTone.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
}

enum class MainTab {
    RECORD,
    HISTORY,
}
