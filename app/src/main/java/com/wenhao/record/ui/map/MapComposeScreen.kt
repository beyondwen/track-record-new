package com.wenhao.record.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.TrackFloatingMapButton
import com.wenhao.record.ui.designsystem.TrackInsetPanel
import com.wenhao.record.ui.designsystem.TrackGlassCard
import com.wenhao.record.ui.designsystem.TrackGlowRing
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.designsystem.TrackRecordSpacing
import com.wenhao.record.ui.designsystem.TrackStatChip

data class MapAltitudeLegend(
    val minText: String = "",
    val maxText: String = "",
    val contentDescription: String = "",
)

data class MapScreenUiState(
    val title: String = "",
    val timeText: String = "",
    val qualityText: String = "",
    val pointCountText: String = "",
    val summaryText: String = "",
    val distanceText: String = "",
    val durationText: String = "",
    val speedText: String = "",
    val altitudeLegend: MapAltitudeLegend? = null,
    val mapState: TrackMapSceneState = TrackMapSceneState(),
)

@Composable
fun MapComposeScreen(
    state: MapScreenUiState,
    mapboxAccessToken: String,
    onBackClick: () -> Unit,
    onRefitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val historyMapKey = remember { "history-detail-map-${System.nanoTime()}" }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 全屏地图背景
        TrackMapboxCanvas(
            state = state.mapState,
            accessToken = mapboxAccessToken,
            modifier = Modifier.fillMaxSize(),
            mapKey = historyMapKey,
            viewportPadding = TrackMapViewportPadding(
                top = 36.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 200.dp, // 为浮动卡片留出空间
            ),
            showUserLocationPuck = false,
        )

        // 2. 顶部返回按钮
        TrackFloatingMapButton(
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.AutoMirrored.Outlined.ArrowBack),
            contentDescription = stringResource(R.string.compose_map_back),
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp),
        )

        // 3. 右侧重定位按钮 (稍微上移，避免被卡片遮挡)
        TrackFloatingMapButton(
            icon = painterResource(R.drawable.ic_fit_route),
            contentDescription = stringResource(R.string.compose_map_refit),
            onClick = onRefitClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 240.dp),
        )

        // 4. 底部浮动详情卡片
        var cardExpanded by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            TrackGlassCard(
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { cardExpanded = !cardExpanded },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 卡片头部
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = state.timeText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        
                        // 里程概览 (常驻显示)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                TrackGlowRing(progress = 1f, modifier = Modifier.size(42.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_tab_record),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = state.distanceText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 展开的详细内容
                    AnimatedVisibility(visible = cardExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MapFloatingStatChip(state.qualityText, modifier = Modifier.weight(1f))
                                MapFloatingStatChip(state.pointCountText, modifier = Modifier.weight(1f))
                            }

                            Text(
                                text = state.summaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MapFloatingMetricTile(stringResource(R.string.compose_map_duration), state.durationText, Modifier.weight(1f))
                                MapFloatingMetricTile(stringResource(R.string.compose_map_speed), state.speedText, Modifier.weight(1f))
                            }

                            state.altitudeLegend?.let { legend ->
                                AltitudeLegendCard(legend = legend, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapFloatingStatChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MapFloatingMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AltitudeLegendCard(
    legend: MapAltitudeLegend,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = legend.contentDescription
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.compose_map_altitude_legend_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.compose_map_altitude_legend_low),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(com.wenhao.record.ui.map.TrackAltitudePalette.legendColors())),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.compose_map_altitude_legend_high),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = legend.minText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(text = legend.maxText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun MapComposeScreenPreview() {
    TrackRecordTheme {
        MapComposeScreen(
            state = MapScreenUiState(
                title = "Mar 27, 2026",
                timeText = "Thu / 07:10 - 08:52",
                qualityText = "Good accuracy",
                pointCountText = "168 points",
                summaryText = "12.6 km total route with a steady urban walking pace.",
                distanceText = "12.6 km",
                durationText = "01:42",
                speedText = "7.4 km/h",
                altitudeLegend = MapAltitudeLegend(
                    minText = "23 m",
                    maxText = "128 m",
                    contentDescription = "Altitude legend",
                ),
            ),
            mapboxAccessToken = "pk.preview-token",
            onBackClick = {},
            onRefitClick = {},
        )
    }
}
