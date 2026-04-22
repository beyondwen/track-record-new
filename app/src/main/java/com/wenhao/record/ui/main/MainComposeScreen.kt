package com.wenhao.record.ui.main

import androidx.compose.foundation.background
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay

@Composable
fun MainComposeScreen(
    currentTab: MainTab,
    dashboardState: DashboardScreenUiState,
    dashboardOverlayState: DashboardOverlayUiState,
    historyState: HistoryScreenUiState,
    aboutState: AboutUiState,
    mapboxAccessToken: String,
    dashboardMapState: TrackMapSceneState,
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
    onHistoryOpen: (Long) -> Unit,
    onHistoryDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        TrackAtmosphericBackground()
        when (currentTab) {
            MainTab.HISTORY -> {
                HistoryComposeScreen(
                    state = historyState,
                    onRecordClick = onRecordTabClick,
                    onSettingsClick = onAboutTabClick,
                    onHistoryClick = { onHistoryOpen(it.dayStartMillis) },
                    onHistoryLongClick = { onHistoryDelete(it.dayStartMillis) },
                )
            }

            MainTab.ABOUT -> {
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

            MainTab.RECORD -> {
                DashboardRoot(
                    dashboardState = dashboardState,
                    overlayState = dashboardOverlayState,
                    mapState = dashboardMapState,
                    mapboxAccessToken = mapboxAccessToken,
                    onHistoryTabClick = onHistoryTabClick,
                    onAboutTabClick = onAboutTabClick,
                    onLocateClick = onLocateClick,
                )
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
    onHistoryTabClick: () -> Unit,
    onAboutTabClick: () -> Unit,
    onLocateClick: () -> Unit,
) {
    var showRecenterCue by rememberSaveable { mutableStateOf(false) }
    var hasAttachedMap by rememberSaveable { mutableStateOf(false) }

    val metricsStripHeightEstimate = 66.dp
    val navigationBarHeight = 84.dp
    val floatingGap = 12.dp

    LaunchedEffect(Unit) {
        if (hasAttachedMap) return@LaunchedEffect
        withFrameNanos { }
        delay(120L)
        hasAttachedMap = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasAttachedMap) {
            TrackMapboxCanvas(
                state = mapState,
                accessToken = mapboxAccessToken,
                modifier = Modifier.fillMaxSize(),
                viewportPadding = TrackMapViewportPadding(
                    top = 116.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = metricsStripHeightEstimate + navigationBarHeight + floatingGap + 24.dp,
                ),
                showUserLocationPuck = true,
                onUserGestureMove = { showRecenterCue = true },
            )
        } else {
            DashboardMapLoadingPlaceholder(modifier = Modifier.fillMaxSize())
        }

        DashboardStatusHeader(
            dashboardState = dashboardState,
            overlayState = overlayState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(2f)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )

        if (overlayState.locateVisible) {
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
                    .zIndex(2f)
                    .navigationBarsPadding()
                    .padding(
                        end = 18.dp,
                        bottom = metricsStripHeightEstimate + navigationBarHeight + floatingGap + 14.dp,
                    ),
            )
        }

        DashboardMetricsStrip(
            dashboardState = dashboardState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f)
                .padding(bottom = navigationBarHeight + floatingGap)
                .padding(horizontal = 16.dp),
        )

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
    } // Box
}

@Composable
private fun DashboardMapLoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "正在准备地图",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

enum class MainTab {
    RECORD,
    HISTORY,
    ABOUT,
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
}

@Composable
private fun DashboardMetricsStrip(
    dashboardState: DashboardScreenUiState,
    modifier: Modifier = Modifier,
) {
    val speedValue = dashboardState.speedText.substringBefore(" ").ifBlank { dashboardState.speedText }
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
