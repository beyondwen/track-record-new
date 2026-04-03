package com.wenhao.record.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

@Composable
fun TrackAtmosphericBackground(
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colorScheme.trackPageBackground,
                    colorScheme.trackSecondarySurface.copy(alpha = 0.94f),
                    colorScheme.trackPageBackground.copy(alpha = 0.96f),
                ),
            ),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorScheme.trackGlowPrimary.copy(alpha = 0.48f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.64f, size.height * 0.18f),
                radius = size.minDimension * 0.62f,
            ),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 0.64f, size.height * 0.18f),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorScheme.trackGlowSecondary.copy(alpha = 0.36f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.28f, size.height * 0.76f),
                radius = size.minDimension * 0.56f,
            ),
            radius = size.minDimension * 0.56f,
            center = Offset(size.width * 0.28f, size.height * 0.76f),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.2f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.78f, size.height * 0.3f),
                radius = size.minDimension * 0.22f,
            ),
            radius = size.minDimension * 0.22f,
            center = Offset(size.width * 0.78f, size.height * 0.3f),
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                start = Offset(size.width * 0.08f, 0f),
                end = Offset(size.width * 0.92f, size.height * 0.82f),
            ),
        )
    }
}
