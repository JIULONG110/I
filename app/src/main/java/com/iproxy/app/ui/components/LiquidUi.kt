package com.iproxy.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iproxy.app.R
import com.iproxy.app.data.model.ThemeMode
import com.iproxy.app.data.model.VpnStatus
import com.iproxy.app.ui.theme.BrandBlue
import com.iproxy.app.ui.theme.ConnectedGreen
import com.iproxy.app.ui.theme.CyanAccent
import com.iproxy.app.ui.theme.ErrorRed
import com.iproxy.app.ui.theme.IProxyTheme
import com.iproxy.app.ui.theme.LocalLiquidPalette
import com.iproxy.app.ui.theme.WarningAmber

/**
 * 动态液态光影背景层：随连接状态与日/夜间模式实时流动过渡
 */
@Composable
fun LiquidBackdrop(
    status: VpnStatus,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = LocalLiquidPalette.current
    val transition = rememberInfiniteTransition(label = "liquid_backdrop")

    val driftA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftA"
    )
    val driftB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftB"
    )

    val targetOrbPrimary = when (status) {
        VpnStatus.CONNECTED -> ConnectedGreen
        VpnStatus.CONNECTING -> WarningAmber
        VpnStatus.ERROR -> ErrorRed
        else -> palette.ambientOrbPrimary
    }
    val targetOrbSecondary = when (status) {
        VpnStatus.CONNECTED -> CyanAccent
        VpnStatus.CONNECTING -> BrandBlue
        VpnStatus.ERROR -> Color(0xFF6366F1)
        else -> palette.ambientOrbSecondary
    }

    val orbPrimary by animateColorAsState(
        targetValue = targetOrbPrimary,
        animationSpec = tween(700),
        label = "orbPrimary"
    )
    val orbSecondary by animateColorAsState(
        targetValue = targetOrbSecondary,
        animationSpec = tween(700),
        label = "orbSecondary"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(palette.backdropColors))

            val primaryAlpha = if (palette.isDark) 0.24f else 0.28f
            val secondaryAlpha = if (palette.isDark) 0.20f else 0.22f
            val tertiaryAlpha = if (palette.isDark) 0.14f else 0.16f

            val c1 = Offset(
                x = size.width * (0.18f + 0.18f * driftA),
                y = size.height * (0.12f + 0.10f * driftB)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orbPrimary.copy(alpha = primaryAlpha), Color.Transparent),
                    center = c1,
                    radius = size.minDimension * 0.78f
                ),
                radius = size.minDimension * 0.78f,
                center = c1
            )

            val c2 = Offset(
                x = size.width * (0.86f - 0.22f * driftB),
                y = size.height * (0.44f + 0.14f * driftA)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orbSecondary.copy(alpha = secondaryAlpha), Color.Transparent),
                    center = c2,
                    radius = size.minDimension * 0.74f
                ),
                radius = size.minDimension * 0.74f,
                center = c2
            )

            val c3 = Offset(
                x = size.width * (0.30f + 0.35f * driftB),
                y = size.height * (0.86f - 0.10f * driftA)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orbPrimary.copy(alpha = tertiaryAlpha), Color.Transparent),
                    center = c3,
                    radius = size.minDimension * 0.68f
                ),
                radius = size.minDimension * 0.68f,
                center = c3
            )
        }
        content()
    }
}

/**
 * 液态磨砂玻璃卡片：半透明渐变填充 + 对角高光描边
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    elevated: Boolean = false,
    accentBorder: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalLiquidPalette.current
    val shape = RoundedCornerShape(cornerRadius)

    val fillBrush = Brush.linearGradient(
        colors = if (elevated) {
            listOf(palette.glassElevatedStart, palette.glassElevatedEnd)
        } else {
            listOf(palette.glassStart, palette.glassEnd)
        }
    )

    val borderBrush = if (accentBorder != null) {
        Brush.linearGradient(
            colors = listOf(accentBorder.copy(alpha = 0.9f), accentBorder.copy(alpha = 0.45f))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(palette.glassBorderTop, palette.glassBorderBottom)
        )
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(brush = fillBrush, shape = shape)
            .border(
                width = if (accentBorder != null) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = shape
            )
            .then(clickableModifier),
        content = content
    )
}

/**
 * 悬浮液态胶囊底部导航栏
 *
 * 暗黑模式下呈深海军蓝玻璃质感、蓝色流动粒子与星链连线、柔和蓝色选中高光，
 * 并自动响应系统「移除动画 / 减弱动态效果」设置；浅色模式沿用通透液态玻璃效果。
 */
@Composable
fun LiquidBottomNavBar(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidPalette.current
    val dockShape = if (palette.isDark) CircleShape else RoundedCornerShape(28.dp)
    val items = listOf(
        Triple(0, "连接", Icons.Rounded.PowerSettingsNew),
        Triple(1, "节点", Icons.Rounded.Dns),
        Triple(2, "分流", Icons.Rounded.Route),
        Triple(3, "设置", Icons.Rounded.Settings)
    )

    val reduceMotion = if (palette.isDark) rememberReduceMotionState() else false
    val animatedTabIndex by animateFloatAsState(
        targetValue = currentTab.coerceIn(0, items.lastIndex).toFloat(),
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "darkDockSelectedSlide"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val dockModifier = if (palette.isDark) {
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(dockShape)
        } else {
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(dockShape)
                .background(
                    brush = Brush.linearGradient(listOf(palette.dockStart, palette.dockEnd)),
                    shape = dockShape
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(palette.glassBorderTop, palette.glassBorderBottom)),
                    shape = dockShape
                )
        }

        Surface(
            modifier = dockModifier,
            shape = dockShape,
            color = Color.Transparent,
            shadowElevation = if (palette.isDark) 14.dp else 8.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (palette.isDark) {
                    DarkParticleDockBackdrop(
                        animatedSelectedIndex = animatedTabIndex,
                        tabCount = items.size,
                        reduceMotion = reduceMotion,
                        modifier = Modifier.matchParentSize()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (palette.isDark) 6.dp else 8.dp,
                            vertical = if (palette.isDark) 5.dp else 6.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { (index, label, icon) ->
                        LiquidDockItem(
                            label = label,
                            icon = icon,
                            selected = currentTab == index,
                            isDarkDock = palette.isDark,
                            reduceMotion = reduceMotion,
                            selectedPillColor = palette.dockSelectedPill,
                            onClick = { onSelectTab(index) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidDockItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    isDarkDock: Boolean,
    reduceMotion: Boolean,
    selectedPillColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemShape = if (isDarkDock) CircleShape else RoundedCornerShape(20.dp)
    val animMs = if (reduceMotion) 0 else 250

    // 暗黑模式下选中态由底层滑动蓝色玻璃高光统一渲染，避免叠出额外白圈
    val pillColor by animateColorAsState(
        targetValue = when {
            isDarkDock -> Color.Transparent
            selected -> selectedPillColor
            else -> Color.Transparent
        },
        animationSpec = tween(animMs),
        label = "dockPill"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isDarkDock && selected -> DarkNavPalette.IconSelected
            isDarkDock -> DarkNavPalette.IconUnselected
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(animMs),
        label = "dockText"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .clip(itemShape)
            .background(pillColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = if (isDarkDock) DarkNavPalette.RippleBlue else Color.Unspecified
                ),
                onClick = onClick
            )
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * 顶部快速切换「日间 / 夜间 / 跟随系统」液态胶囊按钮
 */
@Composable
fun LiquidThemeToggleButton(
    themeMode: ThemeMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidPalette.current
    val (icon, label) = when (themeMode) {
        ThemeMode.LIGHT -> Icons.Rounded.LightMode to "日间"
        ThemeMode.DARK -> Icons.Rounded.DarkMode to "夜间"
        ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto to "自动"
    }

    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (palette.isDark) Color(0x88151E30) else Color(0xCCFFFFFF),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(palette.glassBorderTop, palette.glassBorderBottom))
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "切换日夜间模式",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 黑曜石流纹光球「I」品牌 Logo（用于启动页、首页抬头、设置与更新弹窗）
 */
@Composable
fun BlackBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    accentGlow: Color? = null
) {
    val shape = RoundedCornerShape(size * 0.26f)
    val borderBrush = Brush.linearGradient(
        colors = if (accentGlow != null) {
            listOf(accentGlow.copy(alpha = 0.88f), Color(0x55FFFFFF))
        } else {
            listOf(Color(0x55E2E8F0), Color(0x2064748B))
        }
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .border(width = (size * 0.028f).coerceAtLeast(1.dp), brush = borderBrush, shape = shape)
    ) {
        Image(
            painter = painterResource(id = R.drawable.brand_logo),
            contentDescription = "I 品牌 Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF040812, widthDp = 412)
@Composable
private fun LiquidBottomNavBarDarkPreview() {
    IProxyTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF040812))
                .padding(vertical = 16.dp)
        ) {
            LiquidBottomNavBar(
                currentTab = 0,
                onSelectTab = {}
            )
        }
    }
}
