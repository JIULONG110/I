package com.iproxy.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iproxy.app.R
import com.iproxy.app.data.model.RoutingMode
import com.iproxy.app.data.model.ThemeMode
import com.iproxy.app.data.model.VpnStatus
import com.iproxy.app.ui.theme.IProxyTheme
import com.iproxy.app.ui.theme.LocalLiquidPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val ProtoTextPrimary = Color(0xFFF4EFFF)
internal val ProtoTextSecondary = Color(0xFFC2B7E8)
internal val ProtoTextMuted = Color(0xFFA99FD2)
internal val ProtoTextSubtle = Color(0xFF837AA8)
internal val ProtoDividerBlue = Color(0x637A6AD8)
internal val ProtoAccentSky = Color(0xFF4BE8FF)

/**
 * 深邃暗夜底景（页面空白处不散落额外粒子，粒子与光效仅严格呈现在各类 3D 玻璃容器内部）
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun LiquidBackdrop(
    status: VpnStatus,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = LocalLiquidPalette.current

    ProvideParticleAnimationClock {
        Box(modifier = modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to palette.backdropColors[0],
                        0.58f to palette.backdropColors[1],
                        1.0f to palette.backdropColors[2]
                    )
                )
            }

            CompositionLocalProvider(LocalContentColor provides ProtoTextPrimary) {
                content()
            }
        }
    }
}

/**
 * 深色高级 3D 实体玻璃卡片：压暗内底色 + 双层紫/洋红/电蓝立体边框 + 容器内部等比例快速上冲流光粒子
 * 无论处于高亮/开启态还是常态/关闭态，内部流光粒子始终快速向上冲动，同时清晰区分真实状态。
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevated: Boolean = false,
    isActive: Boolean = false,
    accentBorder: Color? = null,
    particleCount: Int = 26,
    seedOffsetSec: Float = 0f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val reduceMotion = rememberReduceMotionState()
    val shape = RoundedCornerShape(cornerRadius)
    val effectiveActive = isActive || accentBorder != null
    val effectiveIntensity = if (effectiveActive) 0.72f else 0.45f

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = cornerRadius,
                    active = effectiveActive,
                    elevated = elevated,
                    accentBorder = accentBorder,
                    innerBevelInsetDp = 3.dp
                )
            }
            .then(clickableModifier)
    ) {
        if (particleCount > 0) {
            BlueConstellationParticlesCanvas(
                particleCount = particleCount,
                reduceMotion = reduceMotion,
                densityMultiplier = 0.28f,
                timeOffsetSec = seedOffsetSec,
                intensity = effectiveIntensity,
                radiusScale = 0.76f,
                modifier = Modifier.matchParentSize()
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalContentColor provides ProtoTextPrimary) {
                content()
            }
        }
    }
}

/**
 * 原型 1:1 智能分流模式卡：选中态与未选中态均具实体玻璃 3D 边缘与高速蓝色流动粒子，
 * 选中项以亮钴蓝玻璃与高亮青边框一目了然区分真实模式。
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
        cornerRadius = 24.dp,
        particleCount = 24,
        seedOffsetSec = 0.9f
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
                RoutingMode.entries.forEachIndexed { idx, mode ->
                    val label = when (mode) {
                        RoutingMode.RULE_BYPASS_CN -> "绕过大陆"
                        RoutingMode.GLOBAL_PROXY -> "全局代理"
                        RoutingMode.DIRECT -> "直连"
                    }
                    PrototypeModeButton(
                        label = label,
                        selected = routingMode == mode,
                        reduceMotion = reduceMotion,
                        seedOffsetSec = idx * 1.15f,
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
                color = ProtoTextMuted,
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
    seedOffsetSec: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(13.dp)
    val textColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFFFFFFF) else Color(0xFFD6CEFA),
        animationSpec = tween(180),
        label = "modeBtnText"
    )

    Box(
        modifier = modifier
            .heightIn(min = 43.dp)
            .clip(shape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = 13.dp,
                    active = selected,
                    innerBevelInsetDp = 2.5.dp
                )
            }
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BlueConstellationParticlesCanvas(
            reduceMotion = reduceMotion,
            densityMultiplier = 0.72f,
            timeOffsetSec = seedOffsetSec,
            intensity = if (selected) 1.05f else 0.72f,
            radiusScale = 0.88f,
            modifier = Modifier.matchParentSize()
        )
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        )
    }
}

/**
 * 3D 实体玻璃横向胶囊开关：无论处于开启态或关闭态，均保持完整半圆两端与高速上冲粒子流，绝不因关闭而变灰或失去动效，
 * 同时通过亮紫-电蓝发光玻璃滑道 vs 深靛紫玻璃、滑块左右位置及亮芯色彩对比，一眼清晰辨识真实开关状态。
 */
@Composable
fun PrototypeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotionState()
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 0.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "switchThumb"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color(0xFFFFECFC) else Color(0xFFC2BAEE),
        animationSpec = tween(220),
        label = "switchThumbColor"
    )
    val thumbRingColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF4BE8FF) else Color(0xCC916BFF),
        animationSpec = tween(220),
        label = "switchThumbRing"
    )

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 28.dp)
            .clip(CircleShape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = 14.dp,
                    active = checked,
                    innerBevelInsetDp = 2.dp
                )
            }
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        BlueConstellationParticlesCanvas(
            reduceMotion = reduceMotion,
            densityMultiplier = 0.95f,
            intensity = if (checked) 1.15f else 0.84f,
            radiusScale = 0.72f,
            modifier = Modifier.matchParentSize()
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor)
                .border(width = 1.4.dp, color = thumbRingColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color(0xFFE82EC8) else Color(0xFF281A58))
            )
        }
    }
}

/**
 * 3D 实体玻璃复选框（按控件尺寸等比例流动粒子）：勾选与未勾选状态均保持饱满紫蓝玻璃与快速上冲粒子。
 */
@Composable
fun GlassCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotionState()
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(shape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = 7.dp,
                    active = checked,
                    innerBevelInsetDp = 1.5.dp
                )
            }
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        BlueConstellationParticlesCanvas(
            reduceMotion = reduceMotion,
            densityMultiplier = 0.95f,
            intensity = if (checked) 1.12f else 0.78f,
            radiusScale = 0.62f,
            modifier = Modifier.matchParentSize()
        )
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "已选中",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 3D 钴蓝实体玻璃主按钮：替代普通实心 Button，带立体蓝边与持续快速流动粒子
 */
@Composable
fun GlassActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 15.dp,
    seedOffsetSec: Float = 0.4f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val reduceMotion = rememberReduceMotionState()
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = cornerRadius,
                    active = enabled,
                    elevated = !enabled,
                    innerBevelInsetDp = 2.5.dp
                )
            }
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BlueConstellationParticlesCanvas(
            reduceMotion = reduceMotion,
            densityMultiplier = 0.68f,
            timeOffsetSec = seedOffsetSec,
            intensity = if (enabled) 1.05f else 0.62f,
            modifier = Modifier.matchParentSize()
        )
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) Color.White else ProtoTextSecondary
            ) {
                content()
            }
        }
    }
}

/**
 * 3D 深紫靛蓝玻璃次要按钮：保留连续快速上冲粒子与高饱和立体边框
 */
@Composable
fun GlassSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 14.dp,
    seedOffsetSec: Float = 1.2f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    content: @Composable RowScope.() -> Unit
) {
    val reduceMotion = rememberReduceMotionState()
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(shape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = cornerRadius,
                    active = false,
                    elevated = enabled,
                    accentBorder = if (enabled) Color(0xBA948CFF) else Color(0x6B6E5DC4),
                    innerBevelInsetDp = 2.5.dp
                )
            }
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BlueConstellationParticlesCanvas(
            reduceMotion = reduceMotion,
            densityMultiplier = 0.62f,
            timeOffsetSec = seedOffsetSec,
            intensity = if (enabled) 0.80f else 0.52f,
            modifier = Modifier.matchParentSize()
        )
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) ProtoTextPrimary else ProtoTextSecondary
            ) {
                content()
            }
        }
    }
}

/**
 * 轻量暗玻璃辅助胶囊按钮（替代弹窗/列表中的扁平 TextButton）
 */
@Composable
fun GlassGhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 13.dp,
    content: @Composable RowScope.() -> Unit
) {
    val reduceMotion = rememberReduceMotionState()
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(shape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = cornerRadius,
                    active = false,
                    innerBevelInsetDp = 2.dp
                )
            }
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BlueConstellationParticlesCanvas(
            reduceMotion = reduceMotion,
            densityMultiplier = 0.62f,
            timeOffsetSec = 2.1f,
            intensity = if (enabled) 0.72f else 0.48f,
            modifier = Modifier.matchParentSize()
        )
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) Color(0xFFD9CFFF) else ProtoTextMuted
            ) {
                content()
            }
        }
    }
}

/**
 * 3D 玻璃迷你图标按钮（按 30..36dp 尺寸等比例渲染快速上冲粒子，不拥挤不变灰）
 */
@Composable
fun GlassSmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isHighlighted: Boolean = false,
    sizeDp: Dp = 32.dp,
    cornerRadius: Dp = 10.dp,
    iconTint: Color = if (isHighlighted) Color.White else Color(0xFFE0D4FF)
) {
    val reduceMotion = rememberReduceMotionState()
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(shape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = cornerRadius,
                    active = isHighlighted,
                    elevated = true,
                    innerBevelInsetDp = 1.8.dp
                )
            }
            .alpha(if (enabled) 1f else 0.62f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BlueConstellationParticlesCanvas(
            reduceMotion = reduceMotion,
            densityMultiplier = 0.90f,
            intensity = if (isHighlighted) 1.05f else 0.78f,
            radiusScale = 0.72f,
            modifier = Modifier.matchParentSize()
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(sizeDp * 0.52f)
        )
    }
}

/**
 * 3D 玻璃可选胶囊标签（替换全部 FilterChip / 分组过滤 / 协议芯片 / 策略芯片 / 日志过滤）：
 * 选中与未选中均保持高速向上流光粒子，通过亮紫-电蓝发光态 vs 深紫靛低眩光玻璃清晰区分真实选中态。
 */
@Composable
fun GlassSelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    seedOffsetSec: Float = 0f,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 7.dp
) {
    val reduceMotion = rememberReduceMotionState()
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = cornerRadius,
                    active = selected,
                    innerBevelInsetDp = 2.dp
                )
            }
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BlueConstellationParticlesCanvas(
            reduceMotion = reduceMotion,
            densityMultiplier = 0.72f,
            timeOffsetSec = seedOffsetSec,
            intensity = if (selected) 1.08f else 0.72f,
            radiusScale = 0.85f,
            modifier = Modifier.matchParentSize()
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFFD6CEFA),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding)
        )
    }
}

/**
 * 3D 暗紫靛实体玻璃输入框（在可阅读深色玻璃底上嵌入微粒子流光框）
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val reduceMotion = rememberReduceMotionState()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = 16.dp,
                    active = false,
                    accentBorder = if (isError) Color(0xCCF87171) else null,
                    innerBevelInsetDp = 2.dp
                )
            }
    ) {
        BlueConstellationParticlesCanvas(
            reduceMotion = reduceMotion,
            densityMultiplier = 0.34f,
            intensity = 0.45f,
            radiusScale = 0.72f,
            modifier = Modifier.matchParentSize()
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label?.let { text -> { Text(text) } },
            placeholder = placeholder?.let { text -> { Text(text) } },
            leadingIcon = leadingIcon?.let { icon ->
                { Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF4BE8FF)) }
            },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            isError = isError,
            keyboardOptions = keyboardOptions,
            shape = shape,
            colors = prototypeTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 实体横向玻璃管充能进度条（完整圆头胶囊 + 等比例粒子 + 紫洋红电蓝流光脉冲波）
 */
@Composable
fun GlassLoadingBar(
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    val reduceMotion = rememberReduceMotionState()
    val transition = rememberInfiniteTransition(label = "glassLoadingBar")
    val sweep by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glassLoadingSweep"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .drawBehind {
                drawGlassCapsule3D(cornerRadiusDp = 5.dp, active = false, innerBevelInsetDp = 1.5.dp)
            }
    ) {
        BlueConstellationParticlesCanvas(
            reduceMotion = reduceMotion,
            densityMultiplier = 0.72f,
            intensity = 0.95f,
            radiusScale = 0.72f,
            modifier = Modifier.matchParentSize()
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val w = size.width
            if (progress != null) {
                val fillW = (w * progress.coerceIn(0f, 1f)).coerceAtLeast(h)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xE07B2EFF), Color(0xEBE82EC8), Color(0xFF3AEDFF))
                    ),
                    size = Size(fillW, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )
            } else {
                val beamW = w * 0.36f
                val left = sweep * w
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color(0xFFE82EC8), Color(0xFF3AEDFF), Color.Transparent),
                        startX = left,
                        endX = left + beamW
                    ),
                    topLeft = Offset(left.coerceIn(0f, w), 0f),
                    size = Size(beamW.coerceAtMost(w - left.coerceAtLeast(0f)).coerceAtLeast(0f), h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )
            }
        }
    }
}

/**
 * 3D 玻璃旋转全息加载指示器（紫-洋红-青流光弧 + 按卡片大小自适应比例粒子）
 */
@Composable
fun GlassLoadingIndicator(
    label: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val reduceMotion = rememberReduceMotionState()
    val transition = rememberInfiniteTransition(label = "glassSpinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerAngle"
    )

    if (compact) {
        Canvas(modifier = modifier.size(18.dp)) {
            drawArc(
                color = Color(0x4D947AE6),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, Color(0xFFE82EC8), Color(0xFF4BE8FF))
                ),
                startAngle = angle,
                sweepAngle = 220f,
                useCenter = false,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        return
    }

    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = 22.dp,
        isActive = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        drawGlassCapsule3D(cornerRadiusDp = 28.dp, active = true, innerBevelInsetDp = 2.dp)
                    },
                contentAlignment = Alignment.Center
            ) {
                BlueConstellationParticlesCanvas(
                    reduceMotion = reduceMotion,
                    densityMultiplier = 0.85f,
                    intensity = 1.0f,
                    modifier = Modifier.matchParentSize()
                )
                Canvas(modifier = Modifier.size(34.dp)) {
                    drawArc(
                        color = Color(0x52B588FF),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFFFDDF6),
                        startAngle = angle,
                        sweepAngle = 150f,
                        useCenter = false,
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            if (!label.isNullOrBlank()) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ProtoTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 3D 实体玻璃空状态卡片（内置完整圆头胶囊粒子容器）
 */
@Composable
fun GlassEmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val reduceMotion = rememberReduceMotionState()
    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        isActive = false,
        seedOffsetSec = 1.9f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 104.dp, height = 38.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        drawGlassCapsule3D(cornerRadiusDp = 19.dp, active = true, innerBevelInsetDp = 2.5.dp)
                    },
                contentAlignment = Alignment.Center
            ) {
                BlueConstellationParticlesCanvas(
                    reduceMotion = reduceMotion,
                    densityMultiplier = 0.90f,
                    intensity = 1.08f,
                    modifier = Modifier.matchParentSize()
                )
            }
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ProtoTextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = ProtoTextMuted,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(4.dp))
                action()
            }
        }
    }
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0x788F6CE8), Color(0x6E3EB8FF), Color.Transparent)
                )
            )
    )
}

/**
 * 统一全屏主题 3D 玻璃弹窗（替换所有系统灰色/哑光 AlertDialog）：
 * 压暗内部底色 + 饱和紫/洋红/电蓝边 + 等比例高速上冲粒子
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    headerExtra: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside
        )
    ) {
        ProvideParticleAnimationClock {
            val reduceMotion = rememberReduceMotionState()
            val shape = RoundedCornerShape(26.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .drawBehind {
                            drawGlassCapsule3D(
                                cornerRadiusDp = 26.dp,
                                active = false,
                                elevated = true,
                                accentBorder = Color(0xBFA28EFF),
                                innerBevelInsetDp = 3.5.dp
                            )
                        }
                ) {
                    BlueConstellationParticlesCanvas(
                        reduceMotion = reduceMotion,
                        densityMultiplier = 0.34f,
                        intensity = 0.46f,
                        radiusScale = 0.74f,
                        modifier = Modifier.matchParentSize()
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 6.dp, height = 22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFE82EC8), Color(0xFF4BE8FF))
                                        )
                                    )
                            )
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ProtoTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (headerExtra != null) {
                                headerExtra()
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        GlassDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompositionLocalProvider(LocalContentColor provides ProtoTextPrimary) {
                                content()
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        GlassDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            content = buttons
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3D 玻璃浮动菜单（带独立帧时钟与全菜单等比例上冲紫/洋红/电蓝粒子流）
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF090717),
        shape = shape,
        border = BorderStroke(1.2.dp, Color(0xCC9D8BFF)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
    ) {
        ProvideParticleAnimationClock {
            val reduceMotion = rememberReduceMotionState()
            Box(
                modifier = Modifier
                    .widthIn(min = 224.dp, max = 290.dp)
                    .drawBehind {
                        drawGlassCapsule3D(
                            cornerRadiusDp = 18.dp,
                            active = false,
                            elevated = true,
                            accentBorder = Color(0xB39E8FFF),
                            innerBevelInsetDp = 2.5.dp
                        )
                    }
            ) {
                BlueConstellationParticlesCanvas(
                    reduceMotion = reduceMotion,
                    densityMultiplier = 0.36f,
                    intensity = 0.50f,
                    radiusScale = 0.74f,
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp, horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun GlassDropdownItem(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    accentTint: Color = Color(0xFF4BE8FF),
    badge: String? = null
) {
    val rowShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(Color(0x47151030), rowShape)
            .border(0.75.dp, Color(0x528D78E8), rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = accentTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ProtoTextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (!badge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x6649249B))
                    .border(0.75.dp, Color(0x884BE8FF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFDDF6)
                )
            }
        }
    }
}

/**
 * 暗黑玻璃主按钮与输入框配色助手（保持紫-洋红-电蓝基调与清晰反差）
 */
@Composable
fun prototypePrimaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Color(0xFF5D2BFF),
    contentColor = Color(0xFFFFFFFF),
    disabledContainerColor = Color(0xFF171330),
    disabledContentColor = Color(0xFF837AA8)
)

@Composable
fun prototypeTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ProtoTextPrimary,
    unfocusedTextColor = ProtoTextPrimary,
    cursorColor = Color(0xFF4BE8FF),
    focusedBorderColor = Color(0xCC9D7AFF),
    unfocusedBorderColor = Color(0x6E6C5AC8),
    focusedContainerColor = Color(0x8F080614),
    unfocusedContainerColor = Color(0x7D070512),
    focusedLabelColor = Color(0xFFD9C7FF),
    unfocusedLabelColor = ProtoTextMuted,
    focusedPlaceholderColor = ProtoTextSubtle,
    unfocusedPlaceholderColor = ProtoTextSubtle
)

/**
 * 悬浮胶囊底部导航栏：1:1 对标图 1 74dp 实体玻璃胶囊、4 枚细描边图标、104 颗高速蓝色流动粒子与星链
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
        size = Size(20.8f * sx, 20.8f * sy),
        style = stroke
    )
}

private fun DrawScope.drawServerNodesGlyph(tint: Color, sx: Float, sy: Float, stroke: Stroke) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(4f * sx, 4f * sy),
        size = Size(20f * sx, 8f * sy),
        cornerRadius = CornerRadius(2.2f * sx, 2.2f * sy),
        style = stroke
    )
    drawCircle(color = tint, radius = 1.25f * sx, center = Offset(9f * sx, 8f * sy))
    drawRoundRect(
        color = tint,
        topLeft = Offset(4f * sx, 16f * sy),
        size = Size(20f * sx, 8f * sy),
        cornerRadius = CornerRadius(2.2f * sx, 2.2f * sy),
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
 * 黑曜石流纹光球「I」品牌 Logo（用于启动页、关于弹窗、通知图标与更新弹窗）
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
            listOf(accentGlow.copy(alpha = 0.88f), Color(0x884BE8FF))
        } else {
            listOf(Color(0x889F90EE), Color(0x554BE8FF))
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
