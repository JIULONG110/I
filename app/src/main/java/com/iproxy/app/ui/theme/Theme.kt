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

val CyanAccent = Color(0xFF53CAFF)
val BrandBlue = Color(0xFF168BFF)
val BrandDeepNavy = Color(0xFF050A14)
val BrandCardDark = Color(0xFF0E192A)
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
        Color(0xFF050912),
        Color(0xFF08111F),
        Color(0xFF050A14)
    ),
    ambientOrbPrimary = Color(0xFF0857AA),
    ambientOrbSecondary = Color(0xFF063C87),
    glassStart = Color(0xF51C293B),
    glassMid = Color(0xFA0B1422),
    glassEnd = Color(0xF50C1C34),
    glassElevatedStart = Color(0xFC172739),
    glassElevatedEnd = Color(0xFF08101C),
    glassBorderTop = Color(0x75709ACA),
    glassBorderBottom = Color(0x524679B5),
    dockStart = Color(0xF5142334),
    dockEnd = Color(0xFA08111E),
    dockSelectedPill = Color(0x8F1779E6),
    obsidianCoreTop = Color(0xFF1C293B),
    obsidianCoreBottom = Color(0xFF08101C)
)

val LocalLiquidPalette = staticCompositionLocalOf { DarkLiquidPalette }

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF031526),
    primaryContainer = Color(0xFF12365E),
    onPrimaryContainer = Color(0xFFEFF7FF),
    secondary = BrandBlue,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF142D4B),
    onSecondaryContainer = Color(0xFFC0D4EA),
    tertiary = Color(0xFF59BAFF),
    onTertiary = Color(0xFF031526),
    background = BrandDeepNavy,
    onBackground = Color(0xFFEFF7FF),
    surface = BrandCardDark,
    onSurface = Color(0xFFEFF7FF),
    surfaceVariant = Color(0xFF17263B),
    onSurfaceVariant = Color(0xFF9FB2C9),
    surfaceTint = BrandBlue,
    inverseSurface = Color(0xFF142338),
    inverseOnSurface = Color(0xFFEFF7FF),
    outline = Color(0x75709ACA),
    outlineVariant = Color(0x3B5D88B9),
    surfaceContainerLowest = Color(0xFF050A14),
    surfaceContainerLow = Color(0xFF0B1422),
    surfaceContainer = Color(0xFF0E192A),
    surfaceContainerHigh = Color(0xFF142338),
    surfaceContainerHighest = Color(0xFF1C293B),
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
