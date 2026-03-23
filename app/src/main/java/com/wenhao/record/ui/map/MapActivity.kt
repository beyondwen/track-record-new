package com.wenhao.record.ui.map

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
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
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.map.MapMarkerIconFactory

class MapActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_DAY_START = "extra_day_start"

        fun createHistoryIntent(context: Context, dayStartMillis: Long): Intent {
            return Intent(context, MapActivity::class.java).putExtra(EXTRA_DAY_START, dayStartMillis)
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
    private lateinit var tvRouteQuality: TextView
    private lateinit var tvRouteSummary: TextView
    private lateinit var tvRoutePointCount: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnRefitRoute: ImageView

    private val routePolylines = mutableListOf<Polyline>()
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var routeBounds: LatLngBounds? = null
    private var singlePoint: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        applyWindowInsets()

        mapView = findViewById(R.id.mapView)
        aMap = mapView.map
        historyInfoCard = findViewById(R.id.historyInfoCard)
        tvRouteTitle = findViewById(R.id.tvRouteTitle)
        tvRouteTime = findViewById(R.id.tvRouteTime)
        tvRouteDistance = findViewById(R.id.tvRouteDistance)
        tvRouteDuration = findViewById(R.id.tvRouteDuration)
        tvRouteSpeed = findViewById(R.id.tvRouteSpeed)
        tvRouteQuality = findViewById(R.id.tvRouteQuality)
        tvRouteSummary = findViewById(R.id.tvRouteSummary)
        tvRoutePointCount = findViewById(R.id.tvRoutePointCount)
        btnBack = findViewById(R.id.btnBack)
        btnRefitRoute = findViewById(R.id.btnRefitRoute)

        btnBack.setOnClickListener { finish() }
        btnRefitRoute.setOnClickListener { refitRoute() }

        mapView.showZoomControls(false)
        mapView.showScaleControl(false)
        aMap.uiSettings.setCompassEnabled(false)
        aMap.uiSettings.setRotateGesturesEnabled(false)
        aMap.uiSettings.setOverlookingGesturesEnabled(false)

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
        tvRouteTitle.text = item.displayTitle
        tvRouteTime.text = item.formattedDateDetail
        tvRouteDistance.text = item.formattedDistance
        tvRouteDuration.text = item.formattedDurationDetail
        tvRouteSpeed.text = item.formattedSpeed
        tvRouteQuality.text = item.quality.badgeLabel
        tvRouteSummary.text = item.summary
        tvRoutePointCount.text = item.pointCountLabel

        val allPoints = item.points.map { it.toLatLng() }
        singlePoint = allPoints.firstOrNull()
        routeBounds = if (allPoints.size > 1) {
            LatLngBounds.Builder().apply { allPoints.forEach(::include) }.build()
        } else {
            null
        }

        routePolylines.forEach { polyline -> polyline.remove() }
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
                .title(getString(R.string.history_map_start))
                .icon(MapMarkerIconFactory.fromDrawableResource(this, R.drawable.ic_route_start_marker))
        ) as Marker

        endMarker = aMap.addOverlay(
            MarkerOptions()
                .position(endPoint)
                .title(getString(R.string.history_map_end))
                .icon(MapMarkerIconFactory.fromDrawableResource(this, R.drawable.ic_route_end_marker))
        ) as Marker

        historyInfoCard.post {
            refitRoute()
        }
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = findViewById<View>(android.R.id.content)
        val backButton = findViewById<View>(R.id.btnBack)
        val refitButton = findViewById<View>(R.id.btnRefitRoute)
        val infoCard = findViewById<View>(R.id.historyInfoCard)

        val backLayout = backButton.layoutParams as ViewGroup.MarginLayoutParams
        val backTopMargin = backLayout.topMargin
        val backStartMargin = backLayout.marginStart

        val refitLayout = refitButton.layoutParams as ViewGroup.MarginLayoutParams
        val refitBottomMargin = refitLayout.bottomMargin
        val refitEndMargin = refitLayout.marginEnd

        val infoCardLayout = infoCard.layoutParams as ViewGroup.MarginLayoutParams
        val infoCardBottomMargin = infoCardLayout.bottomMargin
        val infoCardStartMargin = infoCardLayout.marginStart
        val infoCardEndMargin = infoCardLayout.marginEnd

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            backButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = backTopMargin + systemBars.top
                marginStart = backStartMargin + systemBars.left
            }

            refitButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = refitBottomMargin + systemBars.bottom
                marginEnd = refitEndMargin + systemBars.right
            }

            infoCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = infoCardBottomMargin + systemBars.bottom
                marginStart = infoCardStartMargin + systemBars.left
                marginEnd = infoCardEndMargin + systemBars.right
            }

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun refitRoute() {
        val point = singlePoint
        val bounds = routeBounds

        when {
            point != null && bounds == null -> {
                aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, 17f))
            }

            bounds != null -> {
                val bottomMargin = (historyInfoCard.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                aMap.setViewPadding(
                    dpToPx(20),
                    dpToPx(20),
                    dpToPx(20),
                    historyInfoCard.height + bottomMargin + dpToPx(12)
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
        routePolylines.forEach { polyline -> polyline.remove() }
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
