package com.wenhao.record.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WarmPaper = Color(0xFFF6F1EB)
private val WarmPaperDark = Color(0xFF171318)
private val Violet = Color(0xFF7B5CFA)
private val VioletContainer = Color(0xFFEDE7FF)
private val Ink = Color(0xFF1E1A21)
private val MutedInk = Color(0xFF6F6878)
private val Outline = Color(0xFFDCD2E6)
private val SurfaceCard = Color(0xFFFFFFFF)
private val SurfaceMuted = Color(0xFFF4EFF8)
private val SuccessColor = Color(0xFF2FA56B)
private val WarningColor = Color(0xFFC88A1D)
private val ErrorColor = Color(0xFFCC5A5A)

private val TrackRecordLightColors: ColorScheme = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletContainer,
    onPrimaryContainer = Color(0xFF251542),
    secondary = Color(0xFF8E7FA6),
    onSecondary = Color.White,
    secondaryContainer = SurfaceMuted,
    onSecondaryContainer = Ink,
    background = WarmPaper,
    onBackground = Ink,
    surface = SurfaceCard,
    onSurface = Ink,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = MutedInk,
    outline = Outline,
    outlineVariant = Outline,
    error = ErrorColor,
    onError = Color.White,
    errorContainer = Color(0xFFFDE2E2),
    onErrorContainer = Color(0xFF4C1111),
)

private val TrackRecordDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFB8A6FF),
    onPrimary = Color(0xFF2F185F),
    primaryContainer = Color(0xFF422A79),
    onPrimaryContainer = Color(0xFFE8DFFF),
    secondary = Color(0xFFCDBFE2),
    onSecondary = Color(0xFF352B45),
    secondaryContainer = Color(0xFF4A3F5B),
    onSecondaryContainer = Color(0xFFF0E8FB),
    background = WarmPaperDark,
    onBackground = Color(0xFFF3EDF7),
    surface = Color(0xFF221C24),
    onSurface = Color(0xFFF3EDF7),
    surfaceVariant = Color(0xFF312A36),
    onSurfaceVariant = Color(0xFFD3CAD8),
    outline = Color(0xFF584F5F),
    outlineVariant = Color(0xFF453D4C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val TrackRecordSpacing = TrackRecordSpacingTokens()

class TrackRecordSpacingTokens(
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val sm: androidx.compose.ui.unit.Dp = 8.dp,
    val md: androidx.compose.ui.unit.Dp = 12.dp,
    val lg: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 24.dp,
    val xxl: androidx.compose.ui.unit.Dp = 32.dp,
)

private val TrackRecordTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

private val TrackRecordShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun TrackRecordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) TrackRecordDarkColors else TrackRecordLightColors,
        typography = TrackRecordTypography,
        shapes = TrackRecordShapes,
        content = content,
    )
}

object TrackRecordStatusColors {
    val Success: Color = SuccessColor
    val Warning: Color = WarningColor
    val Error: Color = ErrorColor
}
