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
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.map.MapMarkerIconFactory
import com.wenhao.record.ui.map.BaiduMapProvider
import com.wenhao.record.ui.designsystem.TrackRecordTheme

class MapActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_DAY_START = "extra_day_start"

        fun createHistoryIntent(context: Context, dayStartMillis: Long): Intent {
            return Intent(context, MapActivity::class.java).putExtra(EXTRA_DAY_START, dayStartMillis)
        }
    }

    private lateinit var mapView: MapView
    private lateinit var aMap: BaiduMap

    private val routePolylines = mutableListOf<Polyline>()
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var routeBounds: LatLngBounds? = null
    private var singlePoint: LatLng? = null

    private var uiState by mutableStateOf(MapScreenUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        mapView = BaiduMapProvider.createMapView(this)
        aMap = mapView.map
        mapView.showZoomControls(false)
        mapView.showScaleControl(false)
        aMap.uiSettings.setCompassEnabled(false)
        aMap.uiSettings.setRotateGesturesEnabled(false)
        aMap.uiSettings.setOverlookingGesturesEnabled(false)

        setContent {
            TrackRecordTheme {
                MapComposeScreen(
                    mapView = mapView,
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
        if (item != null && item.points.isNotEmpty()) {
            renderHistoryItem(item)
            return
        }

        HistoryStorage.whenReady(this) {
            if (isFinishing || isDestroyed) return@whenReady
            val readyItem = HistoryStorage.peekDailyByStart(this, dayStartMillis)
            if (readyItem == null || readyItem.points.isEmpty()) {
                Toast.makeText(this, R.string.dashboard_history_no_route, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                renderHistoryItem(readyItem)
            }
        }
    }

    private fun renderHistoryItem(item: HistoryDayItem) {
        uiState = MapScreenUiState(
            title = item.displayTitle,
            timeText = item.formattedDateDetail,
            qualityText = item.quality.badgeLabel,
            pointCountText = item.pointCountLabel,
            summaryText = item.summary,
            distanceText = item.formattedDistance,
            durationText = item.formattedDurationDetail,
            speedText = item.formattedSpeed,
        )

        val allPoints = item.points.map { it.toLatLng() }
        singlePoint = allPoints.firstOrNull()
        routeBounds = if (allPoints.size > 1) {
            LatLngBounds.Builder().apply { allPoints.forEach(::include) }.build()
        } else {
            null
        }

        routePolylines.forEach { it.remove() }
        routePolylines.clear()
        startMarker?.remove()
        endMarker?.remove()

        item.segments.filter { it.size > 1 }.forEach { segment ->
            routePolylines += aMap.addOverlay(
                PolylineOptions()
                    .points(segment.map { it.toLatLng() })
                    .color("#8B5CF6".toColorInt())
                    .width(14)
            ) as Polyline
        }

        val startPoint = item.segments.firstOrNull()?.firstOrNull()?.toLatLng() ?: allPoints.first()
        val endPoint = item.segments.lastOrNull()?.lastOrNull()?.toLatLng() ?: allPoints.last()

        startMarker = aMap.addOverlay(
            MarkerOptions()
                .position(startPoint)
                .title(getString(R.string.compose_map_start))
                .icon(MapMarkerIconFactory.fromDrawableResource(this, R.drawable.ic_route_start_marker))
        ) as Marker

        endMarker = aMap.addOverlay(
            MarkerOptions()
                .position(endPoint)
                .title(getString(R.string.compose_map_end))
                .icon(MapMarkerIconFactory.fromDrawableResource(this, R.drawable.ic_route_end_marker))
        ) as Marker

        mapView.post { refitRoute() }
    }

    private fun refitRoute() {
        val point = singlePoint
        val bounds = routeBounds

        when {
            point != null && bounds == null -> {
                aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, 17f))
            }

            bounds != null -> {
                aMap.setViewPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(268))
                aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(bounds))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        routePolylines.forEach { it.remove() }
        routePolylines.clear()
        startMarker?.remove()
        endMarker?.remove()
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
