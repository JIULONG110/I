package com.iproxy.app.ui.theme

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
import com.iproxy.app.data.model.ThemeMode

val CyanAccent = Color(0xFF4BE8FF)
val BrandBlue = Color(0xFF2174FF)
val BrandViolet = Color(0xFF8C3CFF)
val BrandMagenta = Color(0xFFE82EC8)
val BrandDeepNavy = Color(0xFF050814)
val BrandCardDark = Color(0xFF0C0A1E)
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
    val glassMid: Color,
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
        Color(0xFF050814),
        Color(0xFF080E1E),
        Color(0xFF050914)
    ),
    ambientOrbPrimary = Color(0xFF3E187C),
    ambientOrbSecondary = Color(0xFF093A8A),
    glassStart = Color(0xF5110F24),
    glassMid = Color(0xFA090717),
    glassEnd = Color(0xFC070A18),
    glassElevatedStart = Color(0xFC141129),
    glassElevatedEnd = Color(0xFF070614),
    glassBorderTop = Color(0xA69F90EE),
    glassBorderBottom = Color(0x703D78DB),
    dockStart = Color(0xF7110D24),
    dockEnd = Color(0xFC070A18),
    dockSelectedPill = Color(0xDC6B2EFF),
    obsidianCoreTop = Color(0xFF16112C),
    obsidianCoreBottom = Color(0xFF070614)
)

val LocalLiquidPalette = staticCompositionLocalOf { DarkLiquidPalette }

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF041022),
    primaryContainer = Color(0xFF281856),
    onPrimaryContainer = Color(0xFFF3ECFF),
    secondary = BrandBlue,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF19143A),
    onSecondaryContainer = Color(0xFFD6CEFA),
    tertiary = BrandMagenta,
    onTertiary = Color(0xFFFFFFFF),
    background = BrandDeepNavy,
    onBackground = Color(0xFFF2EEFF),
    surface = BrandCardDark,
    onSurface = Color(0xFFF2EEFF),
    surfaceVariant = Color(0xFF14112B),
    onSurfaceVariant = Color(0xFFAFA7D2),
    surfaceTint = BrandViolet,
    inverseSurface = Color(0xFF16122E),
    inverseOnSurface = Color(0xFFF2EEFF),
    outline = Color(0x9E9F90EE),
    outlineVariant = Color(0x526A58C8),
    surfaceContainerLowest = Color(0xFF050814),
    surfaceContainerLow = Color(0xFF090717),
    surfaceContainer = Color(0xFF0C0A1E),
    surfaceContainerHigh = Color(0xFF141129),
    surfaceContainerHighest = Color(0xFF1C1738),
    error = ErrorRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3F1219),
    onErrorContainer = Color(0xFFFEE2E2)
)

/**
 * 全应用唯一暗黑深海军蓝主题（忽略系统日夜间与动态取色，保持系统栏通透暗色）
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun IProxyTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
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
