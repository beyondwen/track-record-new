package com.wenhao.record.ui.main

import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.map.GeoCoordinate
import com.wenhao.record.ui.map.TrackMapSceneState

interface HomeMapPort {
    val renderState: TrackMapSceneState
    var shouldRefit: Boolean

    fun configure(previewLocation: GeoCoordinate?, hasLocationPermission: Boolean, defaultCoordinate: GeoCoordinate)
    fun hasActiveTrack(): Boolean
    fun hasTodayTracks(): Boolean
    fun render(runtimeSnapshot: TrackingRuntimeSnapshot?, previewLocation: GeoCoordinate?, todayHistoryItems: List<HistoryItem>)
    fun centerOnPreviewLocation(latLng: GeoCoordinate, zoom: Double = 17.0)
    fun updateCurrentLocation(latLng: GeoCoordinate, shouldCenter: Boolean)
    fun showPreviewLocationIfIdle(previewLocation: GeoCoordinate?)
    fun focusActiveTrackOnLatestPoint(forceZoom: Boolean)
    fun fitTodayTracksToMap(forceSinglePointZoom: Boolean)
    fun onCleared()
}
