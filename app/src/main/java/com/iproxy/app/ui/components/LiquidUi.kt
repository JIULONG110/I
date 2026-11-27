package com.iproxy.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iproxy.app.R
import com.iproxy.app.data.model.RoutingMode
import com.iproxy.app.data.model.ThemeMode
import com.iproxy.app.data.model.VpnStatus
import com.iproxy.app.ui.theme.IProxyTheme
import com.iproxy.app.ui.theme.LocalLiquidPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val ProtoTextPrimary = Color(0xFFEFF7FF)
internal val ProtoTextSecondary = Color(0xFFAEBFD2)
internal val ProtoTextMuted = Color(0xFF9FB2C9)
internal val ProtoTextSubtle = Color(0xFF7890AA)
internal val ProtoDividerBlue = Color(0x5C5B93D8)
internal val ProtoAccentSky = Color(0xFF59BAFF)

/**
 * 深海军蓝动态光影底景（连接/断开均保持一致的低调深蓝光泽，避免纯黑死板或突兀变绿）
 */
@Suppress("UNUSED_PARAMETER")
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
            animation = tween(durationMillis = 12000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftA"
    )
    val driftB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftB"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 基准纵向渐变 #050912 -> #08111f (58%) -> #050a14
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to palette.backdropColors[0],
                    0.58f to palette.backdropColors[1],
                    1.0f to palette.backdropColors[2]
                )
            )

            // 居中反应舱弱蓝辉光 + 右下深海蓝光晕
            val midGlowCenter = Offset(size.width * 0.50f, size.height * 0.33f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x290C478F), Color.Transparent),
                    center = midGlowCenter,
                    radius = size.minDimension * 0.65f
                ),
                radius = size.minDimension * 0.65f,
                center = midGlowCenter
            )

            val bottomGlowCenter = Offset(size.width * 0.96f, size.height * 0.92f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2E0C3677), Color.Transparent),
                    center = bottomGlowCenter,
                    radius = size.minDimension * 0.72f
                ),
                radius = size.minDimension * 0.72f,
                center = bottomGlowCenter
            )

            // 左右边缘柔和环境深蓝光球（对应 .app::before / .app::after）
            val leftOrb = Offset(
                x = -size.width * 0.08f + size.width * 0.04f * driftA,
                y = size.height * (0.24f + 0.03f * driftB)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.ambientOrbPrimary.copy(alpha = 0.24f), Color.Transparent),
                    center = leftOrb,
                    radius = size.minDimension * 0.62f
                ),
                radius = size.minDimension * 0.62f,
                center = leftOrb
            )

            val rightOrb = Offset(
                x = size.width * (1.06f - 0.04f * driftB),
                y = size.height * (0.78f - 0.03f * driftA)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.ambientOrbSecondary.copy(alpha = 0.24f), Color.Transparent),
                    center = rightOrb,
                    radius = size.minDimension * 0.60f
                ),
                radius = size.minDimension * 0.60f,
                center = rightOrb
            )
        }
        CompositionLocalProvider(LocalContentColor provides ProtoTextPrimary) {
            content()
        }
    }
}

/**
 * 深石板蓝黑立体玻璃卡片：3 段对角渐变 + 底部深蓝径向底晕 + 顶部冷光高光描边
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevated: Boolean = false,
    accentBorder: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalLiquidPalette.current
    val shape = RoundedCornerShape(cornerRadius)

    val fillBrush = Brush.linearGradient(
        0.0f to if (elevated) Color(0xFA223249) else palette.glassStart,
        0.63f to palette.glassMid,
        1.0f to palette.glassEnd
    )

    val borderBrush = if (accentBorder != null) {
        Brush.linearGradient(
            colors = listOf(accentBorder.copy(alpha = 0.92f), accentBorder.copy(alpha = 0.52f))
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
            .drawBehind {
                // 底部微弱幽蓝光晕（对应 .card::before）
                val glowCenter = Offset(size.width * 0.5f, size.height * 1.05f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x381171F0), Color.Transparent),
                        center = glowCenter,
                        radius = size.width * 0.68f
                    ),
                    radius = size.width * 0.68f,
                    center = glowCenter
                )
                // 顶部细腻高光高线（对应 inset 0 1px 0 rgba(219, 238, 255, .14)）
                drawLine(
                    color = Color(0x24DBEEFF),
                    start = Offset(cornerRadius.toPx() * 0.55f, 1.dp.toPx()),
                    end = Offset(size.width - cornerRadius.toPx() * 0.55f, 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .border(
                width = if (accentBorder != null) 1.4.dp else 1.dp,
                brush = borderBrush,
                shape = shape
            )
            .then(clickableModifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides ProtoTextPrimary) {
            content()
        }
    }
}

/**
 * 原型 1:1 智能分流模式卡：选中按钮带与底栏完全一致的蓝色流动粒子与星链
 */
@Composable
fun PrototypeRoutingModeCard(
    routingMode: RoutingMode,
    onSelectMode: (RoutingMode) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "智能分流模式",
    useCompactDescriptions: Boolean = true
) {
    val reduceMotion = rememberReduceMotionState()
    val description = if (useCompactDescriptions) {
        when (routingMode) {
            RoutingMode.RULE_BYPASS_CN -> "大陆网络直连，其余流量通过代理转发"
            RoutingMode.GLOBAL_PROXY -> "除局域网外所有网络流量均通过代理转发"
            RoutingMode.DIRECT -> "所有网络流量均直接连接，不通过代理"
        }
    } else {
        routingMode.subtitle
    }

    LiquidGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 145.dp),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 13.dp, end = 13.dp, top = 14.dp, bottom = 13.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                color = ProtoTextPrimary,
                modifier = Modifier.padding(start = 2.dp, bottom = 13.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoutingMode.entries.forEach { mode ->
                    val label = when (mode) {
                        RoutingMode.RULE_BYPASS_CN -> "绕过大陆"
                        RoutingMode.GLOBAL_PROXY -> "全局代理"
                        RoutingMode.DIRECT -> "直连"
                    }
                    PrototypeModeButton(
                        label = label,
                        selected = routingMode == mode,
                        reduceMotion = reduceMotion,
                        onClick = { onSelectMode(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Color(0xFFAABBD0),
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .heightIn(min = 17.dp)
            )
        }
    }
}

@Composable
private fun PrototypeModeButton(
    label: String,
    selected: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(13.dp)
    val bgBrush = if (selected) {
        Brush.linearGradient(
            colors = listOf(Color(0x9E1779E6), Color(0xC2074097))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xF02D3848), Color(0xFA17202E))
        )
    }
    val borderColor = if (selected) Color(0xD179D1FF) else Color(0x667D91AD)
    val textColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFFFFFFF) else Color(0xFFE2EAF4),
        animationSpec = tween(180),
        label = "modeBtnText"
    )

    Box(
        modifier = modifier
            .heightIn(min = 43.dp)
            .clip(shape)
            .background(brush = bgBrush, shape = shape)
            .drawBehind {
                if (selected) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x551F8EFF), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.72f
                        ),
                        radius = size.maxDimension * 0.72f,
                        center = center
                    )
                    drawLine(
                        color = Color(0x47E5F9FF),
                        start = Offset(10.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 10.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                } else {
                    drawLine(
                        color = Color(0x1AE9F4FF),
                        start = Offset(10.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 10.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .border(width = 1.dp, color = borderColor, shape = shape)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            BlueConstellationParticlesCanvas(
                particleCount = 18,
                reduceMotion = reduceMotion,
                modifier = Modifier.matchParentSize(),
                referenceWidthDp = 400f
            )
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        )
    }
}

/**
 * 原型 1:1 胶囊切换开关（用于分流与设置开关）
 */
@Composable
fun PrototypeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 19.dp else 0.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "switchThumb"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF67C8FF) else Color(0x8A7E9DBB),
        animationSpec = tween(220),
        label = "switchBorder"
    )
    val trackBrush = if (checked) {
        Brush.horizontalGradient(listOf(Color(0xFF1174DF), Color(0xFF18A0FF)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF111B29), Color(0xFF111B29)))
    }

    Box(
        modifier = modifier
            .size(width = 46.dp, height = 27.dp)
            .clip(CircleShape)
            .background(brush = trackBrush, shape = CircleShape)
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(19.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFFF6FCFF) else Color(0xFFBECDE0))
        )
    }
}

/**
 * 暗黑玻璃主按钮与输入框配色助手，避免任何浅色/紫色默认样式
 */
@Composable
fun prototypePrimaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Color(0xFF168BFF),
    contentColor = Color(0xFFFFFFFF),
    disabledContainerColor = Color(0xFF1B2B41),
    disabledContentColor = Color(0xFF6B819B)
)

@Composable
fun prototypeTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ProtoTextPrimary,
    unfocusedTextColor = ProtoTextPrimary,
    cursorColor = Color(0xFF53CAFF),
    focusedBorderColor = Color(0xFF53CAFF),
    unfocusedBorderColor = Color(0x66709ACA),
    focusedContainerColor = Color(0xAA08111F),
    unfocusedContainerColor = Color(0x8808111F),
    focusedLabelColor = Color(0xFF79D1FF),
    unfocusedLabelColor = ProtoTextMuted,
    focusedPlaceholderColor = ProtoTextSubtle,
    unfocusedPlaceholderColor = ProtoTextSubtle
)

/**
 * 悬浮胶囊底部导航栏：1:1 对标原型 74dp 胶囊、4 枚细描边图标、104 颗高速蓝色流动粒子与星链
 */
@Composable
fun LiquidBottomNavBar(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockShape = CircleShape
    val items = listOf(
        0 to "连接",
        1 to "节点",
        2 to "分流",
        3 to "设置"
    )

    val reduceMotion = rememberReduceMotionState()
    val animatedTabIndex by animateFloatAsState(
        targetValue = currentTab.coerceIn(0, items.lastIndex).toFloat(),
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "darkDockSelectedSlide"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 428.dp)
                .fillMaxWidth()
                .height(74.dp)
                .clip(dockShape),
            shape = dockShape,
            color = Color.Transparent,
            shadowElevation = 14.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                DarkParticleDockBackdrop(
                    animatedSelectedIndex = animatedTabIndex,
                    tabCount = items.size,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.matchParentSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { (index, label) ->
                        LiquidDockItem(
                            tabIndex = index,
                            label = label,
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
    tabIndex: Int,
    label: String,
    selected: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animMs = if (reduceMotion) 0 else 220
    val contentColor by animateColorAsState(
        targetValue = if (selected) DarkNavPalette.IconSelected else DarkNavPalette.IconUnselected,
        animationSpec = tween(animMs),
        label = "dockText"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = DarkNavPalette.RippleBlue
                ),
                onClick = onClick
            )
            .padding(top = 5.dp, bottom = 4.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NavStrokeIcon(
            tabIndex = tabIndex,
            tint = contentColor,
            modifier = Modifier.size(23.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun NavStrokeIcon(
    tabIndex: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val sx = size.width / 28f
        val sy = size.height / 28f
        val strokePx = 2.25f * ((sx + sy) * 0.5f)
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (tabIndex) {
            0 -> drawPowerGlyph(tint, sx, sy, stroke)
            1 -> drawServerNodesGlyph(tint, sx, sy, stroke)
            2 -> drawSplitRouteGlyph(tint, sx, sy, stroke)
            else -> drawGearGlyph(tint, sx, sy, stroke)
        }
    }
}

private fun DrawScope.drawPowerGlyph(tint: Color, sx: Float, sy: Float, stroke: Stroke) {
    drawLine(
        color = tint,
        start = Offset(14f * sx, 3f * sy),
        end = Offset(14f * sx, 13f * sy),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
    drawArc(
        color = tint,
        startAngle = -55f,
        sweepAngle = 290f,
        useCenter = false,
        topLeft = Offset(3.6f * sx, 4.6f * sy),
        size = androidx.compose.ui.geometry.Size(20.8f * sx, 20.8f * sy),
        style = stroke
    )
}

private fun DrawScope.drawServerNodesGlyph(tint: Color, sx: Float, sy: Float, stroke: Stroke) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(4f * sx, 4f * sy),
        size = androidx.compose.ui.geometry.Size(20f * sx, 8f * sy),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f * sx, 2.2f * sy),
        style = stroke
    )
    drawCircle(color = tint, radius = 1.25f * sx, center = Offset(9f * sx, 8f * sy))
    drawRoundRect(
        color = tint,
        topLeft = Offset(4f * sx, 16f * sy),
        size = androidx.compose.ui.geometry.Size(20f * sx, 8f * sy),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f * sx, 2.2f * sy),
        style = stroke
    )
    drawCircle(color = tint, radius = 1.25f * sx, center = Offset(9f * sx, 20f * sy))
}

private fun DrawScope.drawSplitRouteGlyph(tint: Color, sx: Float, sy: Float, stroke: Stroke) {
    val leftPath = Path().apply {
        moveTo(7f * sx, 6f * sy)
        lineTo(7f * sx, 10.5f * sy)
        cubicTo(7f * sx, 13.5f * sy, 9.5f * sx, 15f * sy, 12.2f * sx, 15f * sy)
        lineTo(13.2f * sx, 15f * sy)
        cubicTo(15.6f * sx, 15f * sy, 17f * sx, 16.8f * sy, 17f * sx, 19.5f * sy)
        lineTo(17f * sx, 22f * sy)
    }
    drawPath(path = leftPath, color = tint, style = stroke)

    val rightPath = Path().apply {
        moveTo(21f * sx, 6f * sy)
        lineTo(21f * sx, 9f * sy)
        cubicTo(21f * sx, 11.8f * sy, 19.3f * sx, 13.5f * sy, 16.5f * sx, 13.5f * sy)
        lineTo(14.5f * sx, 15f * sy)
    }
    drawPath(path = rightPath, color = tint, style = stroke)

    drawCircle(color = tint, radius = 2.1f * sx, center = Offset(7f * sx, 4.2f * sy), style = stroke)
    drawCircle(color = tint, radius = 2.1f * sx, center = Offset(21f * sx, 4.2f * sy), style = stroke)
    drawCircle(color = tint, radius = 2.1f * sx, center = Offset(17f * sx, 23.8f * sy), style = stroke)
}

private fun DrawScope.drawGearGlyph(tint: Color, sx: Float, sy: Float, stroke: Stroke) {
    val cx = 14f * sx
    val cy = 14f * sy
    val outerR = 10.6f * sx
    val innerR = 7.7f * sx
    val teeth = 6
    val gearPath = Path()
    for (i in 0 until teeth) {
        val baseDeg = i * (360f / teeth) - 90f
        val a0 = (baseDeg - 13f) * (PI / 180.0)
        val a1 = (baseDeg - 7f) * (PI / 180.0)
        val a2 = (baseDeg + 7f) * (PI / 180.0)
        val a3 = (baseDeg + 13f) * (PI / 180.0)
        val p0 = Offset(cx + (innerR * cos(a0)).toFloat(), cy + (innerR * sin(a0)).toFloat())
        val p1 = Offset(cx + (outerR * cos(a1)).toFloat(), cy + (outerR * sin(a1)).toFloat())
        val p2 = Offset(cx + (outerR * cos(a2)).toFloat(), cy + (outerR * sin(a2)).toFloat())
        val p3 = Offset(cx + (innerR * cos(a3)).toFloat(), cy + (innerR * sin(a3)).toFloat())
        if (i == 0) gearPath.moveTo(p0.x, p0.y) else gearPath.lineTo(p0.x, p0.y)
        gearPath.lineTo(p1.x, p1.y)
        gearPath.lineTo(p2.x, p2.y)
        gearPath.lineTo(p3.x, p3.y)
    }
    gearPath.close()
    drawPath(path = gearPath, color = tint, style = stroke)
    drawCircle(color = tint, radius = 3.2f * sx, center = Offset(cx, cy), style = stroke)
}

/**
 * 黑曜石流纹光球「I」品牌 Logo（用于启动页、关于弹窗与更新弹窗）
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
            listOf(accentGlow.copy(alpha = 0.88f), Color(0x55709ACA))
        } else {
            listOf(Color(0x6679D1FF), Color(0x2E4679B5))
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

@Preview(showBackground = true, backgroundColor = 0xFF050A14, widthDp = 412)
@Composable
private fun LiquidBottomNavBarDarkPreview() {
    IProxyTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF050A14))
                .padding(vertical = 16.dp)
        ) {
            LiquidBottomNavBar(
                currentTab = 0,
                onSelectTab = {}
            )
        }
    }
}
