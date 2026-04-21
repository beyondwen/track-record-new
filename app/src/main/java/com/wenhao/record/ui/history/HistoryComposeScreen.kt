package com.wenhao.record.ui.history

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.TrackQualityLevel
import com.wenhao.record.data.history.buildHistoryDayItem
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.ui.designsystem.TrackEmptyStateCard
import com.wenhao.record.ui.designsystem.TrackBottomNavigationBar
import com.wenhao.record.ui.designsystem.TrackBottomTab
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.designsystem.TrackStatChip
import com.wenhao.record.ui.designsystem.TrackAtmosphericBackground
import com.wenhao.record.ui.designsystem.trackInnerPanelBorder
import com.wenhao.record.ui.designsystem.trackInnerPanelSurface
import com.wenhao.record.ui.designsystem.trackSecondarySurface
import com.wenhao.record.ui.designsystem.trackSoftAccent
import com.wenhao.record.ui.designsystem.trackSoftOutline
import com.wenhao.record.ui.designsystem.trackSoftSurface
import java.util.Calendar

@Immutable
data class HistoryScreenUiState(
    val items: List<HistoryDayItem> = emptyList(),
    val selectedDayStartMillis: Long? = null,
    val totalDistanceText: String = "",
    val totalDurationText: String = "",
    val totalCountText: String = "",
)

@Composable
fun HistoryComposeScreen(
    state: HistoryScreenUiState,
    onRecordClick: () -> Unit,
    onHistoryClick: (HistoryDayItem) -> Unit,
    onHistoryLongClick: (HistoryDayItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val todayLabel = stringResource(R.string.compose_history_day_today)
    val yesterdayLabel = stringResource(R.string.compose_history_day_yesterday)

    val groupLabels = remember(state.items, context, todayLabel, yesterdayLabel) {
        buildGroupLabels(
            items = state.items,
            context = context,
            todayLabel = todayLabel,
            yesterdayLabel = yesterdayLabel,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 28.dp,
                    end = 20.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                item {
                    HistoryHeroSection(state = state)
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
                        val previousLabel = state.items.getOrNull(index - 1)?.let { previousItem ->
                            groupLabels[previousItem.dayStartMillis]
                        }
                        val currentLabel = requireNotNull(groupLabels[item.dayStartMillis])

                        androidx.compose.animation.AnimatedVisibility(
                            visible = true, // 初始入场
                            enter = androidx.compose.animation.fadeIn(
                                androidx.compose.animation.core.tween(600, delayMillis = index * 100)
                            ) + androidx.compose.animation.slideInVertically(
                                androidx.compose.animation.core.tween(600, delayMillis = index * 100)
                            ) { it / 2 }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (index == 0 || currentLabel != previousLabel) {
                                    HistorySectionHeader(
                                        title = if (index == 0) {
                                            stringResource(R.string.compose_history_log_title)
                                        } else {
                                            currentLabel
                                        },
                                        trailing = if (index == 0) {
                                            stringResource(R.string.compose_history_view_all)
                                        } else {
                                            null
                                        },
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
            }

            HistoryBottomBar(
                onRecordClick = onRecordClick,
            )
        }
    }
}

@Composable
private fun HistoryHeroSection(
    state: HistoryScreenUiState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DecorativeMenuGlyph()

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.compose_history_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )

                if (state.totalCountText.isNotBlank()) {
                    Text(
                        text = state.totalCountText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HistoryOverviewMetricCard(
                label = stringResource(R.string.compose_history_total_distance),
                value = state.totalDistanceText,
                modifier = Modifier.weight(1f),
            )

            HistoryOverviewMetricCard(
                label = stringResource(R.string.compose_history_total_duration),
                value = state.totalDurationText,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HistoryOverviewMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    com.wenhao.record.ui.designsystem.TrackGlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DecorativeMenuGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 38.dp, height = 38.dp)) {
        val color = Color(0xFF0B6C6D)
        val startX = size.width * 0.12f
        val endX = size.width * 0.88f
        val ys = listOf(size.height * 0.28f, size.height * 0.5f, size.height * 0.72f)
        ys.forEach { y ->
            drawLine(
                color = color,
                start = Offset(startX, y),
                end = Offset(endX, y),
                strokeWidth = 3.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun HistorySectionHeader(
    title: String,
    trailing: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
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
    val cardStateDescription = if (isSelected) {
        stringResource(R.string.compose_history_card_selected)
    } else {
        stringResource(R.string.compose_history_card_open)
    }

    com.wenhao.record.ui.designsystem.TrackGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
                stateDescription = cardStateDescription
            }
            .combinedClickable(
                onClickLabel = stringResource(R.string.compose_history_card_open_action),
                onLongClickLabel = stringResource(R.string.compose_history_card_delete_action),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                    )

                    Text(
                        text = buildHistoryMetaLine(item),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }

                TrackStatChip(
                    text = item.quality.badgeLabel,
                    containerColor = qualityChipContainerColor(item.quality.level).copy(alpha = 0.15f),
                    contentColor = qualityChipContentColor(item.quality.level),
                )
            }

            HistoryPreviewCard(
                item = item,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HistoryPreviewCard(
    item: HistoryDayItem,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(240.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
    ) {
        HistoryRoutePreviewCanvas(
            segments = item.segments,
            modifier = Modifier.fillMaxSize().padding(12.dp),
        )
    }
}

@Composable
private fun HistoryBottomBar(
    onRecordClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        com.wenhao.record.ui.designsystem.TrackGlassCard(
            shape = RoundedCornerShape(40.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                TrackBottomNavigationBar(
                    selectedTab = TrackBottomTab.HISTORY,
                    onRecordClick = onRecordClick,
                    onHistoryClick = {},
                    recordLabel = stringResource(R.string.compose_history_record_tab),
                    historyLabel = stringResource(R.string.compose_history_list_tab),
                    recordEnabled = true,
                    historyEnabled = false,
                )
            }
        }
    }
}

@Composable
private fun qualityChipContainerColor(level: TrackQualityLevel) = when (level) {
    TrackQualityLevel.EXCELLENT, TrackQualityLevel.GOOD -> MaterialTheme.colorScheme.trackSoftAccent
    TrackQualityLevel.FAIR -> MaterialTheme.colorScheme.trackSoftSurface
    TrackQualityLevel.LOW -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun qualityChipContentColor(level: TrackQualityLevel) = when (level) {
    TrackQualityLevel.EXCELLENT, TrackQualityLevel.GOOD -> MaterialTheme.colorScheme.primary
    TrackQualityLevel.FAIR -> MaterialTheme.colorScheme.onSurfaceVariant
    TrackQualityLevel.LOW -> MaterialTheme.colorScheme.onErrorContainer
}

private fun buildGroupLabels(
    items: List<HistoryDayItem>,
    context: Context,
    todayLabel: String,
    yesterdayLabel: String,
): Map<Long, String> {
    if (items.isEmpty()) return emptyMap()

    val now = Calendar.getInstance()
    val yesterday = (now.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val target = Calendar.getInstance()

    return buildMap(items.size) {
        items.forEach { item ->
            target.timeInMillis = item.dayStartMillis
            put(
                item.dayStartMillis,
                buildGroupLabel(
                    target = target,
                    context = context,
                    now = now,
                    yesterday = yesterday,
                    todayLabel = todayLabel,
                    yesterdayLabel = yesterdayLabel,
                ),
            )
        }
    }
}

private fun buildGroupLabel(
    target: Calendar,
    context: Context,
    now: Calendar,
    yesterday: Calendar,
    todayLabel: String,
    yesterdayLabel: String,
): String {
    return when {
        isSameDay(target, now) -> todayLabel
        isSameDay(target, yesterday) -> yesterdayLabel
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

@Composable
private fun buildHistoryMetaLine(item: HistoryDayItem): String {
    return stringResource(
        R.string.compose_history_card_meta,
        item.sessionCount,
        item.formattedLatestTime,
    )
}

private fun isSameDay(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

@Preview(showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun HistoryComposeScreenPreview() {
    val sampleSegment = listOf(
        TrackPoint(30.2741, 120.1551, timestampMillis = 1_000L, accuracyMeters = 6f, altitudeMeters = 24.0),
        TrackPoint(30.2761, 120.1621, timestampMillis = 61_000L, accuracyMeters = 6f, altitudeMeters = 42.0),
        TrackPoint(30.2794, 120.1682, timestampMillis = 121_000L, accuracyMeters = 6f, altitudeMeters = 61.0),
    )

    val sampleItem = buildHistoryDayItem(
        dayStartMillis = 1_742_976_000_000L,
        latestTimestamp = 1_742_980_200_000L,
        sessionCount = 1,
        totalDistanceKm = 12.6,
        totalDurationSeconds = 4_320,
        averageSpeedKmh = 10.5,
        sourceIds = listOf(1L),
        segments = listOf(sampleSegment),
    )

    TrackRecordTheme {
        HistoryComposeScreen(
            state = HistoryScreenUiState(
                items = listOf(sampleItem),
                selectedDayStartMillis = sampleItem.dayStartMillis,
                totalDistanceText = "126.4 km",
                totalDurationText = "12:48:12",
                totalCountText = "14 days",
            ),
            onRecordClick = {},
            onHistoryClick = {},
            onHistoryLongClick = {},
        )
    }
}
