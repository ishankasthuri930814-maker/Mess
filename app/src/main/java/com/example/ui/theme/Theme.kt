package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = PoliceNavyDark,
        primaryContainer = PoliceSlate,
        onPrimaryContainer = PoliceBlueLight,
        secondary = DarkSecondary,
        onSecondary = PoliceNavyDark,
        secondaryContainer = Color(0xFF1E3A5F),
        onSecondaryContainer = Color(0xFFE0F2FE),
        tertiary = DarkTertiary,
        onTertiary = PoliceNavyDark,
        tertiaryContainer = Color(0xFF422006),
        onTertiaryContainer = BadgeGoldLight,
        background = DarkBackground,
        onBackground = DarkOnSurface,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        outline = Color(0xFF475569),
        outlineVariant = Color(0xFF334155),
        error = Color(0xFFF87171),
        onError = PoliceNavyDark,
        errorContainer = Color(0xFF7F1D1D),
        onErrorContainer = Color(0xFFFEE2E2)
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PoliceNavyPrimary,
        onPrimary = Color.White,
        primaryContainer = PoliceBlueLight,
        onPrimaryContainer = PoliceNavyPrimary,
        secondary = PoliceSlate,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2E8F0),
        onSecondaryContainer = PoliceNavyPrimary,
        tertiary = BadgeGold,
        onTertiary = Color.White,
        tertiaryContainer = BadgeGoldLight,
        onTertiaryContainer = BadgeGoldDark,
        background = LightBackground,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        outline = Color(0xFF94A3B8),
        outlineVariant = Color(0xFFCBD5E1),
        error = Color(0xFFDC2626),
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D)
    )

val ColorScheme.isDark: Boolean
    get() = surface.luminance() < 0.5f

object PoliceThemeColors {
    val navyText: Color
        @Composable get() = if (MaterialTheme.colorScheme.isDark) Color(0xFF93C5FD) else PoliceNavyPrimary

    val greenText: Color
        @Composable get() = if (MaterialTheme.colorScheme.isDark) DutyActiveGreenBright else DutyActiveGreen

    val orangeText: Color
        @Composable get() = if (MaterialTheme.colorScheme.isDark) DutyOffOrangeBright else DutyOffOrange

    val goldText: Color
        @Composable get() = if (MaterialTheme.colorScheme.isDark) Color(0xFFFCD34D) else BadgeGoldDark

    val blueText: Color
        @Composable get() = if (MaterialTheme.colorScheme.isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)

    val errorText: Color
        @Composable get() = if (MaterialTheme.colorScheme.isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626)

    val mutedText: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep distinctive Police Navy identity
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val currentDensity = LocalDensity.current
    val compactFontDensity = Density(
        density = currentDensity.density,
        fontScale = (currentDensity.fontScale * 0.56f).coerceIn(0.50f, 0.85f)
    )

    CompositionLocalProvider(LocalDensity provides compactFontDensity) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
