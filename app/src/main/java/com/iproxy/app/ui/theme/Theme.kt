package com.iproxy.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.iproxy.app.data.model.ThemeMode

val CyanAccent = Color(0xFF00D2D3)
val BrandBlue = Color(0xFF0A84FF)
val BrandDeepNavy = Color(0xFF0B132B)
val BrandCardDark = Color(0xFF152039)
val ConnectedGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF002022),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF9AF6F7),
    secondary = BrandBlue,
    onSecondary = Color.White,
    background = BrandDeepNavy,
    onBackground = Color(0xFFEAF0FA),
    surface = BrandCardDark,
    onSurface = Color(0xFFEAF0FA),
    surfaceVariant = Color(0xFF1E2C4A),
    onSurfaceVariant = Color(0xFF9FB2CE),
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006D77),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F4F6),
    onPrimaryContainer = Color(0xFF002023),
    secondary = BrandBlue,
    onSecondary = Color.White,
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EFF8),
    onSurfaceVariant = Color(0xFF475569),
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

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
