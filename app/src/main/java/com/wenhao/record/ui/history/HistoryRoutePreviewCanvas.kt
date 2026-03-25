package com.wenhao.record.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.wenhao.record.data.tracking.TrackPoint
import kotlin.math.max
import kotlin.math.min

@Composable
fun HistoryRoutePreviewCanvas(
    segments: List<List<TrackPoint>>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val inset = 6.dp.toPx()
        val rectTopLeft = Offset(inset, inset)
        val rectSize = Size(size.width - inset * 2, size.height - inset * 2)
        val centerY = rectTopLeft.y + rectSize.height / 2f
        val points = segments.flatten()
        val guideColor = Color(0x14A78BFA)
        val lineColor = Color(0xFF8B5CF6)
        val startColor = Color(0xFF35C77D)
        val endColor = Color(0xFFFF6B6B)

        drawRoundRect(
            color = guideColor,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
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

        segments.forEach { segment ->
            if (segment.isEmpty()) return@forEach

            if (segment.size == 1) {
                val projected = project(
                    point = segment.first(),
                    left = rectTopLeft.x,
                    top = rectTopLeft.y,
                    width = rectSize.width,
                    height = rectSize.height,
                    minLat = minLat,
                    minLng = minLng,
                    latRange = latRange,
                    lngRange = lngRange,
                )
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = projected,
                )
                return@forEach
            }

            val path = Path()
            segment.forEachIndexed { index, point ->
                val projected = project(
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
                if (index == 0) {
                    path.moveTo(projected.x, projected.y)
                } else {
                    path.lineTo(projected.x, projected.y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 3.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        val start = segments.firstOrNull { it.isNotEmpty() }?.first() ?: points.first()
        val end = segments.lastOrNull { it.isNotEmpty() }?.last() ?: points.last()
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
