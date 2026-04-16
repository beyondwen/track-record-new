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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.TrackQualityLevel
import com.wenhao.record.data.history.buildHistoryDayItem
import com.wenhao.record.data.tracking.DecisionFeedbackType
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
data class HistoryDecisionFeedbackItem(
    val eventId: Long,
    val title: String,
    val summary: String,
    val feedbackLabel: String? = null,
)

@Immutable
data class HistoryScreenUiState(
    val items: List<HistoryDayItem> = emptyList(),
    val decisionFeedbackItems: List<HistoryDecisionFeedbackItem> = emptyList(),
    val selectedDayStartMillis: Long? = null,
    val isFeedbackSheetVisible: Boolean = false,
    val totalDistanceText: String = "",
    val totalDurationText: String = "",
    val totalCountText: String = "",
    val isTransferBusy: Boolean = false,
)

@Composable
fun HistoryComposeScreen(
    state: HistoryScreenUiState,
    onRecordClick: () -> Unit,
    onBarometerClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onTrainingSampleExport: () -> Unit,
    onDecisionModelImport: () -> Unit,
    onHistoryClick: (HistoryDayItem) -> Unit,
    onHistoryLongClick: (HistoryDayItem) -> Unit,
    onDecisionFeedback: (Long) -> Unit,
    onFeedbackSubmit: (DecisionFeedbackType) -> Unit,
    onFeedbackDismiss: () -> Unit,
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
        TrackAtmosphericBackground()
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
                        onTrainingSampleExport = onTrainingSampleExport,
                        onDecisionModelImport = onDecisionModelImport,
                    )
                }

                if (state.decisionFeedbackItems.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            HistorySectionHeader(
                                title = stringResource(R.string.compose_history_feedback_title),
                                trailing = null,
                            )
                            Text(
                                text = stringResource(R.string.compose_history_feedback_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                state.decisionFeedbackItems.forEach { item ->
                                    DecisionFeedbackCard(
                                        item = item,
                                        onClick = { onDecisionFeedback(item.eventId) },
                                    )
                                }
                            }
                        }
                    }
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
                onRecordClick = onRecordClick,
                onBarometerClick = onBarometerClick,
            )
        }

        if (state.isFeedbackSheetVisible) {
            DecisionFeedbackBottomSheet(
                onDismiss = onFeedbackDismiss,
                onFeedbackSubmit = onFeedbackSubmit,
            )
        }
    }
}

@Composable
private fun DecisionFeedbackCard(
    item: HistoryDecisionFeedbackItem,
    onClick: () -> Unit,
) {
    TrackLiquidPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tone = TrackLiquidTone.SUBTLE,
        borderColor = MaterialTheme.colorScheme.trackInnerPanelBorder.copy(alpha = 0.18f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TrackStatChip(
                    text = if (item.feedbackLabel != null) {
                        feedbackLabelText(item.feedbackLabel)
                    } else {
                        stringResource(R.string.compose_history_feedback_pending)
                    },
                    containerColor = if (item.feedbackLabel == null) {
                        MaterialTheme.colorScheme.trackSoftSurface
                    } else {
                        MaterialTheme.colorScheme.trackSoftAccent
                    },
                    contentColor = if (item.feedbackLabel == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (item.feedbackLabel == null) {
                        stringResource(R.string.compose_history_feedback_action)
                    } else {
                        stringResource(R.string.compose_history_feedback_update_action)
                    },
                )
            }
        }
    }
}

@Composable
private fun feedbackLabelText(raw: String): String {
    return when (raw) {
        DecisionFeedbackType.START_TOO_EARLY.name -> stringResource(R.string.compose_history_feedback_start_early)
        DecisionFeedbackType.START_TOO_LATE.name -> stringResource(R.string.compose_history_feedback_start_late)
        DecisionFeedbackType.STOP_TOO_EARLY.name -> stringResource(R.string.compose_history_feedback_stop_early)
        DecisionFeedbackType.STOP_TOO_LATE.name -> stringResource(R.string.compose_history_feedback_stop_late)
        DecisionFeedbackType.CORRECT.name -> stringResource(R.string.compose_history_feedback_correct)
        else -> raw
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecisionFeedbackBottomSheet(
    onDismiss: () -> Unit,
    onFeedbackSubmit: (DecisionFeedbackType) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.compose_history_feedback_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.compose_history_feedback_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DecisionFeedbackActionButton(
                label = stringResource(R.string.compose_history_feedback_start_early),
                onClick = { onFeedbackSubmit(DecisionFeedbackType.START_TOO_EARLY) },
            )
            DecisionFeedbackActionButton(
                label = stringResource(R.string.compose_history_feedback_start_late),
                onClick = { onFeedbackSubmit(DecisionFeedbackType.START_TOO_LATE) },
            )
            DecisionFeedbackActionButton(
                label = stringResource(R.string.compose_history_feedback_stop_early),
                onClick = { onFeedbackSubmit(DecisionFeedbackType.STOP_TOO_EARLY) },
            )
            DecisionFeedbackActionButton(
                label = stringResource(R.string.compose_history_feedback_stop_late),
                onClick = { onFeedbackSubmit(DecisionFeedbackType.STOP_TOO_LATE) },
            )
            DecisionFeedbackActionButton(
                label = stringResource(R.string.compose_history_feedback_correct),
                onClick = { onFeedbackSubmit(DecisionFeedbackType.CORRECT) },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DecisionFeedbackActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label)
    }
}

@Composable
private fun HistoryHeroSection(
    state: HistoryScreenUiState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onTrainingSampleExport: () -> Unit,
    onDecisionModelImport: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HistoryActionButton(
                text = stringResource(R.string.compose_history_training_export),
                iconMode = HistoryActionIconMode.UP,
                onClick = onTrainingSampleExport,
                enabled = !state.isTransferBusy && state.decisionFeedbackItems.isNotEmpty(),
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.trackSecondarySurface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                borderColor = Color.Transparent,
            )
            HistoryActionButton(
                text = stringResource(R.string.compose_history_model_import),
                iconMode = HistoryActionIconMode.DOWN,
                onClick = onDecisionModelImport,
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
    TrackLiquidPanel(
        modifier = modifier
            .height(56.dp)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(20.dp),
        tone = if (containerColor == MaterialTheme.colorScheme.primary) {
            TrackLiquidTone.ACCENT
        } else {
            TrackLiquidTone.STANDARD
        },
        shadowElevation = 0.dp,
        borderColor = if (borderColor == Color.Transparent) {
            Color.Transparent
        } else {
            borderColor
        },
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            HistoryTransferGlyph(
                direction = iconMode,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.52f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.52f),
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
    Canvas(modifier = modifier.size(12.dp)) {
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

    TrackLiquidPanel(
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
        shape = RoundedCornerShape(34.dp),
        tone = if (isSelected) TrackLiquidTone.ACCENT else TrackLiquidTone.STRONG,
        borderColor = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            item.quality.level == TrackQualityLevel.LOW -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.trackInnerPanelBorder.copy(alpha = 0.22f)
        },
        shadowElevation = if (isSelected) 10.dp else 4.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Column(
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
    TrackLiquidPanel(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tone = TrackLiquidTone.SUBTLE,
        borderColor = MaterialTheme.colorScheme.trackInnerPanelBorder.copy(alpha = 0.14f),
        contentPadding = PaddingValues(8.dp),
    ) {
        TrackLiquidPanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp),
            shape = RoundedCornerShape(26.dp),
            tone = TrackLiquidTone.STANDARD,
            borderColor = MaterialTheme.colorScheme.trackInnerPanelBorder.copy(alpha = 0.1f),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(26.dp)),
            ) {
                HistoryRoutePreviewCanvas(
                    segments = item.segments,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun HistoryBottomBar(
    onRecordClick: () -> Unit,
    onBarometerClick: () -> Unit,
) {
    TrackLiquidPanel(
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TrackBottomNavigationBar(
                selectedTab = TrackBottomTab.HISTORY,
                onRecordClick = onRecordClick,
                onHistoryClick = {},
                onBarometerClick = onBarometerClick,
                recordLabel = stringResource(R.string.compose_history_record_tab),
                historyLabel = stringResource(R.string.compose_history_list_tab),
                recordEnabled = true,
                historyEnabled = false,
                barometerEnabled = true,
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

private fun buildGroupLabels(
    items: List<HistoryDayItem>,
    context: Context,
    todayLabel: String,
    yesterdayLabel: String,
): Map<Long, String> {
    if (items.isEmpty()) return emptyMap()

    val now = Calendar.getInstance()
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
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
            onBarometerClick = {},
            onExportClick = {},
            onImportClick = {},
            onTrainingSampleExport = {},
            onDecisionModelImport = {},
            onHistoryClick = {},
            onHistoryLongClick = {},
            onDecisionFeedback = {},
            onFeedbackSubmit = {},
            onFeedbackDismiss = {},
        )
    }
}
