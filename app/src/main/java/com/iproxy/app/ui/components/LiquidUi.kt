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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.window.DialogProperties
import com.iproxy.app.R
import com.iproxy.app.data.model.VpnStatus
import com.iproxy.app.ui.theme.BrandBlue
import com.iproxy.app.ui.theme.ConnectedGreen
import com.iproxy.app.ui.theme.CyanAccent
import com.iproxy.app.ui.theme.ErrorRed
import com.iproxy.app.ui.theme.IProxyTheme
import com.iproxy.app.ui.theme.LocalLiquidPalette
import com.iproxy.app.ui.theme.WarningAmber

/**
 * 动态深海军蓝液态光影背景层：随连接状态平滑过渡光晕
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
        VpnStatus.ERROR -> Color(0xFF4F46E5)
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

            val primaryAlpha = 0.24f
            val secondaryAlpha = 0.20f
            val tertiaryAlpha = 0.14f

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
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/**
 * 深海磨砂玻璃卡片：半透明蓝黑渐变填充 + 青蓝微光描边，确保内部文字始终清晰高对比
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
            .then(clickableModifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/**
 * 统一的深海军蓝 TopAppBar 配色
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun darkTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color(0xCC081224),
    titleContentColor = MaterialTheme.colorScheme.onBackground,
    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
)

/**
 * 统一的深海军蓝 OutlinedTextField 配色
 */
@Composable
fun darkOutlinedTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = CyanAccent,
    focusedBorderColor = CyanAccent,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    errorBorderColor = ErrorRed,
    focusedLabelColor = CyanAccent,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    focusedLeadingIconColor = CyanAccent,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor = Color(0x660B1528),
    unfocusedContainerColor = Color(0x44091222)
)

/**
 * 统一的深海军蓝 FilterChip 配色与边框
 */
@Composable
fun darkFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = Color(0x990D182C),
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = Color(0xFF103559),
    selectedLabelColor = Color(0xFFE0F2FE),
    selectedLeadingIconColor = CyanAccent
)

@Composable
fun darkFilterChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
    selectedBorderColor = CyanAccent.copy(alpha = 0.7f),
    borderWidth = 1.dp,
    selectedBorderWidth = 1.dp
)

/**
 * 统一的深海军蓝弹窗容器，确保所有功能弹窗、编辑器与权限说明均无亮色背景或黑字
 */
@Composable
fun IProxyAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text?.let { content ->
            {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    content()
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconContentColor = CyanAccent,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        properties = properties
    )
}

/**
 * 悬浮液态胶囊底部导航栏（固定暗黑深海军蓝模式）
 *
 * 保留「连接、节点、分流、设置」四个入口、原有图标、点击区域与选中态，
 * 搭配平滑弧线流动的蓝色粒子星链与柔和蓝色选中高光，并遵循系统减弱动态效果设置。
 */
@Composable
fun LiquidBottomNavBar(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockShape = CircleShape
    val items = listOf(
        Triple(0, "连接", Icons.Rounded.PowerSettingsNew),
        Triple(1, "节点", Icons.Rounded.Dns),
        Triple(2, "分流", Icons.Rounded.Route),
        Triple(3, "设置", Icons.Rounded.Settings)
    )

    val reduceMotion = rememberReduceMotionState()
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
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(dockShape),
            shape = dockShape,
            color = Color.Transparent,
            shadowElevation = 14.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                DarkParticleDockBackdrop(
                    animatedSelectedIndex = animatedTabIndex,
                    tabCount = items.size,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.matchParentSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { (index, label, icon) ->
                        LiquidDockItem(
                            label = label,
                            icon = icon,
                            selected = currentTab == index,
                            reduceMotion = reduceMotion,
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
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animMs = if (reduceMotion) 0 else 250
    val contentColor by animateColorAsState(
        targetValue = if (selected) DarkNavPalette.IconSelected else DarkNavPalette.IconUnselected,
        animationSpec = tween(animMs),
        label = "dockText"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = DarkNavPalette.RippleBlue
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
            listOf(accentGlow.copy(alpha = 0.88f), Color(0x5538BDF8))
        } else {
            listOf(Color(0x557DD3FC), Color(0x201E3A5F))
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
    IProxyTheme {
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
