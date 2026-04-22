package com.wenhao.record.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.wenhao.record.R
import com.wenhao.record.ui.dashboard.DashboardComposeScreen
import com.wenhao.record.ui.dashboard.DashboardOverlayUiState
import com.wenhao.record.ui.dashboard.DashboardScreenUiState
import com.wenhao.record.ui.dashboard.DashboardTone
import com.wenhao.record.ui.designsystem.TrackAtmosphericBackground
import com.wenhao.record.ui.designsystem.TrackFloatingMapButton
import com.wenhao.record.ui.designsystem.TrackInsetPanel
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackPrimaryButton
import com.wenhao.record.ui.designsystem.TrackRecordSpacing
import com.wenhao.record.ui.designsystem.TrackStatChip
import com.wenhao.record.ui.designsystem.TrackTopOverlayColumn
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
                    onHistoryClick = { onHistoryOpen(it.dayStartMillis) },
                    onHistoryLongClick = { onHistoryDelete(it.dayStartMillis) },
                )
            }

            MainTab.ABOUT -> {
                AboutComposeScreen(
                    state = aboutState,
                    onBackClick = onAboutBackClick,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showOverlayStatusDialog by rememberSaveable { mutableStateOf(false) }
    var showRecenterCue by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var progress by remember { mutableFloatStateOf(1f) }
    val panelHeight = 320.dp
    val handleHeight = 24.dp
    val panelHeightPx = with(density) { panelHeight.toPx() }
    val hiddenOffsetPx = panelHeightPx
    val visiblePanelHeight = with(density) { (panelHeightPx * progress).toDp() }

    fun settle() {
        val target = if (progress > 0.5f) 1f else 0f
        scope.launch {
            val anim = Animatable(progress)
            anim.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
            progress = anim.value
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TrackMapboxCanvas(
            state = mapState,
            accessToken = mapboxAccessToken,
            modifier = Modifier.fillMaxSize(),
            viewportPadding = TrackMapViewportPadding(
                top = 96.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = visiblePanelHeight + 16.dp,
            ),
            showCenterIndicator = true,
            onUserGestureMove = { showRecenterCue = true },
        )

        TrackTopOverlayColumn(modifier = Modifier.padding(top = 8.dp)) {
            MapStatusCollapsedEntry(
                title = overlayState.gpsLabel.ifBlank { dashboardState.statusLabel },
                accentColor = overlayAccentColor(dashboardState.statusTone),
                onClick = { showOverlayStatusDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

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
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = visiblePanelHeight + 20.dp),
            )
        }

        if (progress < 0.1f) {
            TrackFloatingMapButton(
                icon = painterResource(R.drawable.ic_tab_record),
                contentDescription = stringResource(R.string.compose_dashboard_record),
                onClick = {
                    progress = 1f
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }

    val velocityTracker = remember { VelocityTracker() }

    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset { IntOffset(0, (hiddenOffsetPx * (1f - progress)).roundToInt()) }
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    velocityTracker.resetTracking()

                    var totalDragY = 0f
                    var isDragging = false

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue

                        if (!isDragging) {
                            totalDragY += change.positionChange().y
                            if (abs(totalDragY) > viewConfiguration.touchSlop) {
                                isDragging = true
                            }
                        }

                        if (isDragging) {
                            progress = calculateDashboardPanelProgress(
                                currentProgress = progress,
                                dragDeltaY = change.positionChange().y,
                                panelHeightPx = hiddenOffsetPx,
                            )
                            velocityTracker.addPointerInputChange(change)
                        }
                        change.consume()
                    } while (event.changes.any { it.pressed })

                    if (isDragging) {
                        settle()
                    }
                }
            },
        shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(999.dp),
                        ),
                )
            }

            DashboardComposeScreen(
                state = dashboardState,
                onHistoryClick = onHistoryTabClick,
                onAboutClick = onAboutTabClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }

    if (showOverlayStatusDialog) {
        MapOverlayStatusDialog(
            dashboardState = dashboardState,
            overlayState = overlayState,
            onDismiss = { showOverlayStatusDialog = false },
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
    TrackLiquidPanel(
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
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.SUBTLE,
        shadowElevation = 4.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(
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
            Text(
                text = collapsedTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
        TrackLiquidPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            tone = TrackLiquidTone.STRONG,
            shadowElevation = 24.dp,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(
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
