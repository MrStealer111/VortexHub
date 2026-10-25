package com.vpnmobile.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2A14),
    secondary = Cyan,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Purple,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = SurfaceGlass,
    error = ErrorRed,
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0xFF2C2C2E),
    scrim = Color(0x990A0A0A),
)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    secondary = Cyan,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Purple,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    error = ErrorRed,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

data class AppPalette(
    val bg: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val text: Color,
    val textSecondary: Color,
    val border: Color,
    val accent: Color,
    val success: Color,
    val error: Color,
)

val DarkPalette = AppPalette(
    bg = Color(0xFF0A0A0A),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    border = Color(0xFF2C2C2E),
    accent = Color(0xFFFF8C00),
    success = Color(0xFF00C853),
    error = Color(0xFFFF5252),
)

val LightPalette = AppPalette(
    bg = Color(0xFFF4F4F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9E9EF),
    text = Color(0xFF121216),
    textSecondary = Color(0xFF6C6C72),
    border = Color(0xFFDADAE2),
    accent = Color(0xFFE07800),
    success = Color(0xFF0A9D4A),
    error = Color(0xFFD93636),
)

val LocalAppPalette = staticCompositionLocalOf { DarkPalette }

object AppTheme {
    val colors: AppPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppPalette.current
}

@Composable
fun VpnMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppPalette provides if (darkTheme) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
