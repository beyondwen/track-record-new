package com.wenhao.record.ui.map

import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs

internal object TrackRouteStyleLayerManager {

    fun render(style: Style, polylines: List<TrackMapPolyline>) {
        val payload = TrackRouteStylePayload.fromPolylines(polylines)
        removeStaleRouteLayers(style, payload.groups.map { group -> group.layerId }.toSet())
        removeStaleRouteSources(style, payload.groups.map { group -> group.sourceId }.toSet())

        payload.groups.forEach { group ->
            val source = style.getSourceAs<GeoJsonSource>(group.sourceId)
            if (source == null) {
                style.addSource(
                    GeoJsonSource.Builder(group.sourceId)
                        .featureCollection(group.featureCollection)
                        .tolerance(0.85)
                        .build()
                )
            } else {
                source.featureCollection(group.featureCollection)
            }

            if (!style.styleLayerExists(group.layerId)) {
                style.addLayer(
                    LineLayer(group.layerId, group.sourceId)
                        .lineColor(group.colorArgb)
                        .lineWidth(group.width)
                        .lineJoin(LineJoin.ROUND)
                        .lineCap(LineCap.ROUND)
                        .lineOpacity(0.96)
                )
            }
        }
    }

    private fun removeStaleRouteLayers(style: Style, activeLayerIds: Set<String>) {
        style.styleLayers
            .map { layer -> layer.id }
            .filter { layerId -> layerId.startsWith(ROUTE_LAYER_PREFIX) && layerId !in activeLayerIds }
            .forEach { layerId -> style.removeStyleLayer(layerId) }
    }

    private fun removeStaleRouteSources(style: Style, activeSourceIds: Set<String>) {
        style.styleSources
            .map { source -> source.id }
            .filter { sourceId -> sourceId.startsWith(ROUTE_SOURCE_PREFIX) && sourceId !in activeSourceIds }
            .forEach { sourceId -> style.removeStyleSource(sourceId) }
    }
}
