package com.wenhao.record.ui.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.wenhao.record.data.tracking.TrackPoint
import kotlin.math.max
import kotlin.math.min

class HistoryRoutePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8B5CF6.toInt()
        strokeWidth = dp(3.2f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF35C77D.toInt()
        style = Paint.Style.FILL
    }

    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF6B6B.toInt()
        style = Paint.Style.FILL
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14A78BFA
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val contentRect = RectF()
    private val routePath = Path()
    private var segments: List<List<TrackPoint>> = emptyList()

    fun setPoints(points: List<TrackPoint>) {
        setSegments(if (points.isEmpty()) emptyList() else listOf(points))
    }

    fun setSegments(segments: List<List<TrackPoint>>) {
        this.segments = segments.map { segment -> segment.toList() }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentLeft = paddingLeft.toFloat() + dp(6f)
        val contentTop = paddingTop.toFloat() + dp(6f)
        val contentRight = width - paddingRight.toFloat() - dp(6f)
        val contentBottom = height - paddingBottom.toFloat() - dp(6f)
        val rect = contentRect.apply {
            set(contentLeft, contentTop, contentRight, contentBottom)
        }
        val centerY = rect.centerY()
        val points = segments.flatten()

        canvas.drawRoundRect(rect, dp(16f), dp(16f), guidePaint)

        if (points.isEmpty()) {
            canvas.drawLine(rect.left + dp(10f), centerY, rect.right - dp(10f), centerY, guidePaint)
            return
        }

        if (points.size == 1) {
            canvas.drawCircle(rect.centerX(), centerY, dp(5f), startPaint)
            return
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
                val projected = project(segment.first(), rect, minLat, minLng, latRange, lngRange)
                canvas.drawCircle(projected.first, projected.second, dp(3f), linePaint)
                return@forEach
            }

            routePath.reset()
            segment.forEachIndexed { index, point ->
                val projected = project(point, rect, minLat, minLng, latRange, lngRange)
                if (index == 0) {
                    routePath.moveTo(projected.first, projected.second)
                } else {
                    routePath.lineTo(projected.first, projected.second)
                }
            }
            canvas.drawPath(routePath, linePaint)
        }

        val start = segments.firstOrNull { it.isNotEmpty() }?.first() ?: points.first()
        val end = segments.lastOrNull { it.isNotEmpty() }?.last() ?: points.last()
        val startProjected = project(start, rect, minLat, minLng, latRange, lngRange)
        val endProjected = project(end, rect, minLat, minLng, latRange, lngRange)

        canvas.drawCircle(startProjected.first, startProjected.second, dp(4f), startPaint)
        canvas.drawCircle(endProjected.first, endProjected.second, dp(4f), endPaint)
    }

    private fun project(
        point: TrackPoint,
        rect: RectF,
        minLat: Double,
        minLng: Double,
        latRange: Double,
        lngRange: Double
    ): Pair<Float, Float> {
        val xRatio = (point.longitude - minLng) / lngRange
        val yRatio = (point.latitude - minLat) / latRange
        val x = rect.left + (rect.width() * xRatio).toFloat()
        val y = rect.bottom - (rect.height() * yRatio).toFloat()
        return min(rect.right, max(rect.left, x)) to min(rect.bottom, max(rect.top, y))
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
