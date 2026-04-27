package com.wenhao.record.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wenhao.record.R
import com.wenhao.record.runtimeusage.RuntimeUsageModule
import com.wenhao.record.runtimeusage.RuntimeUsageRecorder
import com.wenhao.record.ui.dashboard.DashboardOverlayUiState
import com.wenhao.record.ui.dashboard.DashboardScreenUiState
import com.wenhao.record.ui.designsystem.TrackAtmosphericBackground
import com.wenhao.record.ui.designsystem.TrackBottomNavigationBar
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
    onSyncDiagnosticsRefreshClick: () -> Unit,
    onLocateClick: () -> Unit,
    onRecordingHealthPrimaryAction: () -> Unit,
    onRecordingHealthItemAction: (RecordingHealthAction) -> Unit,
    onRecordingHealthDiagnosticsDismiss: () -> Unit,
    onHistoryOpen: (Long) -> Unit,
    onHistoryDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecordTab = currentTab == MainTab.RECORD
    val bottomNavigationChrome = buildMainBottomNavigationChrome(currentTab)
    LaunchedEffect(currentTab) {
        RuntimeUsageRecorder.hit(
            when (currentTab) {
                MainTab.RECORD -> RuntimeUsageModule.UI_TAB_RECORD
                MainTab.HISTORY -> RuntimeUsageModule.UI_TAB_HISTORY
                MainTab.ABOUT -> RuntimeUsageModule.UI_TAB_ABOUT
            },
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        DashboardRoot(
            dashboardState = dashboardState,
            overlayState = dashboardOverlayState,
            mapState = dashboardMapState,
            mapboxAccessToken = mapboxAccessToken,
            recordingHealthState = recordingHealthState,
            showRecordingHealthDiagnostics = showRecordingHealthDiagnostics,
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
                        onCheckUpdateClick = onCheckUpdateClick,
                        onMapboxTokenChange = onMapboxTokenChange,
                        onMapboxTokenSaveClick = onMapboxTokenSaveClick,
                        onMapboxTokenClearClick = onMapboxTokenClearClick,
                        onWorkerBaseUrlChange = onWorkerBaseUrlChange,
                        onUploadTokenChange = onUploadTokenChange,
                        onSampleUploadConfigSaveClick = onSampleUploadConfigSaveClick,
                        onSampleUploadConfigClearClick = onSampleUploadConfigClearClick,
                        onWorkerConnectivityTestClick = onWorkerConnectivityTestClick,
                        onSyncDiagnosticsRefreshClick = onSyncDiagnosticsRefreshClick,
                    )
                }
            }
        }

        TrackBottomNavigationBar(
            selectedTab = bottomNavigationChrome.selectedTab,
            onRecordClick = onRecordTabClick,
            onHistoryClick = onHistoryTabClick,
            onSettingsClick = onAboutTabClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(5f)
                .padding(horizontal = 18.dp, vertical = 5.dp),
            recordLabel = stringResource(R.string.compose_dashboard_record),
            historyLabel = stringResource(R.string.compose_dashboard_history),
            settingsLabel = stringResource(R.string.compose_dashboard_about),
            recordEnabled = bottomNavigationChrome.recordEnabled,
            historyEnabled = bottomNavigationChrome.historyEnabled,
            settingsEnabled = bottomNavigationChrome.settingsEnabled,
        )
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
    onLocateClick: () -> Unit,
    onRecordingHealthPrimaryAction: () -> Unit,
    onRecordingHealthItemAction: (RecordingHealthAction) -> Unit,
    onRecordingHealthDiagnosticsDismiss: () -> Unit,
    isRecordVisible: Boolean,
) {
    var showRecenterCue by rememberSaveable { mutableStateOf(false) }
    var isStatusCapsuleExpanded by rememberSaveable { mutableStateOf(false) }

    val navigationBarHeight = 64.dp
    val floatingGap = 8.dp
    val visibleMapState = if (isRecordVisible) {
        mapState
    } else {
        mapState.copy(
            polylines = emptyList(),
            heatPoints = emptyList(),
            markers = emptyList(),
            viewportRequest = null,
        )
    }

    LaunchedEffect(isRecordVisible) {
        if (!isRecordVisible) {
            isStatusCapsuleExpanded = false
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
                bottom = navigationBarHeight + floatingGap + 34.dp,
            ),
            showUserLocationPuck = true,
            interactive = isRecordVisible,
            onUserGestureMove = {
                showRecenterCue = true
                isStatusCapsuleExpanded = false
            },
            onMapClick = {
                isStatusCapsuleExpanded = false
            },
        )

        if (isRecordVisible) {
            DashboardStatusBarScrim(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
            )
        }

        if (isRecordVisible) {
            DashboardStatusCapsule(
                dashboardState = dashboardState,
                recordingHealthState = recordingHealthState,
                expanded = isStatusCapsuleExpanded,
                onExpandedChange = { isStatusCapsuleExpanded = it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(3f)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 14.dp),
            )
        }

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
                        end = 16.dp,
                        bottom = navigationBarHeight + floatingGap + 14.dp,
                    ),
            )
        }

        if (isRecordVisible && showRecordingHealthDiagnostics) {
            RecordingHealthDiagnosticsDialog(
                state = recordingHealthState,
                onItemActionClick = onRecordingHealthItemAction,
                onDismissRequest = onRecordingHealthDiagnosticsDismiss,
            )
        }
    } // Box
}


@Composable
private fun DashboardStatusBarScrim(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.16f),
                        Color.Black.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}


@Composable
private fun DashboardStatusCapsule(
    dashboardState: DashboardScreenUiState,
    recordingHealthState: RecordingHealthUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = buildDashboardStatusCapsuleModel(
        dashboardState = dashboardState,
        recordingHealthState = recordingHealthState,
    )
    val transition = updateTransition(targetState = expanded, label = "statusCapsuleTransition")
    val capsuleWidth by transition.animateDp(
        transitionSpec = { tween(durationMillis = 360, easing = FastOutSlowInEasing) },
        label = "statusCapsuleWidth",
    ) { isExpanded ->
        if (isExpanded) 330.dp else 156.dp
    }
    val capsuleHeight by transition.animateDp(
        transitionSpec = { tween(durationMillis = 360, easing = FastOutSlowInEasing) },
        label = "statusCapsuleHeight",
    ) { isExpanded ->
        if (isExpanded) 150.dp else 42.dp
    }
    val capsuleCornerRadius by transition.animateDp(
        transitionSpec = { tween(durationMillis = 360, easing = FastOutSlowInEasing) },
        label = "statusCapsuleCornerRadius",
    ) { isExpanded ->
        if (isExpanded) 30.dp else 999.dp
    }
    val collapsedAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 90) },
        label = "statusCapsuleCollapsedAlpha",
    ) { isExpanded ->
        if (isExpanded) 0f else 1f
    }
    val expandedAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 160, delayMillis = 140) },
        label = "statusCapsuleExpandedAlpha",
    ) { isExpanded ->
        if (isExpanded) 1f else 0f
    }
    val collapsedContentOffset by transition.animateDp(
        transitionSpec = { tween(durationMillis = 360, easing = FastOutSlowInEasing) },
        label = "statusCapsuleCollapsedOffset",
    ) { isExpanded ->
        if (isExpanded) 8.dp else 0.dp
    }
    val chromeModel = buildHomeRecordChromeModel(recordingHealthState)
    val spotlightText = chromeModel.spotlightItem?.let { item ->
        item.riskText ?: item.statusText
    } ?: recordingHealthState.summaryText

    Surface(
        onClick = { onExpandedChange(!expanded) },
        modifier = modifier
            .width(capsuleWidth)
            .height(capsuleHeight)
            .clipToBounds(),
        shape = RoundedCornerShape(capsuleCornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = collapsedContentOffset)
                    .alpha(collapsedAlpha),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dashboardOverviewStatusContentColor(model.healthStatus)),
                )
                Text(
                    text = model.statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = model.distanceText,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            DashboardCapsuleExpandedContent(
                model = model,
                spotlightText = spotlightText,
                modifier = Modifier.alpha(expandedAlpha),
            )
        }
    }
}

@Composable
private fun DashboardCapsuleExpandedContent(
    model: DashboardStatusCapsuleModel,
    spotlightText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dashboardOverviewStatusContentColor(model.healthStatus)),
                )
                Text(
                    text = model.statusText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(dashboardOverviewStatusContainerColor(model.healthStatus).copy(alpha = 0.56f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(
                    text = dashboardOverviewStatusLabel(model.healthStatus),
                    style = MaterialTheme.typography.labelSmall,
                    color = dashboardOverviewStatusContentColor(model.healthStatus),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            DashboardCapsuleDistanceBlock(
                distanceText = model.distanceText,
                modifier = Modifier.weight(1f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DashboardCapsuleMetric(
                    label = stringResource(R.string.compose_dashboard_time_label),
                    value = model.durationText,
                )
                DashboardCapsuleMetric(
                    label = stringResource(R.string.compose_dashboard_speed_label),
                    value = model.speedText,
                )
            }
        }

        Text(
            text = spotlightText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DashboardCapsuleDistanceBlock(
    distanceText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "今日里程",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = distanceText,
                style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = "公里",
                modifier = Modifier.padding(bottom = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DashboardCapsuleMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun dashboardOverviewStatusLabel(status: RecordingHealthOverallStatus): String {
    return when (status) {
        RecordingHealthOverallStatus.READY ->
            stringResource(R.string.compose_dashboard_health_ready)

        RecordingHealthOverallStatus.DEGRADED ->
            stringResource(R.string.compose_dashboard_health_degraded)

        RecordingHealthOverallStatus.BLOCKED ->
            stringResource(R.string.compose_dashboard_health_blocked)
    }
}

@Composable
private fun dashboardOverviewStatusContainerColor(status: RecordingHealthOverallStatus) = when (status) {
    RecordingHealthOverallStatus.READY -> MaterialTheme.colorScheme.secondaryContainer
    RecordingHealthOverallStatus.DEGRADED -> MaterialTheme.colorScheme.tertiaryContainer
    RecordingHealthOverallStatus.BLOCKED -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun dashboardOverviewStatusContentColor(status: RecordingHealthOverallStatus) = when (status) {
    RecordingHealthOverallStatus.READY -> MaterialTheme.colorScheme.onSecondaryContainer
    RecordingHealthOverallStatus.DEGRADED -> MaterialTheme.colorScheme.onTertiaryContainer
    RecordingHealthOverallStatus.BLOCKED -> MaterialTheme.colorScheme.onErrorContainer
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
private fun DashboardMetricsContent(
    dashboardState: DashboardScreenUiState,
    modifier: Modifier = Modifier,
) {
    val speedValue = dashboardState.speedText.substringBefore(" ").ifBlank { dashboardState.speedText }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardMetricPill(
            label = stringResource(R.string.compose_dashboard_primary_metric_label),
            value = dashboardState.distanceText,
            modifier = Modifier.weight(1f),
        )
        DashboardMetricDivider()
        DashboardMetricPill(
            label = stringResource(R.string.compose_dashboard_time_label),
            value = dashboardState.durationText,
            modifier = Modifier.weight(1f),
        )
        DashboardMetricDivider()
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DashboardMetricDivider() {
    Box(
        modifier = Modifier
            .height(22.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
            .widthIn(min = 1.dp, max = 1.dp),
    )
}
