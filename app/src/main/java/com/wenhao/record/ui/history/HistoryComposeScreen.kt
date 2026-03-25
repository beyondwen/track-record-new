package com.wenhao.record.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.TrackQualityLevel
import com.wenhao.record.ui.designsystem.TrackActionRow
import com.wenhao.record.ui.designsystem.TrackEmptyStateCard
import com.wenhao.record.ui.designsystem.TrackMetricTile
import com.wenhao.record.ui.designsystem.TrackRecordSpacing
import com.wenhao.record.ui.designsystem.TrackScreenCard
import com.wenhao.record.ui.designsystem.TrackSectionHeading
import com.wenhao.record.ui.designsystem.TrackStatChip
import java.util.Calendar

data class HistoryScreenUiState(
    val items: List<HistoryDayItem> = emptyList(),
    val selectedDayStartMillis: Long? = null,
    val totalDistanceText: String = "",
    val totalDurationText: String = "",
    val totalCountText: String = "",
    val isTransferBusy: Boolean = false,
    val isRecordTabSelected: Boolean = false,
)

@Composable
fun HistoryComposeScreen(
    state: HistoryScreenUiState,
    onRecordClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onHistoryClick: (HistoryDayItem) -> Unit,
    onHistoryLongClick: (HistoryDayItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = TrackRecordSpacing.xl,
                    top = TrackRecordSpacing.xl,
                    end = TrackRecordSpacing.xl,
                    bottom = TrackRecordSpacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.xl),
            ) {
                item {
                    HistorySummarySection(
                        state = state,
                        onExportClick = onExportClick,
                        onImportClick = onImportClick,
                        onRecordClick = onRecordClick,
                    )
                }

                if (state.items.isEmpty()) {
                    item {
                        TrackEmptyStateCard(
                            title = stringResource(R.string.compose_history_empty_title),
                            message = stringResource(R.string.compose_history_empty_message),
                            actionLabel = stringResource(R.string.compose_history_empty_action),
                            onActionClick = onRecordClick,
                        )
                    }
                } else {
                    itemsIndexed(
                        items = state.items,
                        key = { _, item -> item.dayStartMillis },
                    ) { index, item ->
                        val previousLabel = state.items.getOrNull(index - 1)?.let { buildGroupLabel(it.dayStartMillis) }
                        val currentLabel = buildGroupLabel(item.dayStartMillis)
                        Column(verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.md)) {
                            if (index == 0 || currentLabel != previousLabel) {
                                TrackStatChip(
                                    text = currentLabel,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            HistoryDayCard(
                                item = item,
                                isSelected = item.dayStartMillis == state.selectedDayStartMillis,
                                onClick = { onHistoryClick(item) },
                                onLongClick = { onHistoryLongClick(item) },
                            )
                        }
                    }
                }
            }

            HistoryBottomBar(
                isRecordSelected = state.isRecordTabSelected,
                onRecordClick = onRecordClick,
            )
        }
    }
}

@Composable
private fun HistorySummarySection(
    state: HistoryScreenUiState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onRecordClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.xl)) {
        TrackSectionHeading(
            title = stringResource(R.string.compose_history_title),
            subtitle = stringResource(R.string.compose_history_subtitle),
        )

        TrackScreenCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TrackRecordSpacing.md),
            ) {
                TrackMetricTile(
                    label = stringResource(R.string.compose_history_metric_distance),
                    value = state.totalDistanceText,
                    modifier = Modifier.weight(1f),
                )
                TrackMetricTile(
                    label = stringResource(R.string.compose_history_metric_duration),
                    value = state.totalDurationText,
                    modifier = Modifier.weight(1f),
                )
                TrackMetricTile(
                    label = stringResource(R.string.compose_history_metric_days),
                    value = state.totalCountText,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        TrackActionRow(
            primaryText = stringResource(R.string.compose_history_import),
            onPrimaryClick = onImportClick,
            secondaryText = stringResource(R.string.compose_history_export),
            onSecondaryClick = onExportClick,
            primaryEnabled = !state.isTransferBusy,
            secondaryEnabled = !state.isTransferBusy && state.items.isNotEmpty(),
        )

        if (state.items.isEmpty()) {
            Spacer(modifier = Modifier.height(TrackRecordSpacing.sm))
        } else {
            TrackStatChip(
                text = stringResource(R.string.compose_history_total_count, state.items.size),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryDayCard(
    item: HistoryDayItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cardDescription = stringResource(
        R.string.compose_history_card_accessibility,
        item.displayTitle,
        item.summary,
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                item.quality.level == TrackQualityLevel.LOW -> MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(TrackRecordSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ),
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ic_history),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.Center),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.xs),
                ) {
                    Text(
                        text = item.displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = item.sessionCountLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TrackStatChip(
                    text = if (isSelected) {
                        stringResource(R.string.compose_history_card_selected)
                    } else {
                        stringResource(R.string.compose_history_card_open)
                    },
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Text(
                text = if (item.quality.level == TrackQualityLevel.LOW) item.quality.detail else item.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TrackRecordSpacing.sm),
            ) {
                TrackStatChip(
                    text = item.pointCountLabel,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                TrackStatChip(
                    text = item.quality.badgeLabel,
                    containerColor = qualityChipContainerColor(item.quality.level),
                    contentColor = qualityChipContentColor(item.quality.level),
                )
            }

            TrackScreenCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
                ) {
                    HistoryRoutePreviewCanvas(
                        segments = item.segments,
                        modifier = Modifier.weight(1f),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.xs)) {
                        Text(
                            text = stringResource(R.string.compose_history_card_preview_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.compose_history_card_preview_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TrackRecordSpacing.md),
            ) {
                TrackMetricTile(
                    label = stringResource(R.string.compose_history_metric_distance),
                    value = item.formattedDistance,
                    modifier = Modifier.weight(1f),
                )
                TrackMetricTile(
                    label = stringResource(R.string.compose_history_metric_duration),
                    value = item.formattedDuration,
                    modifier = Modifier.weight(1f),
                )
                TrackMetricTile(
                    label = stringResource(R.string.compose_history_metric_speed),
                    value = item.formattedSpeed,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HistoryBottomBar(
    isRecordSelected: Boolean,
    onRecordClick: () -> Unit,
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NavigationBarItem(
            selected = isRecordSelected,
            onClick = onRecordClick,
            icon = {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_tab_record),
                    contentDescription = stringResource(R.string.compose_history_record_tab),
                )
            },
            label = {
                Text(text = stringResource(R.string.compose_history_record_tab))
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        NavigationBarItem(
            selected = !isRecordSelected,
            onClick = {},
            icon = {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_tab_history),
                    contentDescription = stringResource(R.string.compose_history_list_tab),
                )
            },
            label = {
                Text(text = stringResource(R.string.compose_history_list_tab))
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun qualityChipContainerColor(level: TrackQualityLevel) = when (level) {
    TrackQualityLevel.EXCELLENT,
    TrackQualityLevel.GOOD -> MaterialTheme.colorScheme.primaryContainer
    TrackQualityLevel.FAIR -> MaterialTheme.colorScheme.surfaceVariant
    TrackQualityLevel.LOW -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun qualityChipContentColor(level: TrackQualityLevel) = when (level) {
    TrackQualityLevel.EXCELLENT,
    TrackQualityLevel.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
    TrackQualityLevel.FAIR -> MaterialTheme.colorScheme.onSurfaceVariant
    TrackQualityLevel.LOW -> MaterialTheme.colorScheme.onErrorContainer
}

@Composable
private fun buildGroupLabel(dayStartMillis: Long): String {
    val context = LocalContext.current
    val now = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val target = Calendar.getInstance().apply { timeInMillis = dayStartMillis }

    return when {
        isSameDay(target, now) -> context.getString(R.string.compose_history_day_today)
        isSameDay(target, yesterday) -> context.getString(R.string.compose_history_day_yesterday)
        target.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> {
            context.getString(R.string.compose_history_day_month, target.get(Calendar.MONTH) + 1)
        }

        else -> {
            context.getString(
                R.string.compose_history_day_year_month,
                target.get(Calendar.YEAR),
                target.get(Calendar.MONTH) + 1,
            )
        }
    }
}

private fun isSameDay(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}
