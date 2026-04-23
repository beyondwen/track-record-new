package com.wenhao.record.ui.map

import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString

internal data class TrackRouteStylePayload(
    val groups: List<TrackRouteStyleGroup>,
) {
    companion object {
        fun fromPolylines(polylines: List<TrackMapPolyline>): TrackRouteStylePayload {
            val groups = polylines
                .filter { polyline -> polyline.points.size >= 2 }
                .groupBy { polyline -> RouteStyleKey(polyline.colorArgb, polyline.width) }
                .entries
                .sortedWith(compareBy({ it.key.colorArgb }, { it.key.width }))
                .mapIndexed { index, entry ->
                    TrackRouteStyleGroup(
                        layerId = "$ROUTE_LAYER_PREFIX$index",
                        sourceId = "$ROUTE_SOURCE_PREFIX$index",
                        colorArgb = entry.key.colorArgb,
                        width = entry.key.width,
                        featureCollection = FeatureCollection.fromFeatures(
                            entry.value.map { polyline ->
                                Feature.fromGeometry(
                                    LineString.fromLngLats(
                                        polyline.points.map { point -> point.toMapboxPoint() }
                                    )
                                ).also { feature ->
                                    feature.addStringProperty("id", polyline.id)
                                }
                            }
                        ),
                    )
                }

            return TrackRouteStylePayload(groups)
        }
    }
}

internal data class TrackRouteStyleGroup(
    val layerId: String,
    val sourceId: String,
    val colorArgb: Int,
    val width: Double,
    val featureCollection: FeatureCollection,
)

private data class RouteStyleKey(
    val colorArgb: Int,
    val width: Double,
)

internal const val ROUTE_LAYER_PREFIX = "track-route-layer-"
internal const val ROUTE_SOURCE_PREFIX = "track-route-source-"
