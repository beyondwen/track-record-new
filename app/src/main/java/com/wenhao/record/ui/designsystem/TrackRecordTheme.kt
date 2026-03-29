package com.wenhao.record.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StoneBackground = Color(0xFFF4F1EB)
private val StoneBackgroundDark = Color(0xFF111A1D)
private val IvorySurface = Color(0xFFFFFCF7)
private val SlateSurface = Color(0xFF182327)
private val MistSurface = Color(0xFFE9F0EE)
private val MistSurfaceDark = Color(0xFF213136)
private val LakePrimary = Color(0xFF155E75)
private val LakePrimaryDark = Color(0xFF7FD0E3)
private val LakePrimaryContainer = Color(0xFFD7EEF3)
private val LakePrimaryContainerDark = Color(0xFF0F4D5E)
private val ForestSecondary = Color(0xFF4A6364)
private val ForestSecondaryDark = Color(0xFFB8CCCA)
private val ForestSecondaryContainer = Color(0xFFDCE8E6)
private val ForestSecondaryContainerDark = Color(0xFF334849)
private val AmberAccent = Color(0xFFC88719)
private val AmberAccentDark = Color(0xFFF0C06E)
private val Ink = Color(0xFF172026)
private val InkDark = Color(0xFFF2F7F6)
private val MutedInk = Color(0xFF66727A)
private val MutedInkDark = Color(0xFFB9C4C8)
private val Outline = Color(0xFFD3DDDA)
private val OutlineDark = Color(0xFF405156)
private val SuccessColor = Color(0xFF1E8F62)
private val WarningColor = Color(0xFFC88719)
private val ErrorColor = Color(0xFFB65050)

private val TrackRecordLightColors: ColorScheme = lightColorScheme(
    primary = LakePrimary,
    onPrimary = Color.White,
    primaryContainer = LakePrimaryContainer,
    onPrimaryContainer = Color(0xFF062C37),
    secondary = ForestSecondary,
    onSecondary = Color.White,
    secondaryContainer = ForestSecondaryContainer,
    onSecondaryContainer = Color(0xFF142728),
    tertiary = AmberAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8E0B7),
    onTertiaryContainer = Color(0xFF452B00),
    background = StoneBackground,
    onBackground = Ink,
    surface = IvorySurface,
    onSurface = Ink,
    surfaceVariant = MistSurface,
    onSurfaceVariant = MutedInk,
    outline = Outline,
    outlineVariant = Outline,
    error = ErrorColor,
    onError = Color.White,
    errorContainer = Color(0xFFF7DCDC),
    onErrorContainer = Color(0xFF4A1212),
)

private val TrackRecordDarkColors: ColorScheme = darkColorScheme(
    primary = LakePrimaryDark,
    onPrimary = Color(0xFF003544),
    primaryContainer = LakePrimaryContainerDark,
    onPrimaryContainer = Color(0xFFD7EEF3),
    secondary = ForestSecondaryDark,
    onSecondary = Color(0xFF1C3435),
    secondaryContainer = ForestSecondaryContainerDark,
    onSecondaryContainer = Color(0xFFDCE8E6),
    tertiary = AmberAccentDark,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFF8E0B7),
    background = StoneBackgroundDark,
    onBackground = InkDark,
    surface = SlateSurface,
    onSurface = InkDark,
    surfaceVariant = MistSurfaceDark,
    onSurfaceVariant = MutedInkDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D1D),
    onErrorContainer = Color(0xFFFFDAD6),
)

val TrackRecordSpacing = TrackRecordSpacingTokens()

class TrackRecordSpacingTokens(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 40.dp,
)

private val TrackRecordTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 56.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 46.sp,
        lineHeight = 48.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)

private val TrackRecordShapes = Shapes(
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
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

object TrackRecordStyle {
    val PageBackgroundLight: Color = Color(0xFFF6F7F8)
    val PageBackgroundDark: Color = Color(0xFF0F171A)
    val SoftSurfaceLight: Color = Color(0xFFF3F6F8)
    val SoftSurfaceDark: Color = Color(0xFF1B272C)
    val SoftAccentLight: Color = Color(0xFFE7F4F3)
    val SoftAccentDark: Color = Color(0xFF16363A)
    val SecondarySurfaceLight: Color = Color(0xFFE4EAF0)
    val SecondarySurfaceDark: Color = Color(0xFF26353B)
    val SoftOutlineLight: Color = Color(0xFFE6EBF0)
    val SoftOutlineDark: Color = Color(0xFF314247)
}

val ColorScheme.trackPageBackground: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.PageBackgroundLight
    } else {
        TrackRecordStyle.PageBackgroundDark
    }

val ColorScheme.trackSoftSurface: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.SoftSurfaceLight
    } else {
        TrackRecordStyle.SoftSurfaceDark
    }

val ColorScheme.trackSecondarySurface: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.SecondarySurfaceLight
    } else {
        TrackRecordStyle.SecondarySurfaceDark
    }

val ColorScheme.trackSoftAccent: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.SoftAccentLight
    } else {
        TrackRecordStyle.SoftAccentDark
    }

val ColorScheme.trackSoftOutline: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.SoftOutlineLight
    } else {
        TrackRecordStyle.SoftOutlineDark
    }

private fun ColorScheme.isLightTrackPalette(): Boolean = background == StoneBackground
