package com.wenhao.record.ui.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 带有呼吸光效的进度圆环
 */
@Composable
fun TrackGlowRing(
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    glowColor: Color = ringColor.copy(alpha = 0.4f)
) {
    // 呼吸动效
    val infiniteTransition = rememberInfiniteTransition(label = "GlowPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 8.dp.toPx()
        val glowWidth = 12.dp.toPx()

        // 1. 绘制底色环 (半透明)
        drawArc(
            color = ringColor.copy(alpha = 0.1f),
            startAngle = -225f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 2. 绘制发光层 (随着 progress 和呼吸动效变化)
        drawArc(
            color = glowColor.copy(alpha = glowAlpha),
            startAngle = -225f,
            sweepAngle = 270f * progress,
            useCenter = false,
            style = Stroke(width = glowWidth, cap = StrokeCap.Round)
        )

        // 3. 绘制主进度环
        drawArc(
            color = ringColor,
            startAngle = -225f,
            sweepAngle = 270f * progress,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
