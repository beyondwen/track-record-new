package com.wenhao.record.ui.map

import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.CircleLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs

internal object TrackRawPointStyleLayerManager {
    private const val SOURCE_ID = "track-raw-points-source"
    private const val LAYER_ID = "track-raw-points-layer"

    fun render(style: Style, points: List<TrackMapHeatPoint>) {
        if (points.isEmpty()) {
            remove(style)
            return
        }

        val featureCollection = FeatureCollection.fromFeatures(
            points.map { point -> Feature.fromGeometry(point.coordinate.toMapboxPoint()) }
        )
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
        if (source == null) {
            style.addSource(
                GeoJsonSource.Builder(SOURCE_ID)
                    .featureCollection(featureCollection)
                    .build()
            )
        } else {
            source.featureCollection(featureCollection)
        }

        if (!style.styleLayerExists(LAYER_ID)) {
            style.addLayer(
                CircleLayer(LAYER_ID, SOURCE_ID)
                    .circleColor(0xFFFF6B00.toInt())
                    .circleRadius(3.2)
                    .circleOpacity(0.72)
                    .circleStrokeColor(0xFFFFFFFF.toInt())
                    .circleStrokeWidth(0.8)
            )
        }
    }

    private fun remove(style: Style) {
        if (style.styleLayerExists(LAYER_ID)) {
            style.removeStyleLayer(LAYER_ID)
        }
        if (style.styleSourceExists(SOURCE_ID)) {
            style.removeStyleSource(SOURCE_ID)
        }
    }
}
