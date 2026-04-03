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

private val StoneBackground = Color(0xFFD8D4F6)
private val StoneBackgroundDark = Color(0xFF0A1028)
private val IvorySurface = Color(0xFFF4F1FF)
private val SlateSurface = Color(0xFF141B3A)
private val MistSurface = Color(0xFFE6E6FF)
private val MistSurfaceDark = Color(0xFF1F2954)
private val LakePrimary = Color(0xFF5A66FF)
private val LakePrimaryDark = Color(0xFFB8C2FF)
private val LakePrimaryContainer = Color(0xFFDCE0FF)
private val LakePrimaryContainerDark = Color(0xFF3240A5)
private val ForestSecondary = Color(0xFF7A70E6)
private val ForestSecondaryDark = Color(0xFFD1C8FF)
private val ForestSecondaryContainer = Color(0xFFEAE4FF)
private val ForestSecondaryContainerDark = Color(0xFF473E86)
private val AmberAccent = Color(0xFFFF8AA8)
private val AmberAccentDark = Color(0xFFFFB5C9)
private val Ink = Color(0xFF1F2247)
private val InkDark = Color(0xFFF4F3FF)
private val MutedInk = Color(0xFF6C7099)
private val MutedInkDark = Color(0xFFC2C6F4)
private val Outline = Color(0xFFFCFBFF)
private val OutlineDark = Color(0xFF5A6497)
private val SuccessColor = Color(0xFF52D5B4)
private val WarningColor = Color(0xFFFFB86B)
private val ErrorColor = Color(0xFFFF8B9F)

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
    val PageBackgroundLight: Color = Color(0xFFD9D5F8)
    val PageBackgroundDark: Color = Color(0xFF0A1028)
    val SoftSurfaceLight: Color = Color(0xA7E8E6FF)
    val SoftSurfaceDark: Color = Color(0x8C1B234B)
    val SoftAccentLight: Color = Color(0x99D8D1FF)
    val SoftAccentDark: Color = Color(0xA6323C7E)
    val SecondarySurfaceLight: Color = Color(0x99D7D8FF)
    val SecondarySurfaceDark: Color = Color(0x8A222D5A)
    val SoftOutlineLight: Color = Color(0x73F4F3FF)
    val SoftOutlineDark: Color = Color(0x66E7E9FF)
    val GlassSurfaceLight: Color = Color(0x96DFE1FF)
    val GlassSurfaceDark: Color = Color(0x8C171E43)
    val GlassStrongSurfaceLight: Color = Color(0xB6EEF0FF)
    val GlassStrongSurfaceDark: Color = Color(0xB31B224B)
    val GlassBorderLight: Color = Color(0x8AEFF0FF)
    val GlassBorderDark: Color = Color(0x80F1F0FF)
    val InnerPanelSurfaceLight: Color = Color(0x8BCED3FF)
    val InnerPanelSurfaceDark: Color = Color(0xAA232F66)
    val InnerPanelBorderLight: Color = Color(0x66EEF1FF)
    val InnerPanelBorderDark: Color = Color(0x66C6CCFF)
    val GlowPrimaryLight: Color = Color(0xFF6786FF)
    val GlowPrimaryDark: Color = Color(0xFF8D9CFF)
    val GlowSecondaryLight: Color = Color(0xFFFF92BC)
    val GlowSecondaryDark: Color = Color(0xFFFFA7C8)
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

val ColorScheme.trackGlassSurface: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.GlassSurfaceLight
    } else {
        TrackRecordStyle.GlassSurfaceDark
    }

val ColorScheme.trackGlassStrongSurface: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.GlassStrongSurfaceLight
    } else {
        TrackRecordStyle.GlassStrongSurfaceDark
    }

val ColorScheme.trackGlassBorder: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.GlassBorderLight
    } else {
        TrackRecordStyle.GlassBorderDark
    }

val ColorScheme.trackInnerPanelSurface: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.InnerPanelSurfaceLight
    } else {
        TrackRecordStyle.InnerPanelSurfaceDark
    }

val ColorScheme.trackInnerPanelBorder: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.InnerPanelBorderLight
    } else {
        TrackRecordStyle.InnerPanelBorderDark
    }

val ColorScheme.trackGlowPrimary: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.GlowPrimaryLight
    } else {
        TrackRecordStyle.GlowPrimaryDark
    }

val ColorScheme.trackGlowSecondary: Color
    get() = if (isLightTrackPalette()) {
        TrackRecordStyle.GlowSecondaryLight
    } else {
        TrackRecordStyle.GlowSecondaryDark
    }

private fun ColorScheme.isLightTrackPalette(): Boolean = background == StoneBackground
