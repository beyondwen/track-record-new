package com.wenhao.record.ui.barometer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.TrackAtmosphericBackground
import com.wenhao.record.ui.designsystem.TrackBottomNavigationBar
import com.wenhao.record.ui.designsystem.TrackBottomTab
import com.wenhao.record.ui.designsystem.TrackInsetPanel
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackMetricTile
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.designsystem.TrackScreenCard
import com.wenhao.record.ui.designsystem.TrackSectionHeading
import com.wenhao.record.ui.designsystem.TrackStatChip
import com.wenhao.record.ui.designsystem.trackGlowPrimary
import com.wenhao.record.ui.designsystem.trackGlowSecondary
import com.wenhao.record.ui.designsystem.trackInnerPanelSurface
import com.wenhao.record.ui.designsystem.trackSecondarySurface

@Immutable
data class BarometerUiState(
    val isSensorAvailable: Boolean = true,
    val hasBarometerFeature: Boolean = true,
    val isReadingLive: Boolean = false,
    val sensorDebugLabel: String = "",
    val sensorDiagnostics: String = "",
    val sensorInventory: String = "",
    val pressureValue: String = "--",
    val pressureUnit: String = "m",
    val trendLabel: String = "等待定位",
    val trendSummary: String = "正在等待连续定位，拿到几笔稳定海拔后会显示高度变化趋势。",
    val altitudeValue: String = "--",
    val seaLevelValue: String = "等待定位",
    val comfortLabel: String = "等待定位",
    val comfortSummary: String = "当前位置海拔还没拿到，页面会在定位成功后自动更新。",
    val note: String = "这个页面现在改成了海拔页，需要系统先给出一次有效定位。",
    val trendPoints: List<Float> = listOf(0.62f, 0.6f, 0.58f, 0.56f, 0.57f, 0.55f, 0.54f),
)

@Composable
fun BarometerComposeScreen(
    onRecordClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBarometerClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: BarometerUiState = BarometerUiState(),
) {
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
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    AltitudeHeroCard(state = state)
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TrackMetricTile(
                            label = stringResource(R.string.compose_barometer_metric_altitude),
                            value = state.altitudeValue,
                            modifier = Modifier.weight(1f),
                        )
                        TrackMetricTile(
                            label = stringResource(R.string.compose_barometer_metric_sea_level),
                            value = state.seaLevelValue,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    AltitudeTrendCard(state = state)
                }
                item {
                    AltitudeInsightCard(state = state)
                }
            }

            AltitudeBottomBar(
                onRecordClick = onRecordClick,
                onHistoryClick = onHistoryClick,
                onBarometerClick = onBarometerClick,
            )
        }
    }
}

@Composable
private fun AltitudeHeroCard(
    state: BarometerUiState,
    modifier: Modifier = Modifier,
) {
    val accessibilitySummary = stringResource(
        R.string.compose_barometer_accessibility_summary,
        state.pressureValue,
        state.pressureUnit,
        state.trendLabel,
        state.altitudeValue,
    )
    TrackScreenCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibilitySummary
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TrackStatChip(
                    text = if (state.isReadingLive) {
                        stringResource(R.string.compose_barometer_live_badge)
                    } else {
                        stringResource(R.string.compose_barometer_waiting_badge)
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.compose_barometer_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.compose_barometer_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = state.pressureValue,
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.pressureUnit,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TrackStatChip(text = state.trendLabel)
                    TrackStatChip(
                        text = state.comfortLabel,
                        containerColor = MaterialTheme.colorScheme.trackSecondarySurface.copy(alpha = 0.84f),
                    )
                }
                if (state.sensorDebugLabel.isNotBlank()) {
                    Text(
                        text = state.sensorDebugLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.sensorDiagnostics.isNotBlank()) {
                    Text(
                        text = state.sensorDiagnostics,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AltitudeDial(
                modifier = Modifier.size(124.dp),
            )
        }
    }
}

@Composable
private fun AltitudeTrendCard(
    state: BarometerUiState,
    modifier: Modifier = Modifier,
) {
    val trendAccessibility = stringResource(
        R.string.compose_barometer_trend_accessibility,
        state.trendLabel,
    )
    TrackLiquidPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 8.dp,
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TrackSectionHeading(
                title = stringResource(R.string.compose_barometer_trend_title),
                subtitle = state.trendSummary,
            )

            TrackInsetPanel(
                accented = true,
                contentPadding = PaddingValues(16.dp),
            ) {
                AltitudeSparkline(
                    points = state.trendPoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .semantics {
                            contentDescription = trendAccessibility
                        },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.compose_barometer_trend_start),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.compose_barometer_trend_end),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AltitudeInsightCard(
    state: BarometerUiState,
    modifier: Modifier = Modifier,
) {
    TrackScreenCard(modifier = modifier) {
        TrackSectionHeading(
            title = stringResource(R.string.compose_barometer_insight_title),
            subtitle = stringResource(R.string.compose_barometer_insight_subtitle),
        )

        TrackInsetPanel(accented = true) {
            Text(
                text = stringResource(R.string.compose_barometer_insight_pressure_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.comfortSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TrackInsetPanel {
            Text(
                text = stringResource(R.string.compose_barometer_insight_note_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AltitudeSparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
) {
    val glowPrimary = MaterialTheme.colorScheme.trackGlowPrimary
    val glowSecondary = MaterialTheme.colorScheme.trackGlowSecondary
    val panel = MaterialTheme.colorScheme.trackInnerPanelSurface.copy(alpha = 0.42f)
    val secondarySurface = MaterialTheme.colorScheme.trackSecondarySurface.copy(alpha = 0.32f)
    Canvas(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(panel, secondarySurface),
            ),
            shape = RoundedCornerShape(24.dp),
        ),
    ) {
        val graphPoints = points.ifEmpty { listOf(0.58f, 0.58f, 0.58f) }
        val stepX = size.width / (graphPoints.lastIndex.coerceAtLeast(1))
        val path = androidx.compose.ui.graphics.Path().apply {
            graphPoints.forEachIndexed { index, point ->
                val x = stepX * index
                val y = size.height * point
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        repeat(4) { index ->
            val y = size.height * (0.2f + index * 0.18f)
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }

        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(glowPrimary, glowSecondary),
                start = Offset(0f, size.height * 0.2f),
                end = Offset(size.width, size.height * 0.8f),
            ),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )

        graphPoints.forEachIndexed { index, point ->
            val x = stepX * index
            val y = size.height * point
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = 4.5.dp.toPx(),
                center = Offset(x, y),
            )
            drawCircle(
                color = glowPrimary.copy(alpha = 0.28f),
                radius = 10.dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
}

@Composable
private fun AltitudeDial(
    modifier: Modifier = Modifier,
) {
    val secondarySurface = MaterialTheme.colorScheme.trackSecondarySurface.copy(alpha = 0.76f)
    val innerPanelSurface = MaterialTheme.colorScheme.trackInnerPanelSurface.copy(alpha = 0.4f)
    val glowPrimary = MaterialTheme.colorScheme.trackGlowPrimary
    val glowSecondary = MaterialTheme.colorScheme.trackGlowSecondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        val stroke = 10.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - stroke

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondarySurface, innerPanelSurface),
                center = center,
                radius = radius * 1.2f,
            ),
            radius = radius * 1.06f,
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.1f),
            radius = radius,
            center = center,
            style = Stroke(width = stroke),
        )
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(glowSecondary, glowPrimary, glowSecondary),
                center = center,
            ),
            startAngle = 150f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        val needleAngleRadians = Math.toRadians(292.0)
        val needleEnd = Offset(
            x = center.x + (radius * 0.72f * kotlin.math.cos(needleAngleRadians)).toFloat(),
            y = center.y + (radius * 0.72f * kotlin.math.sin(needleAngleRadians)).toFloat(),
        )
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(glowPrimary, glowSecondary),
                start = center,
                end = needleEnd,
            ),
            start = center,
            end = needleEnd,
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = onSurface,
            radius = 7.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = 2.5.dp.toPx(),
            center = center,
        )
    }
}

@Composable
private fun AltitudeBottomBar(
    onRecordClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBarometerClick: () -> Unit,
) {
    TrackLiquidPanel(
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        TrackBottomNavigationBar(
            selectedTab = TrackBottomTab.BAROMETER,
            onRecordClick = onRecordClick,
            onHistoryClick = onHistoryClick,
            onBarometerClick = onBarometerClick,
            barometerEnabled = false,
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun BarometerComposeScreenPreview() {
    TrackRecordTheme {
        BarometerComposeScreen(
            onRecordClick = {},
            onHistoryClick = {},
            onBarometerClick = {},
            state = BarometerUiState(
                isReadingLive = true,
                sensorDebugLabel = "定位海拔 · 22:08",
                sensorDiagnostics = "定位海拔会随卫星状态波动，当前参考精度约 8 米。",
                pressureValue = "48",
                pressureUnit = "m",
                trendLabel = "缓慢上升",
                altitudeValue = "16 m",
                seaLevelValue = "精度 8 m",
                comfortLabel = "平地段",
                comfortSummary = "当前位置整体还算平缓，更适合看细小的抬升和下探。",
                note = "海拔来自系统定位，最近一次更新于 22:08。",
            ),
        )
    }
}
