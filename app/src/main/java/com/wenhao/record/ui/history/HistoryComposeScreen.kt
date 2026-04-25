package com.wenhao.record.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDaySummaryItem
import com.wenhao.record.data.history.buildHistoryDayItem
import com.wenhao.record.data.history.toSummaryItem
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.ui.designsystem.TrackEmptyStateCard
import com.wenhao.record.ui.designsystem.TrackRecordTheme

@Immutable
data class HistoryScreenUiState(
    val items: List<HistoryDaySummaryItem> = emptyList(),
    val selectedDayStartMillis: Long? = null,
    val totalDistanceText: String = "",
    val totalDurationText: String = "",
    val totalCountText: String = "",
)

@Composable
fun HistoryComposeScreen(
    state: HistoryScreenUiState,
    onRecordClick: () -> Unit,
    onHistoryClick: (HistoryDaySummaryItem) -> Unit,
    onHistoryLongClick: (HistoryDaySummaryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    top = 24.dp,
                    end = 20.dp,
                    bottom = 118.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
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
                        val previousDayStart = state.items.getOrNull(index - 1)?.dayStartMillis
                        val nextDayStart = state.items.getOrNull(index + 1)?.dayStartMillis
                        val isFirstInGroup = index == 0 || item.dayStartMillis != previousDayStart
                        val isLastInGroup = index == state.items.lastIndex || item.dayStartMillis != nextDayStart

                        Column(
                            modifier = Modifier.padding(top = if (isFirstInGroup && index != 0) 12.dp else 0.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            HistoryDayListRow(
                                item = item,
                                isSelected = item.dayStartMillis == state.selectedDayStartMillis,
                                isFirstInGroup = isFirstInGroup,
                                isLastInGroup = isLastInGroup,
                                onClick = { onHistoryClick(item) },
                                onLongClick = { onHistoryLongClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeroSection(
    state: HistoryScreenUiState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.compose_history_log_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.compose_history_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                )
            }
            if (state.totalCountText.isNotBlank()) {
                Text(
                    text = state.totalCountText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryDayListRow(
    item: HistoryDaySummaryItem,
    isSelected: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val model = buildHistoryListItemModel(item)
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
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val rowShape = RoundedCornerShape(
        topStart = if (isFirstInGroup) 24.dp else 4.dp,
        topEnd = if (isFirstInGroup) 24.dp else 4.dp,
        bottomEnd = if (isLastInGroup) 24.dp else 4.dp,
        bottomStart = if (isLastInGroup) 24.dp else 4.dp,
    )
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBackground)
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
                stateDescription = cardStateDescription
            }
            .combinedClickable(
                onClickLabel = stringResource(R.string.compose_history_card_open_action),
                onLongClickLabel = stringResource(R.string.compose_history_card_delete_action),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = 12.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        HistoryListClockIcon(
            modifier = Modifier.padding(top = 17.dp),
            color = contentColor,
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp, bottom = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = model.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(
                    modifier = Modifier.width(64.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = model.distanceText,
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        text = model.distanceUnit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            if (!isLastInGroup) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
                )
            }
        }
    }
}

@Composable
private fun HistoryListClockIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier.size(34.dp)) {
        val stroke = 2.2.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.38f
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, center.y - radius * 0.54f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x + radius * 0.44f, center.y + radius * 0.25f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawArc(
            color = color,
            startAngle = 132f,
            sweepAngle = 50f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 1.2f, center.y - radius * 1.2f),
            size = androidx.compose.ui.geometry.Size(radius * 2.4f, radius * 2.4f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = StrokeCap.Round,
            ),
        )
    }
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
    ).toSummaryItem()

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
