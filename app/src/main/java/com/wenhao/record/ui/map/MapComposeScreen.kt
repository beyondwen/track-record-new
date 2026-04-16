package com.wenhao.record.ui.map

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.rememberSheetAwareBottomPadding
import com.wenhao.record.ui.designsystem.rememberSheetAwareViewportBottomPadding
import com.wenhao.record.ui.designsystem.TrackBottomHandle
import com.wenhao.record.ui.designsystem.TrackBottomSurface
import com.wenhao.record.ui.designsystem.TrackFloatingMapButton
import com.wenhao.record.ui.designsystem.TrackInsetPanel
import com.wenhao.record.ui.designsystem.TrackMapBottomScrim
import com.wenhao.record.ui.designsystem.TrackMetricTile
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.designsystem.TrackRecordSpacing
import com.wenhao.record.ui.designsystem.TrackStatChip
import com.wenhao.record.ui.designsystem.rememberVisibleSheetHeight
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapComposeScreen(
    state: MapScreenUiState,
    onBackClick: () -> Unit,
    onRefitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sheetPeekHeight = 126.dp
        val sheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true,
        )
        val coroutineScope = rememberCoroutineScope()
        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = sheetState,
        )
        var sheetContentHeightPx by remember { mutableFloatStateOf(0f) }
        val visibleSheetHeight = rememberVisibleSheetHeight(
            sheetState = sheetState,
            layoutHeight = maxHeight,
            peekHeight = sheetPeekHeight,
            expandedHeightPx = sheetContentHeightPx,
        )
        val refitButtonBottomPadding = rememberSheetAwareBottomPadding(
            sheetVisibleHeight = visibleSheetHeight,
            extraSpacing = 16.dp,
        )
        val density = LocalDensity.current
        val settledSheetVisibleHeight = remember(sheetState.currentValue, sheetContentHeightPx, density) {
            when (sheetState.currentValue) {
                SheetValue.Expanded -> {
                    if (sheetContentHeightPx > 0f) {
                        with(density) {
                            sheetContentHeightPx.toDp()
                        }
                    } else {
                        sheetPeekHeight
                    }
                }
                SheetValue.PartiallyExpanded,
                SheetValue.Hidden,
                -> sheetPeekHeight
            }
        }
        val viewportBottomPadding = rememberSheetAwareViewportBottomPadding(
            sheetVisibleHeight = settledSheetVisibleHeight,
            extraSpacing = 16.dp,
        )
        val isSheetExpanded = sheetState.currentValue == SheetValue.Expanded
        val sheetToggleLabel = stringResource(
            if (isSheetExpanded) R.string.compose_map_sheet_collapse else R.string.compose_map_sheet_expand
        )
        val sheetStateDescription = stringResource(
            if (isSheetExpanded) R.string.compose_map_sheet_expanded else R.string.compose_map_sheet_collapsed
        )

        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = scaffoldState,
            sheetPeekHeight = sheetPeekHeight,
            sheetContainerColor = Color.Transparent,
            sheetShadowElevation = 0.dp,
            sheetTonalElevation = 0.dp,
            sheetDragHandle = null,
            sheetContent = {
                TrackBottomSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { sheetContentHeightPx = it.height.toFloat() },
                ) {
                    TrackBottomHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                    MapSheetHeader(
                        title = state.title,
                        timeText = state.timeText,
                        actionText = sheetToggleLabel,
                        stateDescription = sheetStateDescription,
                        onClick = {
                            coroutineScope.launch {
                                if (sheetState.currentValue == SheetValue.Expanded) {
                                    sheetState.partialExpand()
                                } else {
                                    sheetState.expand()
                                }
                            }
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 14.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    )

                    MapSheetContent(
                        state = state,
                        isExpanded = isSheetExpanded,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TrackMapboxCanvas(
                    state = state.mapState,
                    modifier = Modifier.fillMaxSize(),
                    viewportPadding = TrackMapViewportPadding(
                        top = 36.dp,
                        start = 20.dp,
                        end = 20.dp,
                        bottom = viewportBottomPadding,
                    ),
                )

                TrackMapBottomScrim(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )

                TrackFloatingMapButton(
                    icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.AutoMirrored.Outlined.ArrowBack),
                    contentDescription = stringResource(R.string.compose_map_back),
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 12.dp),
                )

                TrackFloatingMapButton(
                    icon = painterResource(R.drawable.ic_fit_route),
                    contentDescription = stringResource(R.string.compose_map_refit),
                    onClick = onRefitClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = refitButtonBottomPadding),
                )
            }
        }
    }
}

@Composable
private fun MapSheetContent(
    state: MapScreenUiState,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.lg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TrackRecordSpacing.sm),
        ) {
            MapSheetStatChip(
                text = stringResource(R.string.compose_map_badge),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            if (state.qualityText.isNotBlank()) {
                MapSheetStatChip(
                    text = state.qualityText,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
            if (state.pointCountText.isNotBlank()) {
                MapSheetStatChip(
                    text = state.pointCountText,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        TrackInsetPanel(accented = true) {
            Text(
                text = stringResource(R.string.compose_map_sheet_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = state.distanceText,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TrackMetricTile(
                label = stringResource(R.string.compose_map_duration),
                value = state.durationText,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            TrackMetricTile(
                label = stringResource(R.string.compose_map_speed),
                value = state.speedText,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        state.altitudeLegend?.let { legend ->
            AltitudeLegendCard(
                legend = legend,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RowScope.MapSheetStatChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = contentColor.copy(alpha = 0.12f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AltitudeLegendCard(
    legend: MapAltitudeLegend,
    modifier: Modifier = Modifier,
) {
    TrackInsetPanel(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = legend.contentDescription
        }
    ) {
        Text(
            text = stringResource(R.string.compose_map_altitude_legend_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.compose_map_altitude_legend_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.compose_map_altitude_legend_low),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 12.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Brush.horizontalGradient(TrackAltitudePalette.legendColors())),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.compose_map_altitude_legend_high),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = legend.minText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = legend.maxText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MapSheetHeader(
    title: String,
    timeText: String,
    actionText: String,
    stateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                this.stateDescription = stateDescription
            }
            .clickable(
                role = Role.Button,
                onClickLabel = actionText,
                onClick = onClick,
            ),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrackRecordSpacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TrackRecordSpacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TrackStatChip(
                text = actionText,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/*
@Preview(showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun MapComposeScreenPreview() {
    TrackRecordTheme {
        MapComposeScreen(
            state = MapScreenUiState(
                title = "2026年3月27日",
                timeText = "周四 / 07:10 - 08:52",
                qualityText = "轨迹质量良好",
                pointCountText = "共 168 个点位",
                summaryText = "12.6 公里 / 01:42 / 平均 7.4 公里/小时",
                distanceText = "12.6 km",
                durationText = "01:42",
                speedText = "7.4 km/h",
            ),
            onBackClick = {},
            onRefitClick = {},
        )
    }
}
*/

@Preview(showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun MapComposeScreenPreview() {
    TrackRecordTheme {
        MapComposeScreen(
            state = MapScreenUiState(
                title = "Mar 27, 2026",
                timeText = "Thu / 07:10 - 08:52",
                qualityText = "Good GPS accuracy",
                pointCountText = "168 points collected",
                summaryText = "12.6 km total route with a steady urban walking pace.",
                distanceText = "12.6 km",
                durationText = "01:42",
                speedText = "7.4 km/h",
                altitudeLegend = MapAltitudeLegend(
                    minText = "Lowest 23 m",
                    maxText = "Highest 128 m",
                    contentDescription = "Altitude legend, lowest 23 meters, highest 128 meters",
                ),
            ),
            onBackClick = {},
            onRefitClick = {},
        )
    }
}
