package com.wenhao.record.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.MapView
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.TrackBottomHandle
import com.wenhao.record.ui.designsystem.TrackBottomSurface
import com.wenhao.record.ui.designsystem.TrackFloatingMapButton
import com.wenhao.record.ui.designsystem.TrackMapBottomScrim
import com.wenhao.record.ui.designsystem.TrackMetricTile
import com.wenhao.record.ui.designsystem.TrackStatChip

data class MapScreenUiState(
    val title: String = "",
    val timeText: String = "",
    val qualityText: String = "",
    val pointCountText: String = "",
    val summaryText: String = "",
    val distanceText: String = "",
    val durationText: String = "",
    val speedText: String = "",
)

@Composable
fun MapComposeScreen(
    mapView: MapView,
    state: MapScreenUiState,
    onBackClick: () -> Unit,
    onRefitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
        )

        TrackMapBottomScrim(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )

        TrackFloatingMapButton(
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.AutoMirrored.Outlined.ArrowBack),
            contentDescription = stringResource(R.string.compose_map_back),
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        TrackFloatingMapButton(
            icon = painterResource(R.drawable.ic_locate_dashboard),
            contentDescription = stringResource(R.string.compose_map_refit),
            onClick = onRefitClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 204.dp)
        )

        TrackBottomSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            TrackBottomHandle(modifier = Modifier.align(Alignment.CenterHorizontally))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_history),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.Center),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = state.timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TrackStatChip(text = stringResource(R.string.compose_map_badge))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TrackStatChip(
                    text = state.qualityText,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                TrackStatChip(
                    text = state.pointCountText,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = state.summaryText,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TrackMetricTile(
                    label = stringResource(R.string.compose_map_distance),
                    value = state.distanceText,
                    modifier = Modifier.weight(1f),
                )
                TrackMetricTile(
                    label = stringResource(R.string.compose_map_duration),
                    value = state.durationText,
                    modifier = Modifier.weight(1f),
                )
                TrackMetricTile(
                    label = stringResource(R.string.compose_map_speed),
                    value = state.speedText,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
