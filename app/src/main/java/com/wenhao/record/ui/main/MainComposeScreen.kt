package com.wenhao.record.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.wenhao.record.R
import com.wenhao.record.ui.dashboard.DashboardOverlayUiState
import com.wenhao.record.ui.dashboard.DashboardScreenUiState
import com.wenhao.record.ui.designsystem.TrackAtmosphericBackground
import com.wenhao.record.ui.designsystem.TrackBottomNavigationBar
import com.wenhao.record.ui.designsystem.TrackBottomTab
import com.wenhao.record.ui.designsystem.TrackFloatingMapButton
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackStatChip
import com.wenhao.record.ui.history.HistoryComposeScreen
import com.wenhao.record.ui.history.HistoryScreenUiState
import com.wenhao.record.ui.map.TrackMapSceneState
import com.wenhao.record.ui.map.TrackMapViewportPadding
import com.wenhao.record.ui.map.TrackMapboxCanvas

@Composable
fun MainComposeScreen(
    currentTab: MainTab,
    dashboardState: DashboardScreenUiState,
    dashboardOverlayState: DashboardOverlayUiState,
    historyState: HistoryScreenUiState,
    aboutState: AboutUiState,
    mapboxAccessToken: String,
    dashboardMapState: TrackMapSceneState,
    recordingHealthState: RecordingHealthUiState,
    showRecordingHealthDiagnostics: Boolean,
    onRecordTabClick: () -> Unit,
    onHistoryTabClick: () -> Unit,
    onAboutTabClick: () -> Unit,
    onAboutBackClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onMapboxTokenChange: (String) -> Unit,
    onMapboxTokenSaveClick: () -> Unit,
    onMapboxTokenClearClick: () -> Unit,
    onWorkerBaseUrlChange: (String) -> Unit,
    onUploadTokenChange: (String) -> Unit,
    onSampleUploadConfigSaveClick: () -> Unit,
    onSampleUploadConfigClearClick: () -> Unit,
    onWorkerConnectivityTestClick: () -> Unit,
    onLocateClick: () -> Unit,
    onRecordingHealthPrimaryAction: () -> Unit,
    onRecordingHealthItemAction: (RecordingHealthAction) -> Unit,
    onRecordingHealthDiagnosticsDismiss: () -> Unit,
    onHistoryOpen: (Long) -> Unit,
    onHistoryDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecordTab = currentTab == MainTab.RECORD
    Box(modifier = modifier.fillMaxSize()) {
        DashboardRoot(
            dashboardState = dashboardState,
            overlayState = dashboardOverlayState,
            mapState = dashboardMapState,
            mapboxAccessToken = mapboxAccessToken,
            recordingHealthState = recordingHealthState,
            showRecordingHealthDiagnostics = showRecordingHealthDiagnostics,
            onHistoryTabClick = onHistoryTabClick,
            onAboutTabClick = onAboutTabClick,
            onLocateClick = onLocateClick,
            onRecordingHealthPrimaryAction = onRecordingHealthPrimaryAction,
            onRecordingHealthItemAction = onRecordingHealthItemAction,
            onRecordingHealthDiagnosticsDismiss = onRecordingHealthDiagnosticsDismiss,
            isRecordVisible = isRecordTab,
        )

        when (currentTab) {
            MainTab.RECORD -> Unit

            MainTab.HISTORY -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(4f),
                ) {
                    TrackAtmosphericBackground()
                    HistoryComposeScreen(
                        state = historyState,
                        onRecordClick = onRecordTabClick,
                        onSettingsClick = onAboutTabClick,
                        onHistoryClick = { onHistoryOpen(it.dayStartMillis) },
                        onHistoryLongClick = { onHistoryDelete(it.dayStartMillis) },
                    )
                }
            }

            MainTab.ABOUT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(4f),
                ) {
                    TrackAtmosphericBackground()
                    AboutComposeScreen(
                        state = aboutState,
                        onRecordClick = onRecordTabClick,
                        onHistoryClick = onHistoryTabClick,
                        onSettingsClick = {},
                        onCheckUpdateClick = onCheckUpdateClick,
                        onMapboxTokenChange = onMapboxTokenChange,
                        onMapboxTokenSaveClick = onMapboxTokenSaveClick,
                        onMapboxTokenClearClick = onMapboxTokenClearClick,
                        onWorkerBaseUrlChange = onWorkerBaseUrlChange,
                        onUploadTokenChange = onUploadTokenChange,
                        onSampleUploadConfigSaveClick = onSampleUploadConfigSaveClick,
                        onSampleUploadConfigClearClick = onSampleUploadConfigClearClick,
                        onWorkerConnectivityTestClick = onWorkerConnectivityTestClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardRoot(
    dashboardState: DashboardScreenUiState,
    overlayState: DashboardOverlayUiState,
    mapState: TrackMapSceneState,
    mapboxAccessToken: String,
    recordingHealthState: RecordingHealthUiState,
    showRecordingHealthDiagnostics: Boolean,
    onHistoryTabClick: () -> Unit,
    onAboutTabClick: () -> Unit,
    onLocateClick: () -> Unit,
    onRecordingHealthPrimaryAction: () -> Unit,
    onRecordingHealthItemAction: (RecordingHealthAction) -> Unit,
    onRecordingHealthDiagnosticsDismiss: () -> Unit,
    isRecordVisible: Boolean,
) {
    var showRecenterCue by rememberSaveable { mutableStateOf(false) }
    var showLocationInfoDialog by rememberSaveable { mutableStateOf(false) }

    val navigationBarHeight = 84.dp
    val floatingGap = 12.dp
    val recordOverlayBottomPadding = 236.dp
    val visibleMapState = if (isRecordVisible) {
        mapState
    } else {
        mapState.copy(
            polylines = emptyList(),
            markers = emptyList(),
            viewportRequest = null,
        )
    }

    LaunchedEffect(isRecordVisible) {
        if (!isRecordVisible) {
            showLocationInfoDialog = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TrackMapboxCanvas(
            state = visibleMapState,
            accessToken = mapboxAccessToken,
            modifier = Modifier.fillMaxSize(),
            viewportPadding = TrackMapViewportPadding(
                top = 24.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = if (isRecordVisible) {
                    recordOverlayBottomPadding
                } else {
                    navigationBarHeight + floatingGap + 34.dp
                },
            ),
            showUserLocationPuck = true,
            interactive = isRecordVisible,
            onUserGestureMove = { showRecenterCue = true },
            onUserLocationPuckClick = {
                if (isRecordVisible) {
                    showLocationInfoDialog = true
                }
            },
        )

        if (isRecordVisible && overlayState.locateVisible) {
            TrackFloatingMapButton(
                icon = painterResource(R.drawable.ic_locate_dashboard),
                contentDescription = stringResource(R.string.dashboard_locate),
                onClick = {
                    showRecenterCue = false
                    onLocateClick()
                },
                accented = showRecenterCue,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(4f)
                    .navigationBarsPadding()
                    .padding(
                        end = 18.dp,
                        bottom = if (isRecordVisible) {
                            206.dp
                        } else {
                            navigationBarHeight + floatingGap + 18.dp
                        },
                    ),
            )
        }

        if (isRecordVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 90.dp)
                    .widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DashboardMetricsStrip(dashboardState = dashboardState)
                RecordingHealthCard(
                    state = recordingHealthState,
                    onPrimaryActionClick = onRecordingHealthPrimaryAction,
                    onItemClick = onRecordingHealthItemAction,
                )
            }
        }

        if (isRecordVisible) {
            TrackBottomNavigationBar(
                selectedTab = TrackBottomTab.RECORD,
                onRecordClick = {},
                onHistoryClick = onHistoryTabClick,
                onSettingsClick = onAboutTabClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(3f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                recordLabel = stringResource(R.string.compose_dashboard_record),
                historyLabel = stringResource(R.string.compose_dashboard_history),
                settingsLabel = stringResource(R.string.compose_dashboard_about),
                recordEnabled = false,
            )
        }

        if (isRecordVisible && showLocationInfoDialog) {
            DashboardLocationInfoDialog(
                dashboardState = dashboardState,
                overlayState = overlayState,
                onDismissRequest = { showLocationInfoDialog = false },
            )
        }

        if (isRecordVisible && showRecordingHealthDiagnostics) {
            RecordingHealthDiagnosticsDialog(
                state = recordingHealthState,
                onDismissRequest = onRecordingHealthDiagnosticsDismiss,
            )
        }
    } // Box
}

enum class MainTab {
    RECORD,
    HISTORY,
    ABOUT,
}

@Composable
private fun DashboardLocationInfoDialog(
    dashboardState: DashboardScreenUiState,
    overlayState: DashboardOverlayUiState,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        TrackLiquidPanel(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tone = TrackLiquidTone.STRONG,
            shadowElevation = 22.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 18.dp,
                vertical = 18.dp,
            ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                DashboardStatusContent(
                    dashboardState = dashboardState,
                    overlayState = overlayState,
                )
                DashboardMetricsContent(dashboardState = dashboardState)
            }
        }
    }
}

@Composable
private fun DashboardStatusHeader(
    dashboardState: DashboardScreenUiState,
    overlayState: DashboardOverlayUiState,
    modifier: Modifier = Modifier,
) {
    TrackLiquidPanel(
        modifier = modifier.widthIn(max = 320.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.SUBTLE,
        shadowElevation = 14.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp,
            vertical = 12.dp,
        ),
    ) {
        DashboardStatusContent(
            dashboardState = dashboardState,
            overlayState = overlayState,
        )
    }
}

@Composable
private fun DashboardStatusContent(
    dashboardState: DashboardScreenUiState,
    overlayState: DashboardOverlayUiState,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TrackStatChip(
            text = overlayState.gpsLabel.ifBlank { dashboardState.statusLabel },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = dashboardState.autoTrackTitle.ifBlank {
                stringResource(R.string.compose_dashboard_title_idle)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = dashboardState.autoTrackMeta.ifBlank {
                stringResource(R.string.compose_dashboard_meta_idle)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DashboardMetricsStrip(
    dashboardState: DashboardScreenUiState,
    modifier: Modifier = Modifier,
) {
    TrackLiquidPanel(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 18.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 8.dp,
        ),
    ) {
        DashboardMetricsContent(dashboardState = dashboardState)
    }
}

@Composable
private fun DashboardMetricsContent(
    dashboardState: DashboardScreenUiState,
) {
    val speedValue = dashboardState.speedText.substringBefore(" ").ifBlank { dashboardState.speedText }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashboardMetricPill(
            label = stringResource(R.string.compose_dashboard_primary_metric_label),
            value = dashboardState.distanceText,
            modifier = Modifier.weight(1f),
        )
        DashboardMetricPill(
            label = stringResource(R.string.compose_dashboard_time_label),
            value = dashboardState.durationText,
            modifier = Modifier.weight(1f),
        )
        DashboardMetricPill(
            label = stringResource(R.string.compose_dashboard_speed_label),
            value = speedValue,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DashboardMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    TrackLiquidPanel(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tone = TrackLiquidTone.SUBTLE,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 10.dp,
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
