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

    private val routePath = Path()
    private var points: List<TrackPoint> = emptyList()

    fun setPoints(points: List<TrackPoint>) {
        this.points = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentLeft = paddingLeft.toFloat() + dp(6f)
        val contentTop = paddingTop.toFloat() + dp(6f)
        val contentRight = width - paddingRight.toFloat() - dp(6f)
        val contentBottom = height - paddingBottom.toFloat() - dp(6f)
        val rect = RectF(contentLeft, contentTop, contentRight, contentBottom)
        val centerY = rect.centerY()

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

        routePath.reset()

        points.forEachIndexed { index, point ->
            val xRatio = (point.longitude - minLng) / lngRange
            val yRatio = (point.latitude - minLat) / latRange
            val x = rect.left + (rect.width() * xRatio).toFloat()
            val y = rect.bottom - (rect.height() * yRatio).toFloat()
            val clampedX = min(rect.right, max(rect.left, x))
            val clampedY = min(rect.bottom, max(rect.top, y))

            if (index == 0) {
                routePath.moveTo(clampedX, clampedY)
            } else {
                routePath.lineTo(clampedX, clampedY)
            }
        }

        canvas.drawPath(routePath, linePaint)

        val start = points.first()
        val end = points.last()
        val startX = rect.left + (rect.width() * ((start.longitude - minLng) / lngRange)).toFloat()
        val startY = rect.bottom - (rect.height() * ((start.latitude - minLat) / latRange)).toFloat()
        val endX = rect.left + (rect.width() * ((end.longitude - minLng) / lngRange)).toFloat()
        val endY = rect.bottom - (rect.height() * ((end.latitude - minLat) / latRange)).toFloat()

        canvas.drawCircle(startX, startY, dp(4f), startPaint)
        canvas.drawCircle(endX, endY, dp(4f), endPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
