package com.wenhao.record.ui.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 浅色毛玻璃材质背景修饰符
 */
fun Modifier.glassBackground(
    blur: Dp = 25.dp,
    color: Color = Color.White.copy(alpha = 0.6f),
    borderColor: Color = Color.White.copy(alpha = 0.8f),
    shape: Shape = RoundedCornerShape(24.dp)
): Modifier = this.then(
    Modifier
        .graphicsLayer {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                renderEffect = android.graphics.RenderEffect
                    .createBlurEffect(
                        blur.toPx(),
                        blur.toPx(),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    .asComposeRenderEffect()
            }
            clip = true
            this.shape = shape
        }
        .background(color)
        .border(1.dp, borderColor, shape)
)

/**
 * 玻璃卡片容器
 */
@Composable
fun TrackGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.glassBackground(shape = shape),
        color = Color.Transparent,
        shadowElevation = 8.dp,
        content = content
    )
}
