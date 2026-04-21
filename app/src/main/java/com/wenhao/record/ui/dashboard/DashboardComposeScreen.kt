package com.wenhao.record.ui.dashboard

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.TrackBottomNavigationBar
import com.wenhao.record.ui.designsystem.TrackBottomTab
import com.wenhao.record.ui.designsystem.TrackGlassCard
import com.wenhao.record.ui.designsystem.TrackGlowRing
import com.wenhao.record.ui.designsystem.TrackInsetPanel
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackPrimaryButton
import com.wenhao.record.ui.designsystem.TrackRecordSpacing
import com.wenhao.record.ui.designsystem.TrackRecordStatusColors
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.designsystem.TrackStatChip
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
    var contentVisible by rememberSaveable { mutableStateOf(false) }
    
    // 入场动画触发展示
    androidx.compose.runtime.LaunchedEffect(Unit) {
        contentVisible = true
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 1. 顶部状态芯片 (浮动)
        androidx.compose.animation.AnimatedVisibility(
            visible = contentVisible,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(600)) + 
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(600)) { -it },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(TrackRecordSpacing.lg)
            ) {
                TrackGlassCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = statusContentColor(state.statusTone),
                                    shape = RoundedCornerShape(999.dp)
                                )
                        )
                        Text(
                            text = state.statusLabel.ifBlank { "GPS: SEARCHING" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 2. 核心里程光轮 (Hero)
        androidx.compose.animation.AnimatedVisibility(
            visible = contentVisible,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(800, delayMillis = 200)) + 
                    androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(800, delayMillis = 200), initialScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier.padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TrackGlassCard(
                        modifier = Modifier.size(220.dp),
                        shape = RoundedCornerShape(110.dp)
                    ) { }

                    TrackGlowRing(
                        progress = 0.65f,
                        modifier = Modifier.size(190.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.distanceText,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFeatureSettings = "tnum",
                                letterSpacing = (-2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.compose_dashboard_distance_unit),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // 3. 底部浮动面板 (指标 + 导航)
        androidx.compose.animation.AnimatedVisibility(
            visible = contentVisible,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(800, delayMillis = 400)) + 
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(800, delayMillis = 400)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(TrackRecordSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DashboardMiniGlassMetric(
                        label = stringResource(R.string.compose_dashboard_time_label),
                        value = state.durationText,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardMiniGlassMetric(
                        label = stringResource(R.string.compose_dashboard_speed_label),
                        value = state.speedText.substringBefore(" ").ifBlank { state.speedText },
                        modifier = Modifier.weight(1f)
                    )
                }

                TrackGlassCard(
                    shape = RoundedCornerShape(40.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        FilledIconButton(
                            onClick = { /* TODO */ },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = onHistoryClick,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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
private fun DashboardMiniGlassMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    TrackGlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black
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
