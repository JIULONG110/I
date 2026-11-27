package com.iproxy.app.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.iproxy.app.data.model.ThemeMode

val CyanAccent = Color(0xFF00D2D3)
val BrandBlue = Color(0xFF0A84FF)
val BrandDeepNavy = Color(0xFF05070C)
val BrandCardDark = Color(0xFF101624)
val ConnectedGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)

@Immutable
data class LiquidThemePalette(
    val isDark: Boolean,
    val backdropColors: List<Color>,
    val ambientOrbPrimary: Color,
    val ambientOrbSecondary: Color,
    val glassStart: Color,
    val glassEnd: Color,
    val glassElevatedStart: Color,
    val glassElevatedEnd: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val dockStart: Color,
    val dockEnd: Color,
    val dockSelectedPill: Color,
    val obsidianCoreTop: Color,
    val obsidianCoreBottom: Color
)

private val DarkLiquidPalette = LiquidThemePalette(
    isDark = true,
    backdropColors = listOf(
        Color(0xFF030508),
        Color(0xFF080D18),
        Color(0xFF0B1323)
    ),
    ambientOrbPrimary = Color(0xFF0E7490),
    ambientOrbSecondary = Color(0xFF1D4ED8),
    glassStart = Color(0xAA172033),
    glassEnd = Color(0x880D1320),
    glassElevatedStart = Color(0xCC1E293E),
    glassElevatedEnd = Color(0xAA111827),
    glassBorderTop = Color(0x42FFFFFF),
    glassBorderBottom = Color(0x1494A3B8),
    dockStart = Color(0xCC131A29),
    dockEnd = Color(0xBB090D16),
    dockSelectedPill = Color(0x3800D2D3),
    obsidianCoreTop = Color(0xFF1E2532),
    obsidianCoreBottom = Color(0xFF07090E)
)

private val LightLiquidPalette = LiquidThemePalette(
    isDark = false,
    backdropColors = listOf(
        Color(0xFFEAF1FB),
        Color(0xFFF6F9FE),
        Color(0xFFE3EEF9)
    ),
    ambientOrbPrimary = Color(0xFF38BDF8),
    ambientOrbSecondary = Color(0xFF6366F1),
    glassStart = Color(0xCCFFFFFF),
    glassEnd = Color(0xA6F1F6FD),
    glassElevatedStart = Color(0xE6FFFFFF),
    glassElevatedEnd = Color(0xCCF4F8FF),
    glassBorderTop = Color(0xF0FFFFFF),
    glassBorderBottom = Color(0x240F172A),
    dockStart = Color(0xDEFFFFFF),
    dockEnd = Color(0xCCF1F5FB),
    dockSelectedPill = Color(0x24006D77),
    obsidianCoreTop = Color(0xFF232A36),
    obsidianCoreBottom = Color(0xFF090B10)
)

val LocalLiquidPalette = staticCompositionLocalOf { DarkLiquidPalette }

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF002022),
    primaryContainer = Color(0xFF093B42),
    onPrimaryContainer = Color(0xFF9AF6F7),
    secondary = BrandBlue,
    onSecondary = Color.White,
    background = BrandDeepNavy,
    onBackground = Color(0xFFF1F5F9),
    surface = BrandCardDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF192235),
    onSurfaceVariant = Color(0xFF9FB2CE),
    outline = Color(0xFF334155),
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006D77),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F4F6),
    onPrimaryContainer = Color(0xFF002023),
    secondary = BrandBlue,
    onSecondary = Color.White,
    background = Color(0xFFEEF4FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE5EEF8),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = ErrorRed
)

@Composable
fun IProxyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    SideEffect {
        val activity = context as? ComponentActivity ?: return@SideEffect
        val barStyle = if (isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        }
        activity.enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val liquidPalette = if (isDark) DarkLiquidPalette else LightLiquidPalette

    CompositionLocalProvider(LocalLiquidPalette provides liquidPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
