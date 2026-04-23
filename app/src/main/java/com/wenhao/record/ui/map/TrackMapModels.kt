package com.wenhao.record.ui.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.wenhao.record.map.GeoCoordinate

@Immutable
data class TrackMapViewportPadding(
    val top: Dp = 0.dp,
    val start: Dp = 0.dp,
    val end: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

@Immutable
data class TrackMapSceneState(
    val polylines: List<TrackMapPolyline> = emptyList(),
    val heatPoints: List<TrackMapHeatPoint> = emptyList(),
    val markers: List<TrackMapMarker> = emptyList(),
    val currentLocation: GeoCoordinate? = null,
    val viewportRequest: TrackMapViewportRequest? = null,
)

@Immutable
data class TrackMapPolyline(
    val id: String,
    val points: List<GeoCoordinate>,
    val colorArgb: Int,
    val width: Double,
)

@Immutable
data class TrackMapHeatPoint(
    val id: String,
    val coordinate: GeoCoordinate,
    val intensity: Double,
    val radius: Double,
)

@Immutable
data class TrackMapMarker(
    val id: String,
    val coordinate: GeoCoordinate,
    val kind: TrackMapMarkerKind,
)

enum class TrackMapMarkerKind {
    HOME,
    START,
    END,
    CENTER,
}

sealed interface TrackMapViewportRequest {
    val sequence: Long

    data class Center(
        override val sequence: Long,
        val coordinate: GeoCoordinate,
        val zoom: Double,
    ) : TrackMapViewportRequest

    data class Fit(
        override val sequence: Long,
        val coordinates: List<GeoCoordinate>,
        val singlePointZoom: Double = 17.0,
        val maxZoom: Double? = null,
    ) : TrackMapViewportRequest
}

internal fun GeoCoordinate.toMapboxPoint(): Point = Point.fromLngLat(longitude, latitude)
