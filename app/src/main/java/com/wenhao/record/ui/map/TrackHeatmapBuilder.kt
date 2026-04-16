package com.wenhao.record.ui.map

import com.wenhao.record.map.GeoMath
import com.wenhao.record.map.GeoCoordinate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

internal object TrackHeatmapBuilder {
    fun buildPath(
        segments: List<List<GeoCoordinate>>,
        idPrefix: String,
        targetBucketsPerAxis: Int? = null,
        minimumCellSizeDegrees: Double = 0.000025,
        corridorStepMeters: Double = 18.0,
    ): List<TrackMapHeatPoint> {
        return build(
            points = densifySegments(
                segments = segments,
                corridorStepMeters = corridorStepMeters,
            ),
            idPrefix = idPrefix,
            targetBucketsPerAxis = targetBucketsPerAxis,
            minimumCellSizeDegrees = minimumCellSizeDegrees,
        )
    }

    fun build(
        points: List<GeoCoordinate>,
        idPrefix: String,
        targetBucketsPerAxis: Int? = null,
        minimumCellSizeDegrees: Double = 0.000025,
    ): List<TrackMapHeatPoint> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) {
            return listOf(
                TrackMapHeatPoint(
                    id = "$idPrefix-0",
                    coordinate = points.first(),
                    intensity = 1.0,
                    radius = 18.0,
                )
            )
        }

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }
        val latRange = max(maxLat - minLat, minimumCellSizeDegrees)
        val lngRange = max(maxLng - minLng, minimumCellSizeDegrees)
        val bucketsPerAxis = targetBucketsPerAxis ?: when {
            points.size < 24 -> 36
            points.size < 120 -> 48
            points.size < 320 -> 56
            else -> 64
        }
        val latCell = max(latRange / bucketsPerAxis, minimumCellSizeDegrees)
        val lngCell = max(lngRange / bucketsPerAxis, minimumCellSizeDegrees)

        val buckets = linkedMapOf<Pair<Int, Int>, HeatBucket>()
        points.forEach { point ->
            val row = floor((point.latitude - minLat) / latCell).toInt()
            val column = floor((point.longitude - minLng) / lngCell).toInt()
            val key = row to column
            val bucket = buckets.getOrPut(key) { HeatBucket() }
            bucket.add(point)
        }

        val maxCount = buckets.values.maxOf { it.count }.coerceAtLeast(1)
        return buckets.entries
            .sortedBy { it.value.count }
            .mapIndexed { index, (_, bucket) ->
                val normalized = bucket.count.toDouble() / maxCount.toDouble()
                val adjustedIntensity = normalized.pow(0.82)
                TrackMapHeatPoint(
                    id = "$idPrefix-$index",
                    coordinate = bucket.center,
                    intensity = adjustedIntensity.coerceIn(0.10, 1.0),
                    radius = 9.0 + adjustedIntensity * 14.0,
                )
            }
    }

    private fun densifySegments(
        segments: List<List<GeoCoordinate>>,
        corridorStepMeters: Double,
    ): List<GeoCoordinate> {
        if (segments.isEmpty()) return emptyList()

        return buildList {
            for (segment in segments) {
                if (segment.isEmpty()) continue
                add(segment.first())
                for (i in 0 until segment.size - 1) {
                    val start = segment[i]
                    val end = segment[i + 1]
                    val distanceMeters = GeoMath.distanceMeters(
                        start.latitude,
                        start.longitude,
                        end.latitude,
                        end.longitude,
                    ).toDouble()
                    val steps = ceil(distanceMeters / corridorStepMeters)
                        .toInt()
                        .coerceIn(1, 24)
                    repeat(steps) { index ->
                        val fraction = (index + 1).toDouble() / steps.toDouble()
                        add(
                            GeoCoordinate(
                                latitude = lerp(start.latitude, end.latitude, fraction),
                                longitude = lerp(start.longitude, end.longitude, fraction),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun lerp(start: Double, end: Double, fraction: Double): Double {
        return start + (end - start) * fraction
    }

    private class HeatBucket {
        private var totalLatitude = 0.0
        private var totalLongitude = 0.0
        var count: Int = 0
            private set

        fun add(point: GeoCoordinate) {
            totalLatitude += point.latitude
            totalLongitude += point.longitude
            count += 1
        }

        val center: GeoCoordinate
            get() = GeoCoordinate(
                latitude = totalLatitude / count,
                longitude = totalLongitude / count,
            )
    }
}
