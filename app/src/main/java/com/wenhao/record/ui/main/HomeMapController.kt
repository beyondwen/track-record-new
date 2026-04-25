package com.wenhao.record.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.tracking.TrackPathSanitizer
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.map.GeoCoordinate
import com.wenhao.record.ui.map.HistoryMapGeometryLimiter
import com.wenhao.record.ui.map.TrackMapPolyline
import com.wenhao.record.ui.map.TrackPolylineBuilder
import com.wenhao.record.ui.map.TrackMapSceneState
import com.wenhao.record.ui.map.TrackMapViewportRequest
import java.util.Calendar

class HomeMapController : HomeMapPort {
    override var renderState by mutableStateOf(TrackMapSceneState())
        private set

    override var shouldRefit = true

    private val currentTrackPoints = mutableListOf<TrackPoint>()
    private val todayTrackPoints = mutableListOf<GeoCoordinate>()
    private var currentTrackSegments: List<List<TrackPoint>> = emptyList()
    private var activeTrackPolylines: List<TrackMapPolyline> = emptyList()
    private var todayTrackPolylines: List<TrackMapPolyline> = emptyList()
    private var trackingEnabled = false
    private var viewportSequence = 0L

    override fun configure(previewLocation: GeoCoordinate?, hasLocationPermission: Boolean, defaultCoordinate: GeoCoordinate) {
        val initialLocation = previewLocation ?: defaultCoordinate
        issueCenter(initialLocation, if (hasLocationPermission) 16.0 else 15.0)
    }

    override fun hasActiveTrack(): Boolean = trackingEnabled && currentTrackPoints.isNotEmpty()

    override fun hasTodayTracks(): Boolean = todayTrackPoints.isNotEmpty()

    override fun render(
        runtimeSnapshot: TrackingRuntimeSnapshot?,
        previewLocation: GeoCoordinate?,
        todayHistoryItems: List<HistoryItem>,
        activeSessionPoints: List<TrackPoint>,
    ) {
        trackingEnabled = runtimeSnapshot?.isEnabled == true
        clearActiveTrack()
        renderTodayTracks(todayHistoryItems)
        renderActiveTrack(runtimeSnapshot, activeSessionPoints)

        val liveCoordinate = runtimeSnapshot?.latestPoint?.toGeoCoordinate()
        when {
            trackingEnabled && liveCoordinate != null -> {
                if (shouldRefit) {
                    focusActiveTrackOnLatestPoint(forceZoom = false)
                } else {
                    syncScene()
                }
            }

            else -> {
                clearActiveTrack()
                if (liveCoordinate != null || previewLocation != null) {
                    shouldRefit = shouldRefit
                }
                syncScene()
            }
        }
    }

    override fun centerOnPreviewLocation(latLng: GeoCoordinate, zoom: Double) {
        shouldRefit = false
        issueCenter(latLng, zoom)
    }

    override fun updateCurrentLocation(latLng: GeoCoordinate, shouldCenter: Boolean) {
        if (shouldCenter) {
            shouldRefit = false
            issueCenter(latLng, 17.0)
        } else {
            syncScene()
        }
    }

    override fun showPreviewLocationIfIdle(previewLocation: GeoCoordinate?) {
        if (!trackingEnabled) {
            syncScene()
        }
    }

    override fun focusActiveTrackOnLatestPoint(forceZoom: Boolean) {
        val latestPoint = currentTrackPoints.lastOrNull()?.toGeoCoordinate() ?: return
        issueCenter(
            coordinate = latestPoint,
            zoom = if (forceZoom) 16.8 else 16.2,
        )
        shouldRefit = false
    }

    override fun fitTodayTracksToMap(forceSinglePointZoom: Boolean) {
        when {
            todayTrackPoints.isEmpty() -> Unit
            todayTrackPoints.size == 1 -> {
                if (forceSinglePointZoom || shouldRefit) {
                    issueCenter(todayTrackPoints.first(), 17.0)
                }
                shouldRefit = false
            }

            else -> {
                issueFit(todayTrackPoints, maxZoom = if (forceSinglePointZoom) 17.5 else 16.8)
                shouldRefit = false
            }
        }
    }

    override fun onCleared() {
        clearActiveTrack()
        clearTodayTrackOverlays()
        syncScene(viewportRequest = null)
    }

    private fun renderTrackHeatmap() {
        val renderableSegments = TrackPathSanitizer.renderableSegments(currentTrackSegments)
        activeTrackPolylines = TrackPolylineBuilder.buildCompact(
            segments = renderableSegments,
            idPrefix = "active",
            width = 6.0,
            maxPointsPerSegment = 240,
            altitudeBuckets = 6,
        )
    }

    private fun renderActiveTrack(
        runtimeSnapshot: TrackingRuntimeSnapshot?,
        activeSessionPoints: List<TrackPoint>,
    ) {
        val shouldRenderActiveTrack = trackingEnabled &&
            runtimeSnapshot?.phase != TrackingPhase.IDLE &&
            activeSessionPoints.size >= 2
        if (!shouldRenderActiveTrack) {
            return
        }

        val sanitizedTrack = TrackPathSanitizer.sanitize(activeSessionPoints, sortByTimestamp = true)
        val renderableSegments = TrackPathSanitizer.renderableSegments(sanitizedTrack.segments)
        if (renderableSegments.isEmpty()) {
            clearActiveTrack()
            return
        }

        currentTrackPoints.clear()
        currentTrackPoints.addAll(renderableSegments.flatten())
        currentTrackSegments = renderableSegments
        renderTrackHeatmap()
    }

    private fun clearActiveTrack() {
        activeTrackPolylines = emptyList()
        currentTrackSegments = emptyList()
        currentTrackPoints.clear()
    }

    private fun clearTodayTrackOverlays() {
        todayTrackPolylines = emptyList()
        todayTrackPoints.clear()
    }

    private fun renderTodayTracks(historyItems: List<HistoryItem>) {
        clearTodayTrackOverlays()

        val sourceSegments = mutableListOf<List<TrackPoint>>()
        historyItems.forEach { item ->
            if (!isSameDay(item.timestamp, System.currentTimeMillis())) return@forEach
            val sanitizedTrack = TrackPathSanitizer.sanitize(item.points, sortByTimestamp = true)
            val renderableSegments = TrackPathSanitizer.renderableSegments(sanitizedTrack.segments)
            val geoPoints = renderableSegments.flatten().map(TrackPoint::toGeoCoordinate)
            todayTrackPoints.addAll(geoPoints)
            sourceSegments.addAll(renderableSegments)
        }
        val limitedSegments = HistoryMapGeometryLimiter.limitSegments(sourceSegments)
        todayTrackPoints.clear()
        todayTrackPoints.addAll(limitedSegments.flatten().map(TrackPoint::toGeoCoordinate))
        todayTrackPolylines = TrackPolylineBuilder.buildCompact(
            segments = limitedSegments,
            idPrefix = "today",
            width = 4.6,
            maxPointsPerSegment = 90,
            altitudeBuckets = 2,
        )

        if (todayTrackPoints.isNotEmpty() && shouldRefit) {
            fitTodayTracksToMap(forceSinglePointZoom = false)
        }
    }

    private fun syncScene(viewportRequest: TrackMapViewportRequest? = renderState.viewportRequest) {
        renderState = TrackMapSceneState(
            polylines = activeTrackPolylines + todayTrackPolylines,
            heatPoints = emptyList(),
            markers = emptyList(),
            viewportRequest = viewportRequest,
        )
    }

    private fun issueCenter(coordinate: GeoCoordinate, zoom: Double) {
        syncScene(
            viewportRequest = TrackMapViewportRequest.Center(
                sequence = nextViewportSequence(),
                coordinate = coordinate,
                zoom = zoom,
            )
        )
    }

    private fun issueFit(coordinates: List<GeoCoordinate>, maxZoom: Double? = null) {
        syncScene(
            viewportRequest = TrackMapViewportRequest.Fit(
                sequence = nextViewportSequence(),
                coordinates = coordinates.toList(),
                maxZoom = maxZoom,
            )
        )
    }

    private fun nextViewportSequence(): Long {
        viewportSequence += 1
        return viewportSequence
    }

    private fun isSameDay(firstTimestamp: Long, secondTimestamp: Long): Boolean {
        val first = Calendar.getInstance().apply { timeInMillis = firstTimestamp }
        val second = Calendar.getInstance().apply { timeInMillis = secondTimestamp }
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }
}
