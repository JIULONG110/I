package com.iproxy.app.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val CyanAccent = Color(0xFF00D2D3)
val BrandBlue = Color(0xFF0A84FF)
val BrandDeepNavy = Color(0xFF050A14)
val BrandCardDark = Color(0xFF0E1729)
val ConnectedGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFF87171)

@Immutable
data class LiquidThemePalette(
    val backdropColors: List<Color>,
    val ambientOrbPrimary: Color,
    val ambientOrbSecondary: Color,
    val glassStart: Color,
    val glassEnd: Color,
    val glassElevatedStart: Color,
    val glassElevatedEnd: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val dockSelectedPill: Color,
    val obsidianCoreTop: Color,
    val obsidianCoreBottom: Color
)

private val DarkLiquidPalette = LiquidThemePalette(
    backdropColors = listOf(
        Color(0xFF030812),
        Color(0xFF071122),
        Color(0xFF0A182F)
    ),
    ambientOrbPrimary = Color(0xFF0C6E8C),
    ambientOrbSecondary = Color(0xFF1B4FD8),
    glassStart = Color(0xB8111D32),
    glassEnd = Color(0x9E0A1324),
    glassElevatedStart = Color(0xD6162640),
    glassElevatedEnd = Color(0xBA0D182C),
    glassBorderTop = Color(0x4A5BA8F7),
    glassBorderBottom = Color(0x1C1E3A5F),
    dockSelectedPill = Color(0x422898EB),
    obsidianCoreTop = Color(0xFF162338),
    obsidianCoreBottom = Color(0xFF060D1A)
)

val LocalLiquidPalette = staticCompositionLocalOf { DarkLiquidPalette }

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF031B20),
    primaryContainer = Color(0xFF0B3A53),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = BrandBlue,
    onSecondary = Color(0xFFF8FAFC),
    secondaryContainer = Color(0xFF102A4C),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF041C2C),
    tertiaryContainer = Color(0xFF0E345A),
    onTertiaryContainer = Color(0xFFBAE6FD),
    background = BrandDeepNavy,
    onBackground = Color(0xFFEAF2FF),
    surface = BrandCardDark,
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0xFF152238),
    onSurfaceVariant = Color(0xFF9FB8D9),
    surfaceDim = Color(0xFF050A14),
    surfaceBright = Color(0xFF1A2942),
    surfaceContainerLowest = Color(0xFF040811),
    surfaceContainerLow = Color(0xFF0A1220),
    surfaceContainer = Color(0xFF101C31),
    surfaceContainerHigh = Color(0xFF13223A),
    surfaceContainerHighest = Color(0xFF1A2C4A),
    outline = Color(0xFF2D456B),
    outlineVariant = Color(0xFF1B2E4B),
    inverseSurface = Color(0xFF142642),
    inverseOnSurface = Color(0xFFEAF2FF),
    inversePrimary = Color(0xFF38BDF8),
    surfaceTint = Color(0xFF38BDF8),
    error = ErrorRed,
    onError = Color(0xFF2A060B),
    errorContainer = Color(0xFF4C111B),
    onErrorContainer = Color(0xFFFECACA),
    scrim = Color(0xCC02060D)
)

/**
 * 全局固定深海军蓝暗黑主题：不随系统浅色切换，不提供日间模式
 */
@Composable
fun IProxyTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    SideEffect {
        val activity = context as? ComponentActivity ?: return@SideEffect
        val barStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
    }

    CompositionLocalProvider(
        LocalLiquidPalette provides DarkLiquidPalette,
        LocalContentColor provides DarkColorScheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            content = content
        )
    }
}
