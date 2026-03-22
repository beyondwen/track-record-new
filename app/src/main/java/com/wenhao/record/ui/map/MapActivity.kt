package com.wenhao.record.ui.map

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.map.MapMarkerIconFactory
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.google.android.material.card.MaterialCardView

class MapActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_HISTORY_ID = "extra_history_id"

        fun createHistoryIntent(context: Context, historyId: Long): Intent {
            return Intent(context, MapActivity::class.java).putExtra(EXTRA_HISTORY_ID, historyId)
        }
    }

    private lateinit var mapView: MapView
    private lateinit var aMap: BaiduMap
    private lateinit var historyInfoCard: MaterialCardView
    private lateinit var tvRouteTitle: TextView
    private lateinit var tvRouteTime: TextView
    private lateinit var tvRouteDistance: TextView
    private lateinit var tvRouteDuration: TextView
    private lateinit var tvRouteSpeed: TextView
    private lateinit var tvRouteSummary: TextView

    private var routePolyline: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var routeBounds: LatLngBounds? = null
    private var singlePoint: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.mapView)
        aMap = mapView.map
        historyInfoCard = findViewById(R.id.historyInfoCard)
        tvRouteTitle = findViewById(R.id.tvRouteTitle)
        tvRouteTime = findViewById(R.id.tvRouteTime)
        tvRouteDistance = findViewById(R.id.tvRouteDistance)
        tvRouteDuration = findViewById(R.id.tvRouteDuration)
        tvRouteSpeed = findViewById(R.id.tvRouteSpeed)
        tvRouteSummary = findViewById(R.id.tvRouteSummary)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnRefitRoute).setOnClickListener { refitRoute() }

        mapView.showZoomControls(false)
        mapView.showScaleControl(false)
        aMap.uiSettings.setCompassEnabled(false)
        aMap.uiSettings.setRotateGesturesEnabled(false)
        aMap.uiSettings.setOverlookingGesturesEnabled(false)

        renderHistory()
    }

    private fun renderHistory() {
        val historyId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L).takeIf { it > 0L }
        val item = HistoryStorage.load(this).firstOrNull { it.id == historyId }

        if (item == null || item.points.isEmpty()) {
            Toast.makeText(this, R.string.dashboard_history_no_route, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvRouteTitle.text = item.displayTitle
        tvRouteTime.text = item.formattedDateDetail
        tvRouteDistance.text = item.formattedDistance
        tvRouteDuration.text = item.formattedDurationDetail
        tvRouteSpeed.text = item.formattedSpeed
        tvRouteSummary.text = item.summary

        val points = item.points.map { it.toLatLng() }
        singlePoint = points.firstOrNull()
        routeBounds = if (points.size > 1) {
            LatLngBounds.Builder().apply { points.forEach(::include) }.build()
        } else {
            null
        }

        routePolyline?.remove()
        startMarker?.remove()
        endMarker?.remove()

        routePolyline = aMap.addOverlay(
            PolylineOptions()
                .points(points)
                .color(Color.parseColor("#8B5CF6"))
                .width(14)
        ) as Polyline

        startMarker = aMap.addOverlay(
            MarkerOptions()
                .position(points.first())
                .title(getString(R.string.history_map_start))
                .icon(MapMarkerIconFactory.fromDrawableResource(this, R.drawable.ic_route_start_marker))
        ) as Marker

        endMarker = aMap.addOverlay(
            MarkerOptions()
                .position(points.last())
                .title(getString(R.string.history_map_end))
                .icon(MapMarkerIconFactory.fromDrawableResource(this, R.drawable.ic_route_end_marker))
        ) as Marker

        historyInfoCard.post {
            refitRoute()
        }
    }

    private fun refitRoute() {
        val point = singlePoint
        val bounds = routeBounds

        when {
            point != null && bounds == null -> {
                aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, 17f))
            }

            bounds != null -> {
                aMap.setViewPadding(
                    dpToPx(20),
                    dpToPx(20),
                    dpToPx(20),
                    historyInfoCard.height + dpToPx(28)
                )
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        routePolyline?.remove()
        startMarker?.remove()
        endMarker?.remove()
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
