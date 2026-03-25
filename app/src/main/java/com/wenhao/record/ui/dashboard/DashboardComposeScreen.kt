package com.wenhao.record.ui.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.TrackRecordSpacing
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
    val speedText: String = "0.0 公里/小时",
    val autoTrackTitle: String = "",
    val autoTrackMeta: String = "",
    val statusLabel: String = "",
    val statusTone: DashboardTone = DashboardTone.MUTED,
    val recordIconRes: Int = R.drawable.ic_play_dashboard,
    val isPulseActive: Boolean = false,
)

@Composable
fun DashboardComposeScreen(
    state: DashboardScreenUiState,
    onRecordClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 4.dp,
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(999.dp),
                    ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = state.distanceText,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.compose_dashboard_distance_unit).uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(26.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                DashboardStat(
                    label = stringResource(R.string.compose_dashboard_time_label),
                    value = state.durationText,
                    modifier = Modifier.weight(1f),
                )
                Canvas(
                    modifier = Modifier
                        .height(42.dp)
                        .width(1.dp),
                ) {
                    drawLine(
                        color = dividerColor,
                        start = center.copy(y = 0f),
                        end = center.copy(y = size.height),
                        strokeWidth = size.width,
                    )
                }
                DashboardStat(
                    label = stringResource(R.string.compose_dashboard_speed_label),
                    value = state.speedText.substringBefore(" "),
                    modifier = Modifier.weight(1f),
                    suffix = stringResource(R.string.compose_dashboard_speed_unit),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            DashboardRecordIndicator(
                iconRes = state.recordIconRes,
                isPulseActive = state.isPulseActive,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = state.statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = statusContentColor(state.statusTone),
            )
            Text(
                text = state.autoTrackMeta,
                modifier = Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )

            Spacer(modifier = Modifier.height(30.dp))

            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = state.isRecordTabSelected,
                    onClick = onRecordClick,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_tab_record),
                            contentDescription = stringResource(R.string.compose_dashboard_record),
                        )
                    },
                    label = { Text(stringResource(R.string.compose_dashboard_record)) },
                    colors = navigationItemColors(),
                )
                NavigationBarItem(
                    selected = !state.isRecordTabSelected,
                    onClick = onHistoryClick,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_tab_history),
                            contentDescription = stringResource(R.string.compose_dashboard_history),
                        )
                    },
                    label = { Text(stringResource(R.string.compose_dashboard_history)) },
                    colors = navigationItemColors(),
                )
            }
        }
    }
}

@Composable
private fun DashboardStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!suffix.isNullOrBlank()) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun DashboardRecordIndicator(
    iconRes: Int,
    isPulseActive: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dashboardPulse")
    val haloScale = if (isPulseActive) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "haloScale",
        ).value
    } else {
        1f
    }
    val haloAlpha = if (isPulseActive) {
        infiniteTransition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "haloAlpha",
        ).value
    } else {
        0.72f
    }

    Box(
        modifier = Modifier
            .size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(haloScale)
                .alpha(haloAlpha)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(78.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun navigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun statusContainerColor(tone: DashboardTone): Color = when (tone) {
    DashboardTone.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
    DashboardTone.WARNING -> MaterialTheme.colorScheme.secondaryContainer
    DashboardTone.MUTED -> MaterialTheme.colorScheme.surfaceVariant
    DashboardTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun statusContentColor(tone: DashboardTone): Color = when (tone) {
    DashboardTone.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
    DashboardTone.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
    DashboardTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
    DashboardTone.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
}
