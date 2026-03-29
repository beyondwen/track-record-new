package com.wenhao.record.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.wenhao.record.data.tracking.TrackPathSanitizer
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.ui.map.TrackAltitudePalette
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun HistoryRoutePreviewCanvas(
    segments: List<List<TrackPoint>>,
    modifier: Modifier = Modifier,
) {
    val renderableSegments = TrackPathSanitizer.renderableSegments(segments)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp),
    ) {
        val inset = 6.dp.toPx()
        val rectTopLeft = Offset(inset, inset)
        val rectSize = Size(size.width - inset * 2, size.height - inset * 2)
        val centerY = rectTopLeft.y + rectSize.height / 2f
        val points = renderableSegments.flatten()
        val guideColor = Color(0x3388A2A7)
        val startColor = Color(0xFF35C77D)
        val endColor = Color(0xFFFF6B6B)
        val cornerRadius = 16.dp.toPx()

        drawPreviewMapBackdrop(
            rectTopLeft = rectTopLeft,
            rectSize = rectSize,
            cornerRadius = cornerRadius,
            points = points,
        )

        drawRoundRect(
            color = guideColor,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = 1.dp.toPx()),
        )

        if (points.isEmpty()) {
            drawLine(
                color = guideColor,
                start = Offset(rectTopLeft.x + 10.dp.toPx(), centerY),
                end = Offset(rectTopLeft.x + rectSize.width - 10.dp.toPx(), centerY),
                strokeWidth = 1.dp.toPx(),
            )
            return@Canvas
        }

        if (points.size == 1) {
            drawCircle(
                color = startColor,
                radius = 5.dp.toPx(),
                center = Offset(rectTopLeft.x + rectSize.width / 2f, centerY),
            )
            return@Canvas
        }

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }
        val latRange = max(maxLat - minLat, 0.00001)
        val lngRange = max(maxLng - minLng, 0.00001)
        val altitudeRange = TrackAltitudePalette.altitudeRange(points)

        val projectedSegments = renderableSegments.map { segment ->
            segment.map { point ->
                project(
                    point = point,
                    left = rectTopLeft.x,
                    top = rectTopLeft.y,
                    width = rectSize.width,
                    height = rectSize.height,
                    minLat = minLat,
                    minLng = minLng,
                    latRange = latRange,
                    lngRange = lngRange,
                )
            }
        }
        renderableSegments.zip(projectedSegments).forEach { (segment, projectedSegment) ->
            if (segment.size < 2 || projectedSegment.size < 2) return@forEach
            segment.zipWithNext().zip(projectedSegment.zipWithNext()).forEach { (trackPair, projectedPair) ->
                val averageAltitude = listOfNotNull(
                    trackPair.first.altitudeMeters,
                    trackPair.second.altitudeMeters,
                ).average().takeUnless { it.isNaN() }
                drawLine(
                    color = TrackAltitudePalette.colorForAltitude(averageAltitude, altitudeRange),
                    start = projectedPair.first,
                    end = projectedPair.second,
                    strokeWidth = 3.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        val start = renderableSegments.firstOrNull { it.isNotEmpty() }?.first() ?: points.first()
        val end = renderableSegments.lastOrNull { it.isNotEmpty() }?.last() ?: points.last()
        val startProjected = project(
            point = start,
            left = rectTopLeft.x,
            top = rectTopLeft.y,
            width = rectSize.width,
            height = rectSize.height,
            minLat = minLat,
            minLng = minLng,
            latRange = latRange,
            lngRange = lngRange,
        )
        val endProjected = project(
            point = end,
            left = rectTopLeft.x,
            top = rectTopLeft.y,
            width = rectSize.width,
            height = rectSize.height,
            minLat = minLat,
            minLng = minLng,
            latRange = latRange,
            lngRange = lngRange,
        )

        drawCircle(color = startColor, radius = 4.dp.toPx(), center = startProjected)
        drawCircle(color = endColor, radius = 4.dp.toPx(), center = endProjected)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewMapBackdrop(
    rectTopLeft: Offset,
    rectSize: Size,
    cornerRadius: Float,
    points: List<TrackPoint>,
) {
    val left = rectTopLeft.x
    val top = rectTopLeft.y
    val right = left + rectSize.width
    val bottom = top + rectSize.height
    val roundRect = RoundRect(
        rect = Rect(rectTopLeft, rectSize),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
    )
    val seed = previewSeed(points)

    drawRoundRect(
        color = Color(0xFFEAF1E7),
        topLeft = rectTopLeft,
        size = rectSize,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
    )

    val waterPath = Path().apply {
        val waterTop = top + rectSize.height * (0.14f + seededUnit(seed, 0) * 0.18f)
        val ribbonHeight = rectSize.height * (0.18f + seededUnit(seed, 1) * 0.08f)
        moveTo(left, waterTop)
        cubicTo(
            left + rectSize.width * 0.18f,
            waterTop - rectSize.height * (0.1f + seededUnit(seed, 2) * 0.06f),
            left + rectSize.width * 0.42f,
            waterTop + rectSize.height * (0.08f + seededUnit(seed, 3) * 0.08f),
            left + rectSize.width * 0.64f,
            waterTop - rectSize.height * (0.06f + seededUnit(seed, 4) * 0.05f),
        )
        cubicTo(
            left + rectSize.width * 0.8f,
            waterTop - rectSize.height * (0.12f + seededUnit(seed, 5) * 0.05f),
            right,
            waterTop + rectSize.height * (0.02f + seededUnit(seed, 6) * 0.05f),
            right,
            waterTop + ribbonHeight,
        )
        lineTo(right, top)
        lineTo(left, top)
        close()
    }
    drawPath(
        path = waterPath,
        color = Color(0xFFCFE6EE),
    )

    repeat(3) { index ->
        val parkWidth = rectSize.width * (0.18f + seededUnit(seed, 10 + index) * 0.16f)
        val parkHeight = rectSize.height * (0.12f + seededUnit(seed, 20 + index) * 0.12f)
        val parkLeft = left + (rectSize.width - parkWidth) * seededUnit(seed, 30 + index)
        val parkTop = top + (rectSize.height - parkHeight) * seededUnit(seed, 40 + index)
        drawRoundRect(
            color = Color(0xFFD9EACD),
            topLeft = Offset(parkLeft, parkTop),
            size = Size(parkWidth, parkHeight),
            cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
        )
    }

    repeat(4) { index ->
        val x = left + rectSize.width * (0.12f + index * 0.22f + seededUnit(seed, 50 + index) * 0.05f)
        drawLine(
            color = Color(0xFFFFFFFF).copy(alpha = 0.55f),
            start = Offset(x, top),
            end = Offset(x + rectSize.width * 0.08f, bottom),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    repeat(4) { index ->
        val y = top + rectSize.height * (0.16f + index * 0.2f + seededUnit(seed, 60 + index) * 0.05f)
        drawLine(
            color = Color(0xFFFFFFFF).copy(alpha = 0.52f),
            start = Offset(left, y),
            end = Offset(right, y + rectSize.height * 0.04f),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    repeat(2) { index ->
        val roadPath = Path().apply {
            val startX = left - rectSize.width * 0.06f
            val startY = top + rectSize.height * (0.22f + index * 0.34f)
            moveTo(startX, startY)
            cubicTo(
                left + rectSize.width * (0.18f + seededUnit(seed, 70 + index) * 0.08f),
                startY - rectSize.height * (0.12f + seededUnit(seed, 80 + index) * 0.05f),
                left + rectSize.width * (0.58f + seededUnit(seed, 90 + index) * 0.08f),
                startY + rectSize.height * (0.18f + seededUnit(seed, 100 + index) * 0.06f),
                right + rectSize.width * 0.04f,
                startY - rectSize.height * (0.04f + seededUnit(seed, 110 + index) * 0.04f),
            )
        }
        drawPath(
            path = roadPath,
            color = Color(0xFFF7F4EE),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = roadPath,
            color = Color(0xFFD5CDBF),
            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
        )
    }

    val clipPath = Path().apply { addRoundRect(roundRect) }
    drawPath(
        path = clipPath,
        color = Color(0x14000000),
        style = Stroke(width = 1.dp.toPx()),
    )
}

private fun previewSeed(points: List<TrackPoint>): Float {
    if (points.isEmpty()) return 0.37f
    val first = points.first()
    val last = points.last()
    val raw = first.latitude * 0.37 + first.longitude * 0.19 + last.latitude * 0.11 + last.longitude * 0.07 + points.size
    return ((raw % 1.0) + 1.0).rem(1.0).toFloat()
}

private fun seededUnit(seed: Float, index: Int): Float {
    val raw = sin(seed * 12.9898f + index * 78.233f) * 43758.547f
    return raw - floor(raw)
}

private fun project(
    point: TrackPoint,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    minLat: Double,
    minLng: Double,
    latRange: Double,
    lngRange: Double,
): Offset {
    val xRatio = (point.longitude - minLng) / lngRange
    val yRatio = (point.latitude - minLat) / latRange
    val x = left + (width * xRatio).toFloat()
    val y = top + height - (height * yRatio).toFloat()
    return Offset(
        x = min(left + width, max(left, x)),
        y = min(top + height, max(top, y)),
    )
}
