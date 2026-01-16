package com.liftley.vodrop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Material 3 Expressive - Vibrant, dynamic colors
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9D7BF7),          // Brighter purple
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5B3EC7),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF7C8BF5),         // Softer indigo
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3C4AC7),
    onSecondaryContainer = Color(0xFFDEE0FF),
    tertiary = Color(0xFFFF8FAB),          // Expressive pink accent
    onTertiary = Color.White,
    background = Color(0xFF000000),        // Deeper black with hint of purple
    onBackground = Color(0xFFF0F0F5),
    surface = Color(0xFF1C1C22),           // Elevated surface
    onSurface = Color(0xFFF0F0F5),
    surfaceVariant = Color(0xFF252530),    // Card surfaces
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerHighest = Color(0xFF2C2C36),
    outline = Color(0xFF48484F),
    outlineVariant = Color(0xFF38383F),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF8B0000),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7C4DFF),           // Vibrant purple
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF6366F1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E1FF),
    onSecondaryContainer = Color(0xFF1A1B4B),
    tertiary = Color(0xFFFF4081),           // Expressive pink accent
    onTertiary = Color.White,
    background = Color(0xFFFAF9FC),         // Subtle cool tone
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F3F7),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerHighest = Color(0xFFECEAEE),
    outline = Color(0xFFDDD9E1),
    outlineVariant = Color(0xFFE8E4EC),
    error = Color(0xFFE53935),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// Material 3 Expressive - Rounder, more varied shapes
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),     // Bigger than default
    large = RoundedCornerShape(28.dp),      // Very rounded
    extraLarge = RoundedCornerShape(36.dp)  // Expressive large elements
)

// Material 3 Expressive - Bolder typography
private val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun VoDropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content
    )
}