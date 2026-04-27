package com.wenhao.record.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackMetricTile
import com.wenhao.record.ui.designsystem.TrackPrimaryButton
import com.wenhao.record.ui.designsystem.TrackRecordSpacing
import com.wenhao.record.ui.designsystem.TrackRecordStatusColors
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.designsystem.TrackSecondaryButton
import com.wenhao.record.ui.designsystem.TrackStatChip

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
)

@Composable
fun DashboardComposeScreen(
    state: DashboardScreenUiState,
    onHistoryClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = state.autoTrackTitle.ifBlank { stringResource(R.string.compose_dashboard_title_idle) }
    val summary = state.autoTrackMeta.ifBlank { stringResource(R.string.compose_dashboard_meta_idle) }
    val status = state.statusLabel.ifBlank { stringResource(R.string.compose_dashboard_status_idle) }
    val speedValue = state.speedText.substringBefore(" ").ifBlank { state.speedText }

    TrackLiquidPanel(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 18.dp,
        contentPadding = PaddingValues(20.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.md),
            ) {
                TrackStatChip(
                    text = status,
                    containerColor = statusContainerColor(state.statusTone),
                    contentColor = statusContentColor(state.statusTone),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.sm),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrackMetricTile(
                    label = stringResource(R.string.compose_dashboard_primary_metric_label),
                    value = state.distanceText,
                    modifier = Modifier.weight(1f),
                )
                TrackMetricTile(
                    label = stringResource(R.string.compose_dashboard_time_label),
                    value = state.durationText,
                    modifier = Modifier.weight(1f),
                )
                TrackMetricTile(
                    label = stringResource(R.string.compose_dashboard_speed_label),
                    value = speedValue,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrackSecondaryButton(
                    text = stringResource(R.string.compose_dashboard_about),
                    onClick = onAboutClick,
                    modifier = Modifier.weight(0.42f),
                )
                TrackPrimaryButton(
                    text = stringResource(R.string.compose_dashboard_view_history),
                    onClick = onHistoryClick,
                    modifier = Modifier.weight(0.58f),
                )
            }
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

@Preview(showBackground = true, widthDp = 412, heightDp = 420)
@Composable
private fun DashboardComposeScreenPreview() {
    TrackRecordTheme {
        DashboardComposeScreen(
            state = DashboardScreenUiState(
                distanceText = "18.46",
                durationText = "01:42:18",
                speedText = "12.8 km/h",
                autoTrackTitle = "正在记录这段移动",
                autoTrackMeta = "点击结束记录后会整理并写入历史。",
                statusLabel = "记录中",
                statusTone = DashboardTone.ACTIVE,
            ),
            onHistoryClick = {},
            onAboutClick = {},
        )
    }
}
