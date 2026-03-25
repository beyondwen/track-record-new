package com.wenhao.record.ui.main

import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.Gradient
import com.baidu.mapapi.map.HeatMap
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.map.WeightedLatLng
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.tracking.AutoTrackSession
import com.wenhao.record.data.tracking.TrackPathSanitizer
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.MapMarkerIconFactory
import java.util.Calendar

class HomeMapController(
    private val activity: AppCompatActivity,
    val mapView: MapView
) {
    private val aMap: BaiduMap = mapView.map

    private val activeTrackPolylines = mutableListOf<Polyline>()
    private val todayTrackPolylines = mutableListOf<Polyline>()
    private val currentTrackPoints = mutableListOf<TrackPoint>()
    private val todayTrackPoints = mutableListOf<LatLng>()

    private var homeMarker: Marker? = null
    private var liveStartMarker: Marker? = null
    private var liveCurrentMarker: Marker? = null
    private var todayHeatMap: HeatMap? = null
    private var currentTrackSegments: List<List<TrackPoint>> = emptyList()

    var shouldRefit = true

    fun configure(previewLocation: LatLng?, hasLocationPermission: Boolean, defaultLatLng: LatLng) {
        mapView.showZoomControls(false)
        mapView.showScaleControl(false)
        aMap.uiSettings.setCompassEnabled(false)
        aMap.uiSettings.setRotateGesturesEnabled(false)
        aMap.uiSettings.setOverlookingGesturesEnabled(false)
        aMap.setBaiduHeatMapEnabled(aMap.isSupportBaiduHeatMap())

        val initialLocation = previewLocation ?: defaultLatLng
        updateMarker(initialLocation)
        aMap.setMapStatus(
            MapStatusUpdateFactory.newLatLngZoom(
                initialLocation,
                if (hasLocationPermission) 16f else 15f
            )
        )
    }

    fun hasActiveTrack(): Boolean = currentTrackPoints.isNotEmpty()

    fun hasTodayTracks(): Boolean = todayTrackPoints.isNotEmpty()

    fun render(session: AutoTrackSession?, previewLocation: LatLng?, todayHistoryItems: List<HistoryItem>) {
        val sanitizedTrack = TrackPathSanitizer.sanitize(
            points = session?.points.orEmpty(),
            sortByTimestamp = false
        )
        currentTrackPoints.clear()
        currentTrackPoints.addAll(sanitizedTrack.points)
        currentTrackSegments = sanitizedTrack.segments

        if (currentTrackPoints.isEmpty()) {
            clearActiveTrack()
            renderTodayTracks(todayHistoryItems)
            previewLocation?.let(::updateMarker)
            return
        }

        clearTodayTrackOverlays()
        removeTodayHeatMap()
        renderTrackPolyline(shouldFitMap = false)
        updateLiveTrackMarkers()
        updateMarker(currentTrackPoints.last().toLatLng())
        if (shouldRefit) {
            focusActiveTrackOnLatestPoint(forceZoom = false)
        }
    }

    fun centerOnPreviewLocation(latLng: LatLng, zoom: Float = 17f) {
        applyViewportPadding()
        aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, zoom))
    }

    fun updateCurrentLocation(latLng: LatLng, shouldCenter: Boolean) {
        updateMarker(latLng)
        if (shouldCenter) {
            applyViewportPadding()
            aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, 17f))
        }
    }

    fun showPreviewLocationIfIdle(previewLocation: LatLng?) {
        if (!hasActiveTrack()) {
            previewLocation?.let(::updateMarker)
        }
    }

    fun focusActiveTrackOnLatestPoint(forceZoom: Boolean) {
        mapView.post {
            val latestPoint = currentTrackPoints.lastOrNull()?.toLatLng() ?: return@post
            applyViewportPadding()
            val currentZoom = aMap.mapStatus?.zoom ?: 16f
            val targetZoom = if (forceZoom || currentZoom < 16f) 16.8f else currentZoom
            aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latestPoint, targetZoom))
            shouldRefit = false
        }
    }

    fun fitTodayTracksToMap(forceSinglePointZoom: Boolean) {
        mapView.post {
            when {
                todayTrackPoints.isEmpty() -> Unit
                todayTrackPoints.size == 1 -> {
                    if (forceSinglePointZoom || shouldRefit) {
                        applyViewportPadding()
                        aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(todayTrackPoints.first(), 17f))
                    }
                    shouldRefit = false
                }

                else -> {
                    val bounds = LatLngBounds.Builder().apply {
                        todayTrackPoints.forEach(::include)
                    }.build()
                    applyViewportPadding()
                    aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(bounds))
                    shouldRefit = false
                }
            }
        }
    }

    fun onResume() {
        mapView.onResume()
    }

    fun onPause() {
        mapView.onPause()
    }

    fun onDestroy() {
        clearActiveTrack()
        clearTodayTrackOverlays()
        removeTodayHeatMap()
        homeMarker?.remove()
        mapView.onDestroy()
    }

    private fun updateMarker(latLng: LatLng) {
        if (currentTrackPoints.isNotEmpty()) {
            homeMarker?.remove()
            homeMarker = null
            if (liveCurrentMarker == null) {
                liveCurrentMarker = aMap.addOverlay(
                    MarkerOptions()
                        .position(latLng)
                        .anchor(0.5f, 0.5f)
                        .icon(MapMarkerIconFactory.fromDrawableResource(activity, R.drawable.ic_route_end_marker))
                ) as Marker
            } else {
                liveCurrentMarker?.position = latLng
            }
            return
        }

        liveStartMarker?.remove()
        liveStartMarker = null
        liveCurrentMarker?.remove()
        liveCurrentMarker = null

        if (homeMarker == null) {
            homeMarker = aMap.addOverlay(
                MarkerOptions()
                    .position(latLng)
                    .anchor(0.5f, 0.5f)
                    .icon(MapMarkerIconFactory.fromDrawableResource(activity, R.drawable.ic_location))
            ) as Marker
        } else {
            homeMarker?.position = latLng
        }
    }

    private fun renderTrackPolyline(shouldFitMap: Boolean) {
        activeTrackPolylines.forEach { polyline -> polyline.remove() }
        activeTrackPolylines.clear()

        val drawableSegments = currentTrackSegments.filter { segment -> segment.size > 1 }
        if (drawableSegments.isEmpty()) {
            if (shouldFitMap) {
                focusActiveTrackOnLatestPoint(forceZoom = false)
            }
            return
        }

        drawableSegments.forEach { segment ->
            activeTrackPolylines += aMap.addOverlay(
                PolylineOptions()
                    .points(segment.map { point -> point.toLatLng() })
                    .color("#8B5CF6".toColorInt())
                    .width(12)
            ) as Polyline
        }

        if (shouldFitMap) {
            focusActiveTrackOnLatestPoint(forceZoom = false)
        }
    }

    private fun clearActiveTrack() {
        activeTrackPolylines.forEach { polyline -> polyline.remove() }
        activeTrackPolylines.clear()
        currentTrackSegments = emptyList()
        currentTrackPoints.clear()
        liveStartMarker?.remove()
        liveStartMarker = null
        liveCurrentMarker?.remove()
        liveCurrentMarker = null
    }

    private fun clearTodayTrackOverlays() {
        todayTrackPolylines.forEach { polyline -> polyline.remove() }
        todayTrackPolylines.clear()
        todayTrackPoints.clear()
    }

    private fun removeTodayHeatMap() {
        todayHeatMap?.removeHeatMap()
        todayHeatMap = null
    }

    private fun renderTodayTracks(historyItems: List<HistoryItem>) {
        clearTodayTrackOverlays()
        removeTodayHeatMap()

        val sanitizedTodayPoints = mutableListOf<TrackPoint>()

        historyItems.forEach { item ->
            if (!isSameDay(item.timestamp, System.currentTimeMillis())) return@forEach
            val sanitizedTrack = TrackPathSanitizer.sanitize(item.points, sortByTimestamp = true)
            sanitizedTodayPoints += sanitizedTrack.points
            todayTrackPoints.addAll(sanitizedTrack.points.map { point -> point.toLatLng() })
            sanitizedTrack.segments.filter { segment -> segment.size > 1 }.forEach { segment ->
                val polyline = aMap.addOverlay(
                    PolylineOptions()
                        .points(segment.map { point -> point.toLatLng() })
                        .color("#7A8B5CF6".toColorInt())
                        .width(10)
                ) as Polyline
                todayTrackPolylines += polyline
            }
        }

        renderTodayHeatMap(sanitizedTodayPoints)

        if (todayTrackPoints.isNotEmpty() && shouldRefit) {
            fitTodayTracksToMap(forceSinglePointZoom = false)
        }
    }

    private fun renderTodayHeatMap(points: List<TrackPoint>) {
        if (!aMap.isSupportBaiduHeatMap() || points.size < 3) return

        val weightedPoints = points.map { point -> WeightedLatLng(point.toLatLng()) }
        val gradient = Gradient(
            intArrayOf(
                "#33D9CCFF".toColorInt(),
                "#8A8B5CF6".toColorInt(),
                "#F26E47D7".toColorInt()
            ),
            floatArrayOf(0.2f, 0.6f, 1f)
        )

        todayHeatMap = HeatMap.Builder()
            .weightedData(weightedPoints)
            .radius(32)
            .opacity(0.72)
            .gradient(gradient)
            .build()
        todayHeatMap?.let(aMap::addHeatMap)
    }

    private fun updateLiveTrackMarkers() {
        val firstPoint = currentTrackPoints.firstOrNull()?.toLatLng() ?: return
        val lastPoint = currentTrackPoints.lastOrNull()?.toLatLng() ?: return

        if (liveStartMarker == null) {
            liveStartMarker = aMap.addOverlay(
                MarkerOptions()
                    .position(firstPoint)
                    .anchor(0.5f, 0.5f)
                    .icon(MapMarkerIconFactory.fromDrawableResource(activity, R.drawable.ic_route_start_marker))
            ) as Marker
        } else {
            liveStartMarker?.position = firstPoint
        }

        if (liveCurrentMarker == null) {
            liveCurrentMarker = aMap.addOverlay(
                MarkerOptions()
                    .position(lastPoint)
                    .anchor(0.5f, 0.5f)
                    .icon(MapMarkerIconFactory.fromDrawableResource(activity, R.drawable.ic_route_end_marker))
            ) as Marker
        } else {
            liveCurrentMarker?.position = lastPoint
        }
    }

    private fun applyViewportPadding() {
        aMap.setViewPadding(
            dpToPx(20),
            dpToPx(132),
            dpToPx(20),
            dpToPx(24)
        )
    }

    private fun isSameDay(firstTimestamp: Long, secondTimestamp: Long): Boolean {
        val first = Calendar.getInstance().apply { timeInMillis = firstTimestamp }
        val second = Calendar.getInstance().apply { timeInMillis = secondTimestamp }
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * activity.resources.displayMetrics.density).toInt()
    }
}
