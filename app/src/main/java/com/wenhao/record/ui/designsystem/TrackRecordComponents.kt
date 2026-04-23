package com.wenhao.record.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wenhao.record.R

enum class TrackBottomTab {
    RECORD,
    HISTORY,
    SETTINGS,
}

enum class TrackLiquidTone {
    STRONG,
    STANDARD,
    SUBTLE,
    ACCENT,
}

/**
 * Material 3 风格的表面容器。
 * 使用 tonalElevation 表示层级，替代旧版的液态渐变效果。
 */
@Composable
fun TrackLiquidPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    tone: TrackLiquidTone = TrackLiquidTone.STANDARD,
    shadowElevation: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val tonalElevation = when (tone) {
        TrackLiquidTone.STRONG -> 3.dp
        TrackLiquidTone.STANDARD -> 2.dp
        TrackLiquidTone.SUBTLE -> 1.dp
        TrackLiquidTone.ACCENT -> 2.dp
    }
    val border = if (borderColor != Color.Transparent) {
        BorderStroke(1.dp, borderColor)
    } else {
        null
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun TrackScreenCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(TrackRecordSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
            content = content,
        )
    }
}

@Composable
fun TrackSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier.semantics { heading() },
        verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TrackStatChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun TrackMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val metricDescription = "$label $value"
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = metricDescription
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.xs),
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
            )
        }
    }
}

@Composable
fun TrackInsetPanel(
    modifier: Modifier = Modifier,
    accented: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (accented) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.sm),
            content = content,
        )
    }
}

@Composable
fun TrackBottomNavigationBar(
    selectedTab: TrackBottomTab,
    onRecordClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    recordLabel: String = stringResource(R.string.compose_dashboard_record),
    historyLabel: String = stringResource(R.string.compose_dashboard_history),
    settingsLabel: String = stringResource(R.string.compose_dashboard_about),
    recordEnabled: Boolean = true,
    historyEnabled: Boolean = true,
    settingsEnabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .widthIn(max = 420.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 2.dp,
        shadowElevation = 18.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrackBottomNavigationItem(
                selected = selectedTab == TrackBottomTab.RECORD,
                onClick = onRecordClick,
                enabled = recordEnabled,
                label = recordLabel,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tab_record),
                    contentDescription = null,
                )
            }
            TrackBottomNavigationItem(
                selected = selectedTab == TrackBottomTab.HISTORY,
                onClick = onHistoryClick,
                enabled = historyEnabled,
                label = historyLabel,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tab_history),
                    contentDescription = null,
                )
            }
            TrackBottomNavigationItem(
                selected = selectedTab == TrackBottomTab.SETTINGS,
                onClick = onSettingsClick,
                enabled = settingsEnabled,
                label = settingsLabel,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tab_settings),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun RowScope.TrackBottomNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    label: String,
    icon: @Composable () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor,
        onClick = onClick,
        enabled = enabled,
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun TrackPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun TrackSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun TrackTertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun TrackEmptyStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    TrackScreenCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onActionClick != null) {
                TrackPrimaryButton(
                    text = actionLabel,
                    onClick = onActionClick,
                )
            }
        }
    }
}

@Composable
fun TrackActionRow(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    secondaryText: String,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
    ) {
        TrackSecondaryButton(
            text = secondaryText,
            onClick = onSecondaryClick,
            enabled = secondaryEnabled,
            modifier = Modifier.weight(1f),
        )
        TrackPrimaryButton(
            text = primaryText,
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            modifier = Modifier.weight(1f),
        )
    }
}
