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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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
    BAROMETER,
}

enum class TrackLiquidTone {
    STRONG,
    STANDARD,
    SUBTLE,
    ACCENT,
}

@Composable
fun TrackLiquidPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    tone: TrackLiquidTone = TrackLiquidTone.STANDARD,
    shadowElevation: Dp = 0.dp,
    borderColor: Color = when (tone) {
        TrackLiquidTone.STRONG -> MaterialTheme.colorScheme.trackGlassBorder.copy(alpha = 0.18f)
        TrackLiquidTone.STANDARD -> MaterialTheme.colorScheme.trackGlassBorder.copy(alpha = 0.13f)
        TrackLiquidTone.SUBTLE -> MaterialTheme.colorScheme.trackInnerPanelBorder.copy(alpha = 0.14f)
        TrackLiquidTone.ACCENT -> MaterialTheme.colorScheme.trackGlowPrimary.copy(alpha = 0.2f)
    },
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val baseFill = when (tone) {
        TrackLiquidTone.STRONG -> colorScheme.trackGlassStrongSurface.copy(alpha = 0.985f)
        TrackLiquidTone.STANDARD -> colorScheme.trackGlassSurface.copy(alpha = 0.975f)
        TrackLiquidTone.SUBTLE -> colorScheme.trackInnerPanelSurface.copy(alpha = 0.965f)
        TrackLiquidTone.ACCENT -> colorScheme.trackGlassStrongSurface.copy(alpha = 0.982f)
    }
    val baseColors = when (tone) {
        TrackLiquidTone.STRONG -> listOf(
            colorScheme.trackGlassStrongSurface.copy(alpha = 0.54f),
            colorScheme.trackGlassSurface.copy(alpha = 0.4f),
            colorScheme.trackSecondarySurface.copy(alpha = 0.26f),
            colorScheme.trackSoftAccent.copy(alpha = 0.18f),
        )

        TrackLiquidTone.STANDARD -> listOf(
            colorScheme.trackGlassSurface.copy(alpha = 0.44f),
            colorScheme.trackSecondarySurface.copy(alpha = 0.24f),
            colorScheme.trackInnerPanelSurface.copy(alpha = 0.22f),
            colorScheme.trackSoftAccent.copy(alpha = 0.16f),
        )

        TrackLiquidTone.SUBTLE -> listOf(
            colorScheme.trackInnerPanelSurface.copy(alpha = 0.34f),
            colorScheme.trackSecondarySurface.copy(alpha = 0.2f),
            colorScheme.trackGlassSurface.copy(alpha = 0.14f),
            colorScheme.trackGlowPrimary.copy(alpha = 0.08f),
        )

        TrackLiquidTone.ACCENT -> listOf(
            colorScheme.trackGlassStrongSurface.copy(alpha = 0.4f),
            colorScheme.trackGlowPrimary.copy(alpha = 0.24f),
            colorScheme.trackGlowSecondary.copy(alpha = 0.18f),
            colorScheme.trackGlassSurface.copy(alpha = 0.22f),
        )
    }
    val primaryGlowAlpha = when (tone) {
        TrackLiquidTone.STRONG -> 0.2f
        TrackLiquidTone.STANDARD -> 0.15f
        TrackLiquidTone.SUBTLE -> 0.11f
        TrackLiquidTone.ACCENT -> 0.24f
    }
    val secondaryGlowAlpha = when (tone) {
        TrackLiquidTone.STRONG -> 0.14f
        TrackLiquidTone.STANDARD -> 0.11f
        TrackLiquidTone.SUBTLE -> 0.08f
        TrackLiquidTone.ACCENT -> 0.2f
    }
    val sheenAlpha = when (tone) {
        TrackLiquidTone.STRONG -> 0.17f
        TrackLiquidTone.STANDARD -> 0.14f
        TrackLiquidTone.SUBTLE -> 0.1f
        TrackLiquidTone.ACCENT -> 0.17f
    }

    Surface(
        modifier = modifier,
        color = Color.Transparent,
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = shadowElevation,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .clip(shape)
                .drawWithCache {
                    val diagonalWash = Brush.linearGradient(
                        colors = baseColors,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    val topSheen = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = sheenAlpha),
                            Color.White.copy(alpha = sheenAlpha * 0.35f),
                            Color.Transparent,
                        ),
                        start = Offset(size.width * 0.1f, 0f),
                        end = Offset(size.width * 0.82f, size.height * 0.5f),
                    )
                    val coolOrb = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.trackGlowPrimary.copy(alpha = primaryGlowAlpha),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.14f),
                        radius = size.minDimension * 0.92f,
                    )
                    val warmOrb = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.trackGlowSecondary.copy(alpha = secondaryGlowAlpha),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.9f, size.height * 0.08f),
                        radius = size.minDimension * 0.78f,
                    )
                    val bottomBloom = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = sheenAlpha * 0.42f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.72f, size.height * 1.02f),
                        radius = size.minDimension * 0.88f,
                    )
                    onDrawBehind {
                        drawRect(color = baseFill)
                        drawRect(brush = diagonalWash)
                        drawRect(brush = coolOrb)
                        drawRect(brush = warmOrb)
                        drawRect(brush = bottomBloom)
                        drawRect(brush = topSheen)
                    }
                }
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun TrackScreenCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    TrackLiquidPanel(
        modifier = modifier.fillMaxWidth(),
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 14.dp,
        contentPadding = PaddingValues(TrackRecordSpacing.xl),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
        ) {
            content()
        }
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
    containerColor: Color = MaterialTheme.colorScheme.trackInnerPanelSurface,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val shape = RoundedCornerShape(999.dp)
    val stableChipFill = containerColor.copy(alpha = 0.96f)
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            stableChipFill,
                            containerColor.copy(alpha = 0.9f),
                            Color.White.copy(alpha = 0.05f),
                        ),
                        start = Offset.Zero,
                        end = Offset(300f, 160f),
                    ),
                )
                .drawWithCache {
                    val sheen = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height * 0.75f),
                    )
                    onDrawBehind { drawRect(brush = sheen) }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun TrackMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val metricDescription = "$label $value"
    TrackLiquidPanel(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = metricDescription
        },
        shape = MaterialTheme.shapes.small,
        tone = TrackLiquidTone.STANDARD,
        shadowElevation = 10.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column(
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
    TrackLiquidPanel(
        modifier = modifier,
        tone = if (accented) {
            TrackLiquidTone.STANDARD
        } else {
            TrackLiquidTone.SUBTLE
        },
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 0.dp,
        borderColor = if (accented) {
            MaterialTheme.colorScheme.trackGlassBorder.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.trackInnerPanelBorder.copy(alpha = 0.14f)
        },
        contentPadding = contentPadding,
    ) {
        Column(
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
    onBarometerClick: () -> Unit,
    modifier: Modifier = Modifier,
    recordLabel: String = stringResource(R.string.compose_dashboard_record),
    historyLabel: String = stringResource(R.string.compose_dashboard_history),
    barometerLabel: String = stringResource(R.string.compose_barometer_tab),
    recordEnabled: Boolean = true,
    historyEnabled: Boolean = true,
    barometerEnabled: Boolean = true,
) {
    TrackLiquidPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        tone = TrackLiquidTone.SUBTLE,
        shadowElevation = 0.dp,
        contentPadding = PaddingValues(0.dp),
    ) {
        NavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            NavigationBarItem(
                selected = selectedTab == TrackBottomTab.RECORD,
                onClick = onRecordClick,
                enabled = recordEnabled,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_tab_record),
                        contentDescription = null,
                    )
                },
                label = { Text(recordLabel) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.trackSoftAccent.copy(alpha = 0.5f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                ),
            )
            NavigationBarItem(
                selected = selectedTab == TrackBottomTab.HISTORY,
                onClick = onHistoryClick,
                enabled = historyEnabled,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_tab_history),
                        contentDescription = null,
                    )
                },
                label = { Text(historyLabel) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.trackSoftAccent.copy(alpha = 0.5f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                ),
            )
            NavigationBarItem(
                selected = selectedTab == TrackBottomTab.BAROMETER,
                onClick = onBarometerClick,
                enabled = barometerEnabled,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_tab_altitude),
                        contentDescription = null,
                    )
                },
                label = { Text(barometerLabel) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.trackSoftAccent.copy(alpha = 0.5f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                ),
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
    val shape = RoundedCornerShape(18.dp)
    val baseBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.trackGlowPrimary.copy(alpha = if (enabled) 0.98f else 0.46f),
            MaterialTheme.colorScheme.trackGlowSecondary.copy(alpha = if (enabled) 0.76f else 0.32f),
            MaterialTheme.colorScheme.trackGlowPrimary.copy(alpha = if (enabled) 0.9f else 0.42f),
        ),
        start = Offset.Zero,
        end = Offset(480f, 220f),
    )
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .clip(shape)
            .background(baseBrush)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
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
    val shape = RoundedCornerShape(18.dp)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.trackInnerPanelSurface.copy(alpha = if (enabled) 0.94f else 0.5f),
                        MaterialTheme.colorScheme.trackGlassSurface.copy(alpha = if (enabled) 0.6f else 0.26f),
                    ),
                    start = Offset.Zero,
                    end = Offset(360f, 180f),
                ),
            ),
        enabled = enabled,
        shape = shape,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.trackInnerPanelBorder.copy(alpha = if (enabled) 0.34f else 0.2f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
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
