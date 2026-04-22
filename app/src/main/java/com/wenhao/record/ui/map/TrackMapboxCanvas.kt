package com.wenhao.record.ui.map

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.wenhao.record.BuildConfig
import com.wenhao.record.R
import com.wenhao.record.map.MapMarkerIconFactory
import com.wenhao.record.ui.designsystem.TrackMapCenterIndicator
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TrackMapboxCanvas(
    state: TrackMapSceneState,
    accessToken: String,
    modifier: Modifier = Modifier,
    viewportPadding: TrackMapViewportPadding = TrackMapViewportPadding(),
    showCenterIndicator: Boolean = false,
    showUserLocationPuck: Boolean = false,
    interactive: Boolean = true,
    onUserGestureMove: (() -> Unit)? = null,
    snapshotCacheKey: String? = null,
    onSnapshotCached: (() -> Unit)? = null,
) {
    val resolvedAccessToken = resolveMapboxAccessToken(
        runtimeToken = accessToken,
        bundledToken = BuildConfig.MAPBOX_ACCESS_TOKEN,
    )
    if (resolvedAccessToken.isNotBlank()) {
        MapboxOptions.accessToken = resolvedAccessToken
    }

    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Map Preview",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        return
    }

    if (!isMapboxAccessTokenConfigured(resolvedAccessToken)) {
        MapboxUnavailablePlaceholder(modifier = modifier)
        return
    }

    var snapshotBitmap by remember(snapshotCacheKey) {
        mutableStateOf(snapshotCacheKey?.let(TrackMapSnapshotCache::get))
    }
    var snapshotRequested by remember(snapshotCacheKey) { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val shouldCaptureSnapshot = !interactive && !snapshotCacheKey.isNullOrBlank()
    val density = LocalDensity.current
    val indicatorOffset = remember(viewportPadding, density) {
        with(density) {
            IntOffset(
                x = ((viewportPadding.start - viewportPadding.end) / 2).roundToPx(),
                y = ((viewportPadding.top - viewportPadding.bottom) / 2).roundToPx(),
            )
        }
    }
    snapshotBitmap?.let { bitmap ->
        Box(modifier = modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (showCenterIndicator) {
                TrackMapCenterIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { indicatorOffset },
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val mapViewportState = rememberMapViewportState()
    var gestureHost by remember { mutableStateOf<MoveGestureListenerHost?>(null) }
    val moveGestureBinding = remember { MoveGestureListenerBinding() }
    val mapState = rememberMapState(
        key = buildString {
            append(if (interactive) "interactive" else "static-preview")
            if (!snapshotCacheKey.isNullOrBlank()) append("-").append(snapshotCacheKey)
        }
    ) {
        gesturesSettings = GesturesSettings {
            rotateEnabled = interactive
            pinchToZoomEnabled = interactive
            scrollEnabled = interactive
            simultaneousRotateAndPinchToZoomEnabled = interactive
            pitchEnabled = interactive
            doubleTapToZoomInEnabled = interactive
            doubleTouchToZoomOutEnabled = interactive
            quickZoomEnabled = interactive
            pinchToZoomDecelerationEnabled = interactive
            rotateDecelerationEnabled = interactive
            scrollDecelerationEnabled = interactive
            increaseRotateThresholdWhenPinchingToZoom = interactive
            increasePinchToZoomThresholdWhenRotating = interactive
            pinchScrollEnabled = interactive
        }
    }
    val resolvedPadding = remember(viewportPadding, density) {
        with(density) {
            EdgeInsets(
                viewportPadding.top.toPx().toDouble(),
                viewportPadding.start.toPx().toDouble(),
                viewportPadding.bottom.toPx().toDouble(),
                viewportPadding.end.toPx().toDouble(),
            )
        }
    }
    val homeIcon = remember(context) {
        IconImage(MapMarkerIconFactory.bitmapFromDrawableResource(context, R.drawable.ic_location))
    }
    val startIcon = remember(context) {
        IconImage(MapMarkerIconFactory.bitmapFromDrawableResource(context, R.drawable.ic_route_start_marker))
    }
    val endIcon = remember(context) {
        IconImage(MapMarkerIconFactory.bitmapFromDrawableResource(context, R.drawable.ic_route_end_marker))
    }
    val centerIcon = remember(context) {
        IconImage(MapMarkerIconFactory.bitmapFromDrawableResource(context, R.drawable.ic_route_cluster_center))
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            mapState = mapState,
            compass = {},
            scaleBar = {},
        ) {
            state.polylines.forEach { polyline ->
                key(polyline.id) {
                    val mapboxPoints = remember(polyline.points) {
                        polyline.points.map { it.toMapboxPoint() }
                    }
                    PolylineAnnotation(points = mapboxPoints) {
                        lineColor = Color(polyline.colorArgb)
                        lineWidth = polyline.width
                        lineJoin = LineJoin.ROUND
                        lineOpacity = 0.96
                    }
                }
            }

            state.markers.forEach { marker ->
                val icon = when (marker.kind) {
                    TrackMapMarkerKind.HOME -> homeIcon
                    TrackMapMarkerKind.START -> startIcon
                    TrackMapMarkerKind.END -> endIcon
                    TrackMapMarkerKind.CENTER -> centerIcon
                }
                key(marker.id) {
                    PointAnnotation(point = marker.coordinate.toMapboxPoint()) {
                        iconImage = icon
                        iconAnchor = IconAnchor.CENTER
                        iconSize = when (marker.kind) {
                            TrackMapMarkerKind.HOME -> 1.0
                            TrackMapMarkerKind.CENTER -> 0.92
                            TrackMapMarkerKind.START,
                            TrackMapMarkerKind.END,
                            -> 0.92
                        }
                    }
                }
            }

            if (shouldCaptureSnapshot && snapshotRequested) {
                MapEffect(snapshotCacheKey, snapshotRequested) { mapView ->
                    mapView.snapshot { bitmap ->
                        if (bitmap != null && !snapshotCacheKey.isNullOrBlank()) {
                            mainHandler.post {
                                TrackMapSnapshotCache.put(snapshotCacheKey, bitmap)
                                snapshotBitmap = bitmap
                                onSnapshotCached?.invoke()
                            }
                        }
                    }
                }
            }

            MapEffect(state.viewportRequest?.sequence, resolvedPadding) { mapView ->
                val request = state.viewportRequest
                if (request is TrackMapViewportRequest.Center) {
                    mapView.mapboxMap.setCamera(
                        cameraOptions {
                            center(request.coordinate.toMapboxPoint())
                            zoom(request.zoom)
                            padding(resolvedPadding)
                        }
                    )
                }
            }

            MapEffect(Unit) { mapView ->
                gestureHost = MapboxMoveGestureListenerHost(mapView)
            }

            MapEffect(showUserLocationPuck) { mapView ->
                mapView.location.updateSettings {
                    enabled = showUserLocationPuck
                    locationPuck = createDefault2DPuck(withBearing = true)
                    puckBearingEnabled = showUserLocationPuck
                    puckBearing = PuckBearing.HEADING
                    showAccuracyRing = false
                    pulsingEnabled = false
                }
            }
        }

        if (showCenterIndicator) {
            TrackMapCenterIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { indicatorOffset },
            )
        }
    }

    LaunchedEffect(state.viewportRequest?.sequence) {
        when (val request = state.viewportRequest) {
            null -> Unit
            is TrackMapViewportRequest.Center -> Unit

            is TrackMapViewportRequest.Fit -> {
                val coordinates = request.coordinates
                when (coordinates.size) {
                    0 -> Unit
                    1 -> {
                        mapViewportState.easeTo(
                            cameraOptions {
                                center(coordinates.first().toMapboxPoint())
                                zoom(request.singlePointZoom)
                                padding(resolvedPadding)
                            }
                        )
                    }

                    else -> {
                        val camera = mapViewportState.cameraForCoordinates(
                            coordinates = coordinates.map { it.toMapboxPoint() },
                            camera = cameraOptions { padding(resolvedPadding) },
                            maxZoom = request.maxZoom,
                        )
                        mapViewportState.easeTo(camera)
                    }
                }
            }
        }
    }

    LaunchedEffect(shouldCaptureSnapshot, state.viewportRequest?.sequence) {
        if (shouldCaptureSnapshot && snapshotBitmap == null && !snapshotRequested) {
            mapState.mapIdleEvents.first()
            snapshotRequested = true
        }
    }

    DisposableEffect(gestureHost, interactive, onUserGestureMove) {
        moveGestureBinding.bind(
            host = gestureHost,
            interactive = interactive,
            onUserGestureMove = onUserGestureMove,
        )
        onDispose {
            moveGestureBinding.clear()
        }
    }
}

@Composable
private fun MapboxUnavailablePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            tonalElevation = 2.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "地图暂不可用",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "当前设备尚未配置 Mapbox Token，请先到设置页输入并保存后再使用地图。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

internal fun isMapboxAccessTokenConfigured(token: String): Boolean {
    val normalized = token.trim()
    if (normalized.isEmpty()) return false
    return !normalized.equals("YOUR_MAPBOX_ACCESS_TOKEN", ignoreCase = true)
}

private class MapboxMoveGestureListenerHost(
    private val mapView: MapView,
) : MoveGestureListenerHost {
    override fun addOnMoveListener(listener: com.mapbox.maps.plugin.gestures.OnMoveListener) {
        mapView.gestures.addOnMoveListener(listener)
    }

    override fun removeOnMoveListener(listener: com.mapbox.maps.plugin.gestures.OnMoveListener) {
        mapView.gestures.removeOnMoveListener(listener)
    }
}
