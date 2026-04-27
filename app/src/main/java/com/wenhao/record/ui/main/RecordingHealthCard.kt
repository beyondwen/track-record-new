package com.wenhao.record.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.TrackInsetPanel
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackPrimaryButton
import com.wenhao.record.ui.designsystem.TrackSecondaryButton
import com.wenhao.record.ui.designsystem.TrackStatChip

@Composable
fun RecordingHealthCard(
    state: RecordingHealthUiState,
    onPrimaryActionClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chromeModel = buildHomeRecordChromeModel(state)
    TrackLiquidPanel(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.SUBTLE,
        shadowElevation = 7.dp,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                TrackStatChip(
                    text = overallStatusLabel(state.overallStatus),
                    containerColor = overallStatusContainerColor(state.overallStatus),
                    contentColor = overallStatusContentColor(state.overallStatus),
                )
            }

            Text(
                text = state.summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            TrackInsetPanel(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                accented = state.overallStatus != RecordingHealthOverallStatus.READY,
            ) {
                Text(
                    text = if (chromeModel.spotlightItem == null) "当前状态" else "当前关注",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (chromeModel.spotlightItem == null) {
                    Text(
                        text = stringResource(R.string.compose_dashboard_health_all_clear),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    CompactHighlightRow(item = chromeModel.spotlightItem)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackPrimaryButton(
                    text = state.primaryActionText,
                    onClick = onPrimaryActionClick,
                    modifier = Modifier.weight(1f),
                    minHeight = 44.dp,
                )
                TrackSecondaryButton(
                    text = chromeModel.secondaryActionText,
                    onClick = onDetailsClick,
                    modifier = Modifier.weight(1f),
                    minHeight = 44.dp,
                )
            }
        }
    }
}

@Composable
fun RecordingHealthDiagnosticsDialog(
    state: RecordingHealthUiState,
    onItemActionClick: (RecordingHealthAction) -> Unit,
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
            contentPadding = PaddingValues(18.dp),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.compose_dashboard_health_diagnostics_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                TrackInsetPanel(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    accented = state.overallStatus != RecordingHealthOverallStatus.READY,
                ) {
                    state.items.forEach { item ->
                        RecordingHealthItemRow(
                            item = item,
                            onClick = { onItemActionClick(item.action) },
                        )
                    }
                }
                TrackInsetPanel {
                    DiagnosticLine(
                        label = stringResource(R.string.compose_dashboard_health_diagnostics_recording_status),
                        value = state.diagnosticSummary.recordingStatusText,
                    )
                    DiagnosticLine(
                        label = stringResource(R.string.compose_dashboard_health_diagnostics_latest_point),
                        value = state.diagnosticSummary.latestPointText,
                    )
                    DiagnosticLine(
                        label = stringResource(R.string.compose_dashboard_health_diagnostics_latest_event),
                        value = state.diagnosticSummary.latestEventText,
                    )
                    DiagnosticLine(
                        label = stringResource(R.string.compose_dashboard_health_diagnostics_service),
                        value = state.diagnosticSummary.serviceText,
                    )
                }
                TrackPrimaryButton(
                    text = stringResource(R.string.compose_dashboard_dialog_confirm),
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CompactHighlightRow(item: RecordingHealthItemUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.riskText ?: item.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TrackStatChip(
            text = item.statusText,
            containerColor = compactHighlightContainerColor(item.severity),
            contentColor = compactHighlightContentColor(item.severity),
        )
    }
}

@Composable
private fun RecordingHealthItemRow(
    item: RecordingHealthItemUiState,
    onClick: () -> Unit,
) {
    val (containerColor, contentColor) = when (item.severity) {
        RecordingHealthItemSeverity.NORMAL -> MaterialTheme.colorScheme.surfaceContainer to
            MaterialTheme.colorScheme.onSurface

        RecordingHealthItemSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer

        RecordingHealthItemSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        onClick = onClick,
        enabled = item.action != RecordingHealthAction.NO_OP,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.bodySmall,
                )
                item.riskText?.let { riskText ->
                    Text(
                        text = riskText,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.8f),
                    )
                }
            }
            if (item.action != RecordingHealthAction.NO_OP) {
                TrackSecondaryButton(
                    text = stringResource(R.string.compose_dashboard_health_item_action_short),
                    onClick = onClick,
                    modifier = Modifier.widthIn(min = 88.dp),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun overallStatusLabel(status: RecordingHealthOverallStatus): String {
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
private fun compactHighlightContainerColor(severity: RecordingHealthItemSeverity) = when (severity) {
    RecordingHealthItemSeverity.NORMAL -> MaterialTheme.colorScheme.secondaryContainer
    RecordingHealthItemSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
    RecordingHealthItemSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun compactHighlightContentColor(severity: RecordingHealthItemSeverity) = when (severity) {
    RecordingHealthItemSeverity.NORMAL -> MaterialTheme.colorScheme.onSecondaryContainer
    RecordingHealthItemSeverity.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
    RecordingHealthItemSeverity.ERROR -> MaterialTheme.colorScheme.onErrorContainer
}

@Composable
private fun overallStatusContainerColor(status: RecordingHealthOverallStatus) = when (status) {
    RecordingHealthOverallStatus.READY -> MaterialTheme.colorScheme.secondaryContainer
    RecordingHealthOverallStatus.DEGRADED -> MaterialTheme.colorScheme.tertiaryContainer
    RecordingHealthOverallStatus.BLOCKED -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun overallStatusContentColor(status: RecordingHealthOverallStatus) = when (status) {
    RecordingHealthOverallStatus.READY -> MaterialTheme.colorScheme.onSecondaryContainer
    RecordingHealthOverallStatus.DEGRADED -> MaterialTheme.colorScheme.onTertiaryContainer
    RecordingHealthOverallStatus.BLOCKED -> MaterialTheme.colorScheme.onErrorContainer
}
