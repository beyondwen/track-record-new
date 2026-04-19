package com.wenhao.record.ui.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.TrackBottomNavigationBar
import com.wenhao.record.ui.designsystem.TrackBottomTab
import com.wenhao.record.ui.designsystem.TrackInsetPanel
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackPrimaryButton
import com.wenhao.record.ui.designsystem.TrackRecordSpacing
import com.wenhao.record.ui.designsystem.TrackRecordStatusColors
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.designsystem.TrackStatChip
import com.wenhao.record.ui.designsystem.trackGlowPrimary
import com.wenhao.record.ui.designsystem.trackInnerPanelSurface
import com.wenhao.record.ui.designsystem.trackSecondarySurface

enum class DashboardTone {
    ACTIVE,
    WARNING,
    MUTED,
    SUCCESS,
}

data class DashboardScreenUiState(
    val isRecordTabSelected: Boolean = true,
    val distanceText: String = "0.00",
    val durationText: String = "00:00",
    val speedText: String = "0.0 km/h",
    val autoTrackTitle: String = "",
    val autoTrackMeta: String = "",
    val statusLabel: String = "",
    val statusTone: DashboardTone = DashboardTone.MUTED,
    val recordIconRes: Int = R.drawable.ic_play_dashboard,
    val isPulseActive: Boolean = false,
    val controlTitle: String = "",
    val controlBody: String = "",
)

@Composable
fun DashboardComposeScreen(
    state: DashboardScreenUiState,
    overlayState: DashboardOverlayUiState,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStatusDialog by rememberSaveable { mutableStateOf(false) }

    TrackLiquidPanel(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 10.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(999.dp),
                    ),
            )

            DashboardHeroSection(
                state = state,
                onStatusClick = { showStatusDialog = true },
            )

            DashboardStatusPanel(state = state)

            TrackBottomNavigationBar(
                selectedTab = TrackBottomTab.RECORD,
                onRecordClick = {},
                onHistoryClick = onHistoryClick,
                recordLabel = stringResource(R.string.compose_dashboard_record),
                historyLabel = stringResource(R.string.compose_dashboard_history),
            )
        }
    }

    if (showStatusDialog) {
        DashboardStatusDialog(
            state = state,
            overlayState = overlayState,
            onDismiss = { showStatusDialog = false },
        )
    }
}

@Composable
private fun DashboardHeroSection(
    state: DashboardScreenUiState,
    onStatusClick: () -> Unit,
) {
    val accessibilitySummary = stringResource(
        R.string.compose_dashboard_accessibility_summary,
        state.distanceText,
        state.durationText,
        state.speedText,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilitySummary
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TrackStatChip(
            text = stringResource(R.string.compose_dashboard_primary_metric_label),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
        ) {
            Text(
                text = state.distanceText,
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.compose_dashboard_distance_unit),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Text(
            text = state.autoTrackMeta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DashboardMiniMetric(
                label = stringResource(R.string.compose_dashboard_time_label),
                value = state.durationText,
                modifier = Modifier.weight(1f),
            )
            DashboardMiniMetric(
                label = stringResource(R.string.compose_dashboard_speed_label),
                value = state.speedText.substringBefore(" ").ifBlank { state.speedText },
                modifier = Modifier.weight(1f),
            )
        }

        DashboardStatusEntry(
            title = state.autoTrackTitle,
            badge = state.statusLabel,
            tone = state.statusTone,
            onClick = onStatusClick,
        )
    }
}

@Composable
private fun DashboardMiniMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    TrackLiquidPanel(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        tone = TrackLiquidTone.SUBTLE,
        shadowElevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DashboardStatusEntry(
    title: String,
    badge: String,
    tone: DashboardTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openHint = stringResource(R.string.compose_dashboard_status_entry_hint)
    TrackLiquidPanel(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.compose_dashboard_dialog_open),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = title
                contentDescription = "$title，$badge，$openHint"
            },
        shape = RoundedCornerShape(22.dp),
        tone = TrackLiquidTone.STANDARD,
        shadowElevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = statusContentColor(tone),
                        shape = RoundedCornerShape(999.dp),
                    ),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = openHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (badge.isNotBlank()) {
                TrackStatChip(
                    text = badge,
                    containerColor = statusContainerColor(tone),
                    contentColor = statusContentColor(tone),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DashboardStatusDialog(
    state: DashboardScreenUiState,
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
            shape = RoundedCornerShape(30.dp),
            tone = TrackLiquidTone.STRONG,
            shadowElevation = 18.dp,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = statusContainerColor(state.statusTone),
                                shape = RoundedCornerShape(18.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tab_record),
                            contentDescription = null,
                            tint = statusContentColor(state.statusTone),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TrackStatChip(
                            text = stringResource(R.string.compose_dashboard_dialog_badge),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = state.autoTrackTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        TrackStatChip(
                            text = state.statusLabel,
                            containerColor = statusContainerColor(state.statusTone),
                            contentColor = statusContentColor(state.statusTone),
                        )
                    }

                    FilledIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.trackInnerPanelSurface.copy(alpha = 0.78f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.compose_dashboard_dialog_close),
                        )
                    }
                }

                Text(
                    text = state.autoTrackMeta,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TrackInsetPanel {
                    Text(
                        text = stringResource(R.string.compose_dashboard_dialog_summary_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.compose_dashboard_dialog_summary_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TrackInsetPanel(accented = true) {
                    DialogInfoRow(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_locate_dashboard),
                                contentDescription = null,
                                tint = statusContentColor(overlayState.gpsTone),
                            )
                        },
                        label = stringResource(R.string.compose_dashboard_dialog_gps_label),
                        value = overlayState.gpsLabel,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    DialogInfoRow(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_history),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        label = overlayState.diagnosticsTitle.ifBlank {
                            stringResource(R.string.compose_dashboard_dialog_diagnostics_label)
                        },
                        value = overlayState.diagnosticsCompactBody,
                    )
                }

                TrackPrimaryButton(
                    text = stringResource(R.string.compose_dashboard_dialog_confirm),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DialogInfoRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.trackInnerPanelSurface.copy(alpha = 0.76f),
                    shape = RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value.ifBlank { stringResource(R.string.compose_dashboard_diagnostics_loading) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DashboardStatusPanel(
    state: DashboardScreenUiState,
) {
    TrackLiquidPanel(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = state.controlTitle
            },
        shape = RoundedCornerShape(22.dp),
        tone = TrackLiquidTone.STANDARD,
        shadowElevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.compose_dashboard_panel_caption),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.controlBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TrackStatChip(
                text = state.controlTitle,
                containerColor = MaterialTheme.colorScheme.trackSecondarySurface.copy(alpha = 0.78f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DashboardRecordIndicator(
    iconRes: Int,
    isPulseActive: Boolean,
    tone: DashboardTone,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dashboardPulse")
    val haloScaleState = if (isPulseActive) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "haloScale",
        )
    } else {
        null
    }
    val haloAlphaState = if (isPulseActive) {
        infiniteTransition.animateFloat(
            initialValue = 0.32f,
            targetValue = 0.78f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "haloAlpha",
        )
    } else {
        null
    }
    val accentColor = when (tone) {
        DashboardTone.ACTIVE -> MaterialTheme.colorScheme.primary
        DashboardTone.WARNING -> MaterialTheme.colorScheme.tertiary
        DashboardTone.MUTED -> MaterialTheme.colorScheme.secondary
        DashboardTone.SUCCESS -> TrackRecordStatusColors.Success
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .graphicsLayer {
                    val scale = haloScaleState?.value ?: 1f
                    scaleX = scale
                    scaleY = scale
                    alpha = haloAlphaState?.value ?: 0.28f
                }
                .background(
            color = accentColor.copy(alpha = 0.18f),
            shape = CircleShape,
        ),
        )
        Canvas(modifier = Modifier.size(92.dp)) {
            drawCircle(
                color = accentColor.copy(alpha = 0.12f),
            )
        }
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(
                    color = accentColor,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun statusContainerColor(tone: DashboardTone): Color = when (tone) {
    DashboardTone.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
    DashboardTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
    DashboardTone.MUTED -> MaterialTheme.colorScheme.surface
    DashboardTone.SUCCESS -> TrackRecordStatusColors.Success.copy(alpha = 0.14f)
}

@Composable
private fun statusContentColor(tone: DashboardTone): Color = when (tone) {
    DashboardTone.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
    DashboardTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
    DashboardTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
    DashboardTone.SUCCESS -> TrackRecordStatusColors.Success
}

@Preview(showBackground = true, widthDp = 412, heightDp = 560)
@Composable
private fun DashboardComposeScreenPreview() {
    TrackRecordTheme {
        DashboardComposeScreen(
            state = DashboardScreenUiState(
                distanceText = "18.46",
                durationText = "01:42",
                speedText = "12.8 km/h",
                autoTrackTitle = "Smart tracking is standing by",
                autoTrackMeta = "The app will quietly wait in the background and begin a trip when clear movement is detected.",
                statusLabel = "Background standby",
                statusTone = DashboardTone.ACTIVE,
                isPulseActive = true,
            ),
            overlayState = DashboardOverlayUiState(
                gpsLabel = "GPS ready",
                gpsTone = DashboardTone.SUCCESS,
                diagnosticsTitle = "Recording diagnostics",
                diagnosticsCompactBody = "Background tracking is active and waiting for clear movement.",
            ),
            onHistoryClick = {},
        )
    }
}
