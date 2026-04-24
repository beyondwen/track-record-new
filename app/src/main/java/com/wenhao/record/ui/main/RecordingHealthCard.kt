package com.wenhao.record.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.TrackInsetPanel
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackPrimaryButton
import com.wenhao.record.ui.designsystem.TrackStatChip

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RecordingHealthCard(
    state: RecordingHealthUiState,
    onPrimaryActionClick: () -> Unit,
    onItemClick: (RecordingHealthAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    TrackLiquidPanel(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 18.dp,
        contentPadding = PaddingValues(16.dp),
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
                TrackStatChip(text = overallStatusLabel(state.overallStatus))
            }

            Text(
                text = state.summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.items.forEach { item ->
                    RecordingHealthItemChip(
                        item = item,
                        onClick = { onItemClick(item.action) },
                    )
                }
            }

            TrackPrimaryButton(
                text = state.primaryActionText,
                onClick = onPrimaryActionClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun RecordingHealthDiagnosticsDialog(
    state: RecordingHealthUiState,
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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.compose_dashboard_health_diagnostics_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                TrackInsetPanel {
                    DiagnosticLine(
                        label = stringResource(R.string.compose_dashboard_health_diagnostics_phase),
                        value = state.diagnosticSummary.phaseText,
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
private fun RecordingHealthItemChip(
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
        Column(
            modifier = Modifier
                .widthIn(min = 132.dp, max = 180.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
            if (item.action != RecordingHealthAction.NO_OP) {
                Text(
                    text = stringResource(R.string.compose_dashboard_health_item_action_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.75f),
                )
            }
        }
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
