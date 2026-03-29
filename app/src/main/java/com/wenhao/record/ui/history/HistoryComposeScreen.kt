package com.wenhao.record.ui.history

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.TrackQualityLevel
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.ui.designsystem.TrackEmptyStateCard
import com.wenhao.record.ui.designsystem.TrackBottomNavigationBar
import com.wenhao.record.ui.designsystem.TrackBottomTab
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.designsystem.TrackStatChip
import com.wenhao.record.ui.designsystem.trackPageBackground
import com.wenhao.record.ui.designsystem.trackSecondarySurface
import com.wenhao.record.ui.designsystem.trackSoftAccent
import com.wenhao.record.ui.designsystem.trackSoftOutline
import com.wenhao.record.ui.designsystem.trackSoftSurface
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
        color = MaterialTheme.colorScheme.trackPageBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = 28.dp,
                    end = 24.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                item {
                    HistoryHeroSection(
                        state = state,
                        onExportClick = onExportClick,
                        onImportClick = onImportClick,
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

            HistoryBottomBar(
                isRecordSelected = state.isRecordTabSelected,
                onRecordClick = onRecordClick,
            )
        }
    }
}

@Composable
private fun HistoryHeroSection(
    state: HistoryScreenUiState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DecorativeMenuGlyph()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.compose_history_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (state.totalCountText.isNotBlank()) {
                    Text(
                        text = state.totalCountText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HistoryActionButton(
                text = stringResource(R.string.compose_history_export),
                iconMode = HistoryActionIconMode.UP,
                onClick = onExportClick,
                enabled = !state.isTransferBusy && state.items.isNotEmpty(),
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                borderColor = Color.Transparent,
            )
            HistoryActionButton(
                text = stringResource(R.string.compose_history_import),
                iconMode = HistoryActionIconMode.DOWN,
                onClick = onImportClick,
                enabled = !state.isTransferBusy,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.trackSecondarySurface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                borderColor = Color.Transparent,
            )
        }
    }
}

private enum class HistoryActionIconMode {
    UP,
    DOWN,
}

@Composable
private fun HistoryActionButton(
    text: String,
    iconMode: HistoryActionIconMode,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(100.dp)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
                color = if (enabled) containerColor else containerColor.copy(alpha = 0.48f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.52f),
        shape = RoundedCornerShape(34.dp),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 10.dp,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            HistoryTransferGlyph(
                direction = iconMode,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.52f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
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
private fun HistoryTransferGlyph(
    direction: HistoryActionIconMode,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val centerX = size.width / 2f
        val topY = size.height * 0.18f
        val bottomY = size.height * 0.82f
        val arrowTipY = if (direction == HistoryActionIconMode.UP) topY else bottomY
        val shaftTop = if (direction == HistoryActionIconMode.UP) bottomY else topY
        val shaftBottom = if (direction == HistoryActionIconMode.UP) topY + size.height * 0.2f else bottomY - size.height * 0.2f

        drawLine(
            color = color,
            start = Offset(centerX, shaftTop),
            end = Offset(centerX, shaftBottom),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, arrowTipY),
            end = Offset(centerX - size.width * 0.18f, arrowTipY + if (direction == HistoryActionIconMode.UP) size.height * 0.18f else -size.height * 0.18f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, arrowTipY),
            end = Offset(centerX + size.width * 0.18f, arrowTipY + if (direction == HistoryActionIconMode.UP) size.height * 0.18f else -size.height * 0.18f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.24f, bottomY),
            end = Offset(size.width * 0.76f, bottomY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
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

    Card(
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
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        shape = RoundedCornerShape(34.dp),
        border = BorderStroke(
            width = if (isSelected) 1.6.dp else 1.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                item.quality.level == TrackQualityLevel.LOW -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                else -> MaterialTheme.colorScheme.trackSoftOutline
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 14.dp else 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.displayTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = buildHistoryMetaLine(item),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TrackStatChip(
                    text = item.quality.badgeLabel,
                    containerColor = qualityChipContainerColor(item.quality.level),
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
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.trackSoftSurface,
                    ),
                ),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .background(
                    color = MaterialTheme.colorScheme.trackSoftSurface,
                    shape = RoundedCornerShape(26.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(26.dp)),
            ) {
                HistoryRoutePreviewCanvas(
                    segments = item.segments,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .padding(18.dp),
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(999.dp),
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PreviewTrailGlyph()
                    Text(
                        text = stringResource(R.string.compose_history_card_preview_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewTrailGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 26.dp, height = 16.dp)) {
        val color = Color(0xFF115D77)
        val stroke = 2.5.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.08f, size.height * 0.75f),
            end = Offset(size.width * 0.36f, size.height * 0.3f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.36f, size.height * 0.3f),
            end = Offset(size.width * 0.56f, size.height * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.56f, size.height * 0.72f),
            end = Offset(size.width * 0.88f, size.height * 0.24f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(color = color, radius = 2.3.dp.toPx(), center = Offset(size.width * 0.08f, size.height * 0.75f))
        drawCircle(color = color, radius = 2.3.dp.toPx(), center = Offset(size.width * 0.88f, size.height * 0.24f))
    }
}

@Composable
private fun HistoryBottomBar(
    isRecordSelected: Boolean,
    onRecordClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.985f),
        tonalElevation = 6.dp,
        shadowElevation = 20.dp,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            TrackBottomNavigationBar(
                selectedTab = if (isRecordSelected) {
                    TrackBottomTab.RECORD
                } else {
                    TrackBottomTab.HISTORY
                },
                onRecordClick = onRecordClick,
                onHistoryClick = {},
                recordLabel = stringResource(R.string.compose_history_record_tab),
                historyLabel = stringResource(R.string.compose_history_list_tab),
                recordEnabled = !isRecordSelected,
                historyEnabled = false,
            )
        }
    }
}

@Composable
private fun qualityChipContainerColor(level: TrackQualityLevel) = when (level) {
    TrackQualityLevel.EXCELLENT,
    TrackQualityLevel.GOOD -> MaterialTheme.colorScheme.trackSoftAccent
    TrackQualityLevel.FAIR -> MaterialTheme.colorScheme.trackSoftSurface
    TrackQualityLevel.LOW -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun qualityChipContentColor(level: TrackQualityLevel) = when (level) {
    TrackQualityLevel.EXCELLENT,
    TrackQualityLevel.GOOD -> MaterialTheme.colorScheme.primary
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
    val sampleItem = HistoryDayItem(
        dayStartMillis = 1_742_976_000_000L,
        latestTimestamp = 1_742_980_200_000L,
        sessionCount = 1,
        totalDistanceKm = 12.6,
        totalDurationSeconds = 4_320,
        averageSpeedKmh = 10.5,
        sourceIds = listOf(1L),
        segments = listOf(sampleSegment),
        points = sampleSegment,
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
            onExportClick = {},
            onImportClick = {},
            onHistoryClick = {},
            onHistoryLongClick = {},
        )
    }
}
