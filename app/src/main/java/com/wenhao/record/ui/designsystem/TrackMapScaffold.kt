package com.wenhao.record.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape

@Composable
fun TrackFloatingMapButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    accented: Boolean = false,
    modifier: Modifier = Modifier,
) {
    TrackLiquidPanel(
        modifier = modifier.size(52.dp),
        shape = RoundedCornerShape(18.dp),
        tone = if (accented) TrackLiquidTone.ACCENT else TrackLiquidTone.STRONG,
        shadowElevation = 10.dp,
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(18.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Transparent,
                contentColor = if (accented) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ),
        ) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
fun TrackTopOverlayCard(
    title: String,
    body: String? = null,
    eyebrow: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    TrackLiquidPanel(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        tone = TrackLiquidTone.STANDARD,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor),
                )
                eyebrow?.takeIf { it.isNotBlank() }?.let {
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun TrackBottomSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    TrackLiquidPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 26.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 24.dp,
            top = 14.dp,
            end = 24.dp,
            bottom = 14.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.Start,
            content = content,
        )
    }
}

@Composable
fun TrackBottomHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 42.dp, height = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(999.dp),
            ),
    )
}

@Composable
fun TrackMapBottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.06f),
                        Color.Black.copy(alpha = 0.16f),
                        Color.Black.copy(alpha = 0.24f),
                    ),
                ),
            ),
    )
}

@Composable
fun TrackMapCenterIndicator(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrackLiquidPanel(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            tone = TrackLiquidTone.STRONG,
            shadowElevation = 12.dp,
        ) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(modifier = Modifier.size(4.dp))
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.66f),
                            MaterialTheme.colorScheme.trackGlowSecondary.copy(alpha = 0.42f),
                        ),
                    ),
                ),
        )
        Spacer(modifier = Modifier.size(5.dp))
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.14f)),
        )
    }
}

@Composable
fun TrackTopOverlayColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/*
@Preview(showBackground = true)
@Composable
private fun TrackTopOverlayCardPreview() {
    TrackRecordTheme {
        TrackTopOverlayCard(
            title = "GPS 已就绪",
            body = "后台待命中 · 最近一次定位精度良好",
            eyebrow = "实时状态",
            modifier = Modifier.padding(16.dp),
        )
    }
}
*/

@Preview(showBackground = true)
@Composable
private fun TrackTopOverlayCardPreview() {
    TrackRecordTheme {
        TrackTopOverlayCard(
            title = "GPS ready",
            body = "Background tracking is standing by and the latest location fix is stable.",
            eyebrow = "Live status",
            modifier = Modifier.padding(16.dp),
        )
    }
}
