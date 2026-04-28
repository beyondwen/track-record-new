package com.wenhao.record.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.wenhao.record.BuildConfig
import com.wenhao.record.R
import com.wenhao.record.data.tracking.TrackPathSanitizer
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.map.GeoMath
import com.wenhao.record.map.GeoCoordinate
import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.ui.map.TrackMapHeatPoint
import com.wenhao.record.ui.map.TrackMapPolyline
import com.wenhao.record.ui.map.TrackMapSceneState
import com.wenhao.record.ui.map.TrackMapViewportRequest
import com.wenhao.record.ui.map.TrackPolylineBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KEY_CURRENT_TAB = "current_tab"

class MapViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val _renderState = MutableStateFlow(TrackMapSceneState())
    val renderState: StateFlow<TrackMapSceneState> = _renderState.asStateFlow()

    var shouldRefit = true

    private val currentTrackPoints = mutableListOf<TrackPoint>()
    private var currentTrackSegments: List<List<TrackPoint>> = emptyList()
    private var activeTrackPolylines: List<TrackMapPolyline> = emptyList()
    private var trackingEnabled = false
    private var currentLocation: GeoCoordinate? = null
    private var viewportSequence = 0L
    private var renderJob: Job? = null
    private var lastActiveTrackToken = Long.MIN_VALUE

    fun configure(previewLocation: GeoCoordinate?, hasLocationPermission: Boolean, defaultCoordinate: GeoCoordinate) {
        val initialLocation = previewLocation ?: defaultCoordinate
        issueCenter(initialLocation, if (hasLocationPermission) 16.0 else 15.0)
    }

    fun hasActiveTrack(): Boolean = trackingEnabled && currentTrackPoints.isNotEmpty()

    fun render(
        runtimeSnapshot: TrackingRuntimeSnapshot?,
        previewLocation: GeoCoordinate?,
        activeSessionPoints: List<TrackPoint>,
    ) {
        trackingEnabled = runtimeSnapshot?.isEnabled == true
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            renderActiveTrackIfNeeded(runtimeSnapshot, activeSessionPoints)

            val liveCoordinate = runtimeSnapshot?.latestPoint?.toGeoCoordinate()
            currentLocation = liveCoordinate ?: previewLocation
            when {
                trackingEnabled && liveCoordinate != null -> {
                    syncScene(heatPoints = emptyList())
                    if (shouldRefit) {
                        focusActiveTrackOnLatestPoint(forceZoom = false)
                    }
                }

                else -> {
                    clearActiveTrack()
                    syncScene(heatPoints = emptyList())
                }
            }
        }
    }

    fun centerOnPreviewLocation(latLng: GeoCoordinate, zoom: Double) {
        shouldRefit = false
        issueCenter(latLng, zoom)
    }

    fun updateCurrentLocation(latLng: GeoCoordinate, shouldCenter: Boolean) {
        if (!shouldCenter && currentLocation.isSameVisibleLocation(latLng)) {
            return
        }
        currentLocation = latLng
        if (shouldCenter) {
            shouldRefit = false
            issueCenter(latLng, 17.0)
        } else {
            syncScene()
        }
    }

    fun showPreviewLocationIfIdle(previewLocation: GeoCoordinate?) {
        if (!trackingEnabled) {
            syncScene()
        }
    }

    fun focusActiveTrackOnLatestPoint(forceZoom: Boolean) {
        val latestPoint = currentTrackPoints.lastOrNull()?.toGeoCoordinate() ?: return
        issueCenter(
            coordinate = latestPoint,
            zoom = if (forceZoom) 16.8 else 16.2,
        )
        shouldRefit = false
    }

    fun onClearedInternal() {
        renderJob?.cancel()
        clearActiveTrack()
        lastActiveTrackToken = Long.MIN_VALUE
        syncScene(viewportRequest = null, heatPoints = emptyList())
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

    private fun renderActiveTrackIfNeeded(
        runtimeSnapshot: TrackingRuntimeSnapshot?,
        activeSessionPoints: List<TrackPoint>,
    ) {
        val nextToken = activeTrackToken(runtimeSnapshot, activeSessionPoints)
        if (nextToken == lastActiveTrackToken) return
        lastActiveTrackToken = nextToken

        val shouldRenderActiveTrack = trackingEnabled &&
            runtimeSnapshot?.phase != TrackingPhase.IDLE &&
            activeSessionPoints.size >= 2
        if (!shouldRenderActiveTrack) {
            clearActiveTrack()
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

    private fun syncScene(
        viewportRequest: TrackMapViewportRequest? = _renderState.value.viewportRequest,
        heatPoints: List<TrackMapHeatPoint> = _renderState.value.heatPoints,
    ) {
        val nextState = TrackMapSceneState(
            polylines = activeTrackPolylines,
            heatPoints = heatPoints,
            markers = emptyList(),
            currentLocation = currentLocation,
            viewportRequest = viewportRequest,
        )
        if (_renderState.value != nextState) {
            _renderState.value = nextState
        }
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

    private fun activeTrackToken(
        runtimeSnapshot: TrackingRuntimeSnapshot?,
        activeSessionPoints: List<TrackPoint>,
    ): Long {
        var token = if (runtimeSnapshot?.isEnabled == true) 1L else 0L
        token = token * 31 + (runtimeSnapshot?.phase?.ordinal?.toLong() ?: -1L)
        token = token * 31 + activeSessionPoints.size
        token = token * 31 + (activeSessionPoints.firstOrNull()?.timestampMillis ?: 0L)
        token = token * 31 + (activeSessionPoints.lastOrNull()?.timestampMillis ?: 0L)
        return token
    }

    private fun GeoCoordinate?.isSameVisibleLocation(next: GeoCoordinate): Boolean {
        val current = this ?: return false
        return GeoMath.distanceMeters(
            current.latitude,
            current.longitude,
            next.latitude,
            next.longitude,
        ) < MIN_VISIBLE_LOCATION_MOVE_METERS
    }

    companion object {
        private const val MIN_VISIBLE_LOCATION_MOVE_METERS = 3f

        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    @Suppress("UNCHECKED_CAST")
                    return MapViewModel(application, savedStateHandle) as T
                }
            }
        }
    }
}
