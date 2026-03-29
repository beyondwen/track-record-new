package com.wenhao.record.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wenhao.record.R
import com.wenhao.record.ui.dashboard.DashboardComposeScreen
import com.wenhao.record.ui.dashboard.DashboardOverlayUiState
import com.wenhao.record.ui.dashboard.DashboardScreenUiState
import com.wenhao.record.ui.dashboard.DashboardTone
import com.wenhao.record.ui.designsystem.TrackFloatingMapButton
import com.wenhao.record.ui.designsystem.TrackInsetPanel
import com.wenhao.record.ui.designsystem.TrackPrimaryButton
import com.wenhao.record.ui.designsystem.TrackRecordSpacing
import com.wenhao.record.ui.designsystem.TrackStatChip
import com.wenhao.record.ui.designsystem.TrackTopOverlayColumn
import com.wenhao.record.ui.designsystem.trackPageBackground
import com.wenhao.record.ui.designsystem.trackSecondarySurface
import com.wenhao.record.ui.designsystem.trackSoftOutline
import com.wenhao.record.ui.designsystem.trackSoftSurface
import com.wenhao.record.ui.designsystem.rememberSheetAwareBottomPadding
import com.wenhao.record.ui.designsystem.rememberSheetAwareViewportBottomPadding
import com.wenhao.record.ui.designsystem.rememberVisibleSheetHeight
import com.wenhao.record.ui.history.HistoryComposeScreen
import com.wenhao.record.ui.history.HistoryScreenUiState
import com.wenhao.record.ui.map.TrackMapSceneState
import com.wenhao.record.ui.map.TrackMapViewportPadding
import com.wenhao.record.ui.map.TrackMapboxCanvas
import kotlinx.coroutines.launch

@Composable
fun MainComposeScreen(
    currentTab: MainTab,
    dashboardState: DashboardScreenUiState,
    dashboardOverlayState: DashboardOverlayUiState,
    historyState: HistoryScreenUiState,
    dashboardMapState: TrackMapSceneState,
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
        color = MaterialTheme.colorScheme.trackPageBackground,
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
                mapState = dashboardMapState,
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
    mapState: TrackMapSceneState,
    onRecordTabClick: () -> Unit,
    onHistoryTabClick: () -> Unit,
    onLocateClick: () -> Unit,
) {
    var showOverlayStatusDialog by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val dashboardSheetPeekHeight = 72.dp
        val sheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Expanded,
            skipHiddenState = true,
        )
        val scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = sheetState,
        )
        val coroutineScope = rememberCoroutineScope()
        var sheetContentHeightPx by remember { mutableFloatStateOf(0f) }
        val visibleSheetHeight = rememberVisibleSheetHeight(
            sheetState = sheetState,
            layoutHeight = maxHeight,
            peekHeight = dashboardSheetPeekHeight,
            expandedHeightPx = sheetContentHeightPx,
        )
        val density = LocalDensity.current
        val settledSheetVisibleHeight = remember(sheetState.currentValue, sheetContentHeightPx, density) {
            when (sheetState.currentValue) {
                SheetValue.Expanded -> {
                    if (sheetContentHeightPx > 0f) {
                        with(density) { sheetContentHeightPx.toDp() }
                    } else {
                        dashboardSheetPeekHeight
                    }
                }

                SheetValue.PartiallyExpanded,
                SheetValue.Hidden,
                -> dashboardSheetPeekHeight
            }
        }
        val locateButtonBottomPadding = rememberSheetAwareBottomPadding(
            sheetVisibleHeight = settledSheetVisibleHeight,
            extraSpacing = 18.dp,
        )
        val mapViewportBottomPadding = rememberSheetAwareViewportBottomPadding(
            sheetVisibleHeight = settledSheetVisibleHeight,
            extraSpacing = 18.dp,
        )

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = dashboardSheetPeekHeight,
            sheetDragHandle = null,
            sheetContainerColor = Color.Transparent,
            sheetShadowElevation = 0.dp,
            sheetTonalElevation = 0.dp,
            sheetContent = {
                DashboardComposeScreen(
                    state = dashboardState,
                    overlayState = overlayState,
                    isSheetExpanded = sheetState.currentValue == SheetValue.Expanded,
                    onRecordClick = {
                        coroutineScope.launch {
                            if (sheetState.currentValue == SheetValue.Expanded) {
                                sheetState.partialExpand()
                            } else {
                                sheetState.expand()
                            }
                        }
                    },
                    onHistoryClick = onHistoryTabClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { sheetContentHeightPx = it.height.toFloat() },
                )
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TrackMapboxCanvas(
                    state = mapState,
                    modifier = Modifier.fillMaxSize(),
                    viewportPadding = TrackMapViewportPadding(
                        top = 76.dp,
                        start = 20.dp,
                        end = 20.dp,
                        bottom = mapViewportBottomPadding,
                    ),
                )

                TrackTopOverlayColumn {
                    MapStatusCollapsedEntry(
                        title = overlayState.gpsLabel.ifBlank { dashboardState.statusLabel },
                        accentColor = overlayAccentColor(dashboardState.statusTone),
                        onClick = { showOverlayStatusDialog = true },
                    )
                }

                if (overlayState.locateVisible) {
                    TrackFloatingMapButton(
                        icon = painterResource(R.drawable.ic_locate_dashboard),
                        contentDescription = stringResource(R.string.dashboard_locate),
                        onClick = onLocateClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 18.dp, bottom = locateButtonBottomPadding),
                    )
                }
            }
        }
    }

    if (showOverlayStatusDialog) {
        MapOverlayStatusDialog(
            dashboardState = dashboardState,
            overlayState = overlayState,
            onDismiss = { showOverlayStatusDialog = false },
        )
    }
}

enum class MainTab {
    RECORD,
    HISTORY,
}

@Composable
private fun overlayAccentColor(tone: DashboardTone) = when (tone) {
    DashboardTone.ACTIVE -> MaterialTheme.colorScheme.primary
    DashboardTone.WARNING -> MaterialTheme.colorScheme.tertiary
    DashboardTone.MUTED -> MaterialTheme.colorScheme.secondary
    DashboardTone.SUCCESS -> MaterialTheme.colorScheme.primary
}

@Composable
private fun MapStatusCollapsedEntry(
    title: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallbackTitle = stringResource(R.string.compose_map_overlay_status_fallback)
    val collapsedTitle = title.ifBlank { fallbackTitle }
    val collapsedDescription = stringResource(
        R.string.compose_map_overlay_collapsed_description,
        collapsedTitle,
    )
    Surface(
        modifier = modifier
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.compose_map_overlay_open_status),
                onClick = onClick,
            )
            .semantics {
                stateDescription = collapsedTitle
                contentDescription = collapsedDescription
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.trackSoftOutline,
        ),
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(
                        color = accentColor,
                        shape = MaterialTheme.shapes.extraLarge,
                    ),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = collapsedTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.compose_map_overlay_collapsed_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.size(2.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MapOverlayStatusDialog(
    dashboardState: DashboardScreenUiState,
    overlayState: DashboardOverlayUiState,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.985f),
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 24.dp,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TrackStatChip(
                            text = dashboardState.autoTrackTitle.ifBlank {
                                stringResource(R.string.compose_map_overlay_status_fallback)
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = overlayState.gpsLabel.ifBlank {
                                stringResource(R.string.compose_map_overlay_status_fallback)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = dashboardState.statusLabel.ifBlank {
                                stringResource(R.string.compose_map_overlay_status_waiting)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics {
                                stateDescription = dashboardState.statusLabel
                            },
                        )
                    }

                    FilledIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.compose_map_overlay_close_status),
                        )
                    }
                }

                TrackInsetPanel(accented = true) {
                    Text(
                        text = stringResource(R.string.compose_map_overlay_tracking_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = dashboardState.autoTrackMeta.ifBlank {
                            stringResource(R.string.compose_map_overlay_status_waiting)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TrackInsetPanel {
                    Text(
                        text = overlayState.diagnosticsTitle.ifBlank {
                            stringResource(R.string.compose_map_overlay_diagnostics_title)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = overlayState.diagnosticsCompactBody.ifBlank {
                            stringResource(R.string.compose_map_overlay_diagnostics_empty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TrackPrimaryButton(
                    text = stringResource(R.string.compose_map_overlay_confirm),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
