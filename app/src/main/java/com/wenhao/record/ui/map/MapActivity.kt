package com.wenhao.record.ui.map

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.tracking.TrackPathSanitizer
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.GeoCoordinate
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.util.AppTaskExecutor

class MapActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_DAY_START = "extra_day_start"

        fun createHistoryIntent(context: Context, dayStartMillis: Long): Intent {
            return Intent(context, MapActivity::class.java).putExtra(EXTRA_DAY_START, dayStartMillis)
        }
    }

    private var uiState by mutableStateOf(MapScreenUiState())
    private var viewportSequence = 0L
    private var renderGeneration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TrackRecordTheme {
                MapComposeScreen(
                    state = uiState,
                    onBackClick = { finish() },
                    onRefitClick = ::refitRoute,
                )
            }
        }

        renderHistory()
    }

    private fun renderHistory() {
        val dayStartMillis = intent.getLongExtra(EXTRA_DAY_START, -1L).takeIf { it > 0L }
        if (dayStartMillis == null) {
            Toast.makeText(this, R.string.dashboard_history_no_route, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val item = HistoryStorage.peekDailyByStart(this, dayStartMillis)
        if (item != null && item.segments.any { segment -> segment.isNotEmpty() }) {
            renderHistoryItem(item)
            return
        }

        HistoryStorage.whenReady(this) {
            if (isFinishing || isDestroyed) return@whenReady
            val readyItem = HistoryStorage.peekDailyByStart(this, dayStartMillis)
            if (readyItem == null || readyItem.segments.none { segment -> segment.isNotEmpty() }) {
                Toast.makeText(this, R.string.dashboard_history_no_route, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                renderHistoryItem(readyItem)
            }
        }
    }

    private fun renderHistoryItem(item: HistoryDayItem) {
        val generation = nextRenderGeneration()
        val appContext = applicationContext
        AppTaskExecutor.runOnIo {
            val renderableSegments = TrackPathSanitizer.renderableSegments(item.segments)
            val flattenedPoints = item.segments.flatten()
            val viewportPoints = renderableSegments.flatten().ifEmpty { flattenedPoints }
            val preparedState = MapScreenUiState(
                title = item.displayTitle,
                timeText = item.formattedDateDetail,
                qualityText = item.quality.badgeLabel,
                pointCountText = item.pointCountLabel,
                summaryText = item.summary,
                distanceText = item.formattedDistance,
                durationText = item.formattedDurationDetail,
                speedText = item.formattedSpeed,
                altitudeLegend = buildAltitudeLegend(appContext, viewportPoints),
                mapState = historySceneState(
                    item = item,
                    renderableSegments = renderableSegments,
                    viewportPoints = viewportPoints,
                    flattenedPoints = flattenedPoints,
                ),
            )
            AppTaskExecutor.runOnMain {
                if (isFinishing || isDestroyed || generation != renderGeneration) return@runOnMain
                uiState = preparedState
            }
        }
    }

    private fun refitRoute() {
        val fitRequest = uiState.mapState.viewportRequest as? TrackMapViewportRequest.Fit ?: return
        uiState = uiState.copy(
            mapState = uiState.mapState.copy(
                viewportRequest = fitRequest.copy(sequence = nextViewportSequence()),
            ),
        )
    }

    private fun historySceneState(
        item: HistoryDayItem,
        renderableSegments: List<List<TrackPoint>>,
        viewportPoints: List<TrackPoint>,
        flattenedPoints: List<TrackPoint>,
    ): TrackMapSceneState {
        val markers = buildList {
            val startPoint = renderableSegments.firstOrNull()?.firstOrNull() ?: flattenedPoints.firstOrNull()
            val endPoint = renderableSegments.lastOrNull()?.lastOrNull() ?: flattenedPoints.lastOrNull()
            startPoint?.let { point ->
                add(
                    TrackMapMarker(
                        id = "history-start",
                        coordinate = point.toGeoCoordinate(),
                        kind = TrackMapMarkerKind.START,
                    )
                )
            }
            endPoint?.let { point ->
                add(
                    TrackMapMarker(
                        id = "history-end",
                        coordinate = point.toGeoCoordinate(),
                        kind = TrackMapMarkerKind.END,
                    )
                )
            }
        }

        return TrackMapSceneState(
            polylines = TrackPolylineBuilder.buildCompact(
                segments = renderableSegments,
                idPrefix = "history",
                width = 6.6,
            ),
            markers = markers,
            viewportRequest = TrackMapViewportRequest.Fit(
                sequence = nextViewportSequence(),
                coordinates = viewportCoordinates(viewportPoints),
                singlePointZoom = 17.0,
                maxZoom = 17.2,
            ),
        )
    }

    private fun buildAltitudeLegend(context: Context, points: List<TrackPoint>): MapAltitudeLegend? {
        val altitudeRange = TrackAltitudePalette.altitudeRange(points) ?: return null
        val minText = context.getString(R.string.compose_map_altitude_legend_min, altitudeRange.start)
        val maxText = context.getString(R.string.compose_map_altitude_legend_max, altitudeRange.endInclusive)
        return MapAltitudeLegend(
            minText = minText,
            maxText = maxText,
            contentDescription = context.getString(
                R.string.compose_map_altitude_legend_content_description,
                minText,
                maxText,
            ),
        )
    }

    private fun viewportCoordinates(points: List<TrackPoint>): List<GeoCoordinate> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return listOf(points.first().toGeoCoordinate())

        var minLatitude = Double.POSITIVE_INFINITY
        var maxLatitude = Double.NEGATIVE_INFINITY
        var minLongitude = Double.POSITIVE_INFINITY
        var maxLongitude = Double.NEGATIVE_INFINITY

        points.forEach { point ->
            val coordinate = point.toGeoCoordinate()
            minLatitude = minOf(minLatitude, coordinate.latitude)
            maxLatitude = maxOf(maxLatitude, coordinate.latitude)
            minLongitude = minOf(minLongitude, coordinate.longitude)
            maxLongitude = maxOf(maxLongitude, coordinate.longitude)
        }

        return listOf(
            GeoCoordinate(latitude = minLatitude, longitude = minLongitude),
            GeoCoordinate(latitude = minLatitude, longitude = maxLongitude),
            GeoCoordinate(latitude = maxLatitude, longitude = minLongitude),
            GeoCoordinate(latitude = maxLatitude, longitude = maxLongitude),
        ).distinct()
    }

    @Synchronized
    private fun nextViewportSequence(): Long {
        viewportSequence += 1
        return viewportSequence
    }

    @Synchronized
    private fun nextRenderGeneration(): Long {
        renderGeneration += 1
        return renderGeneration
    }

}
