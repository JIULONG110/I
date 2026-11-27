package com.iproxy.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class NavParticleSpec(
    val baseX: Float,
    val baseY: Float,
    val speedX: Float,
    val arcAmp: Float,
    val arcFreq: Float,
    val verticalFreq: Float,
    val phase: Float,
    val radiusDp: Float,
    val brightness: Float,
    val hueMix: Float,
    val shimmerFreq: Float,
    val shimmerPhase: Float,
    val isBokeh: Boolean
)

internal object DarkNavPalette {
    val DockBgTop = Color(0xED061121)
    val DockBgMid = Color(0xE30A1A30)
    val DockBgBottom = Color(0xF0040B17)
    val DockSheenTop = Color(0x182E8CE6)
    val DockBorderTop = Color(0x6248A3F5)
    val DockBorderMid = Color(0x3622B8E6)
    val DockBorderBottom = Color(0x1E10325E)
    val DockInnerBevelTop = Color(0x325BB5FF)
    val DockInnerBevelBottom = Color(0x141A4C8A)

    val PillGlowCenter = Color(0x522898EB)
    val PillGlowMid = Color(0x2B15549E)
    val PillBodyTop = Color(0x602274BA)
    val PillBodyMid = Color(0x4212477D)
    val PillBodyBottom = Color(0x52092952)
    val PillRimTop = Color(0x7856B8F5)
    val PillRimBottom = Color(0x281E5EB8)

    val IconSelected = Color(0xFF66C7FF)
    val IconUnselected = Color(0xFFAEC7E6)
    val RippleBlue = Color(0xFF38BDF8)

    val ParticleBlue = Color(0xFF2B7FFF)
    val ParticleSky = Color(0xFF4CA8FF)
    val ParticleCyan = Color(0xFF22D3EE)
    val LinkBlue = Color(0xFF3A9BFF)
}

internal object NavParticleField {
    private const val TWO_PI = (PI * 2.0).toFloat()
    private const val TRACK_WIDTH = 1.18f
    private const val TRACK_MARGIN = 0.09f
    private const val DEFAULT_SNAPSHOT_SEC = 4.2f
    private const val MAX_LINKS_PER_NODE = 3

    // 32 颗分层蓝色粒子（10 颗深景柔和光斑 + 22 颗中景流线连线光点），平滑弧线持续流动
    val specs: List<NavParticleSpec> = listOf(
        // 深景柔和光斑层（较慢视差漂移）
        NavParticleSpec(0.05f, 0.34f, 0.058f, 0.11f, 1.05f, 0.14f, 0.4f, 3.1f, 0.34f, 0.20f, 0.22f, 0.2f, true),
        NavParticleSpec(0.16f, 0.68f, 0.068f, 0.12f, 0.90f, 0.13f, 1.8f, 2.8f, 0.32f, 0.55f, 0.20f, 1.1f, true),
        NavParticleSpec(0.28f, 0.28f, 0.054f, 0.10f, 1.15f, 0.15f, 3.1f, 3.3f, 0.30f, 0.80f, 0.18f, 2.4f, true),
        NavParticleSpec(0.40f, 0.72f, 0.066f, 0.12f, 1.00f, 0.16f, 4.2f, 2.6f, 0.36f, 0.35f, 0.21f, 3.5f, true),
        NavParticleSpec(0.52f, 0.36f, 0.062f, 0.11f, 1.10f, 0.14f, 5.0f, 3.0f, 0.31f, 0.65f, 0.19f, 4.1f, true),
        NavParticleSpec(0.64f, 0.65f, 0.056f, 0.13f, 0.85f, 0.15f, 0.9f, 3.2f, 0.29f, 0.15f, 0.23f, 5.2f, true),
        NavParticleSpec(0.75f, 0.30f, 0.072f, 0.10f, 1.05f, 0.13f, 2.3f, 2.7f, 0.33f, 0.75f, 0.20f, 0.8f, true),
        NavParticleSpec(0.87f, 0.70f, 0.060f, 0.12f, 1.10f, 0.15f, 3.7f, 2.9f, 0.30f, 0.45f, 0.17f, 1.9f, true),
        NavParticleSpec(0.95f, 0.46f, 0.055f, 0.09f, 1.20f, 0.14f, 5.5f, 2.5f, 0.32f, 0.90f, 0.22f, 2.8f, true),
        NavParticleSpec(0.11f, 0.52f, 0.065f, 0.11f, 0.95f, 0.12f, 4.8f, 2.7f, 0.31f, 0.60f, 0.18f, 4.6f, true),

        // 中景连线微粒层（约 8~12 秒完整横穿胶囊的清晰丝滑流体节奏）
        NavParticleSpec(0.02f, 0.22f, 0.118f, 0.14f, 1.25f, 0.20f, 0.5f, 1.50f, 0.82f, 0.85f, 0.26f, 0.3f, false),
        NavParticleSpec(0.07f, 0.78f, 0.102f, 0.13f, 1.10f, 0.18f, 1.9f, 1.30f, 0.72f, 0.30f, 0.24f, 1.4f, false),
        NavParticleSpec(0.13f, 0.38f, 0.136f, 0.15f, 1.35f, 0.22f, 3.2f, 1.70f, 0.90f, 0.65f, 0.28f, 2.5f, false),
        NavParticleSpec(0.18f, 0.66f, 0.108f, 0.12f, 1.15f, 0.17f, 4.7f, 1.35f, 0.70f, 0.18f, 0.23f, 3.7f, false),
        NavParticleSpec(0.24f, 0.24f, 0.128f, 0.14f, 1.30f, 0.21f, 1.1f, 1.60f, 0.85f, 0.94f, 0.27f, 4.9f, false),
        NavParticleSpec(0.29f, 0.54f, 0.098f, 0.16f, 1.00f, 0.19f, 2.7f, 1.20f, 0.64f, 0.50f, 0.22f, 0.9f, false),
        NavParticleSpec(0.35f, 0.82f, 0.124f, 0.13f, 1.20f, 0.22f, 4.1f, 1.65f, 0.86f, 0.74f, 0.25f, 2.1f, false),
        NavParticleSpec(0.41f, 0.28f, 0.110f, 0.14f, 1.40f, 0.18f, 5.6f, 1.40f, 0.76f, 0.26f, 0.24f, 3.3f, false),
        NavParticleSpec(0.47f, 0.60f, 0.132f, 0.12f, 1.10f, 0.20f, 0.8f, 1.72f, 0.92f, 0.88f, 0.29f, 4.4f, false),
        NavParticleSpec(0.52f, 0.34f, 0.096f, 0.15f, 1.25f, 0.16f, 2.4f, 1.25f, 0.68f, 0.42f, 0.21f, 5.6f, false),
        NavParticleSpec(0.58f, 0.76f, 0.120f, 0.13f, 1.20f, 0.19f, 3.8f, 1.55f, 0.80f, 0.60f, 0.26f, 1.1f, false),
        NavParticleSpec(0.63f, 0.20f, 0.142f, 0.14f, 1.38f, 0.23f, 5.2f, 1.70f, 0.88f, 0.92f, 0.27f, 2.3f, false),
        NavParticleSpec(0.69f, 0.48f, 0.104f, 0.15f, 1.05f, 0.17f, 1.5f, 1.32f, 0.68f, 0.22f, 0.22f, 3.5f, false),
        NavParticleSpec(0.74f, 0.80f, 0.126f, 0.12f, 1.20f, 0.21f, 2.9f, 1.60f, 0.84f, 0.78f, 0.25f, 4.7f, false),
        NavParticleSpec(0.80f, 0.30f, 0.112f, 0.14f, 1.30f, 0.19f, 4.4f, 1.42f, 0.74f, 0.48f, 0.24f, 5.9f, false),
        NavParticleSpec(0.85f, 0.64f, 0.138f, 0.13f, 1.15f, 0.21f, 0.7f, 1.62f, 0.86f, 0.70f, 0.28f, 0.6f, false),
        NavParticleSpec(0.90f, 0.26f, 0.116f, 0.15f, 1.28f, 0.20f, 2.1f, 1.48f, 0.78f, 0.86f, 0.23f, 1.8f, false),
        NavParticleSpec(0.94f, 0.72f, 0.129f, 0.12f, 1.12f, 0.18f, 3.5f, 1.52f, 0.80f, 0.36f, 0.26f, 3.0f, false),
        NavParticleSpec(0.98f, 0.42f, 0.106f, 0.14f, 1.24f, 0.19f, 4.9f, 1.38f, 0.72f, 0.62f, 0.22f, 4.2f, false),
        NavParticleSpec(0.21f, 0.84f, 0.122f, 0.11f, 1.10f, 0.17f, 1.7f, 1.45f, 0.76f, 0.68f, 0.25f, 5.1f, false),
        NavParticleSpec(0.44f, 0.18f, 0.134f, 0.13f, 1.32f, 0.22f, 3.4f, 1.58f, 0.82f, 0.90f, 0.24f, 2.7f, false),
        NavParticleSpec(0.77f, 0.18f, 0.114f, 0.12f, 1.18f, 0.20f, 5.4f, 1.46f, 0.78f, 0.44f, 0.23f, 1.3f, false)
    )

    fun colorForMix(hueMix: Float): Color {
        val clamped = hueMix.coerceIn(0f, 1f)
        return if (clamped < 0.5f) {
            lerp(DarkNavPalette.ParticleBlue, DarkNavPalette.ParticleSky, clamped * 2f)
        } else {
            lerp(DarkNavPalette.ParticleSky, DarkNavPalette.ParticleCyan, (clamped - 0.5f) * 2f)
        }
    }

    fun initialSnapshotTimeSec(): Float = DEFAULT_SNAPSHOT_SEC

    /**
     * 计算所有粒子在 [elapsedSec] 时刻的归一化坐标 (normX, normY) 与可见度权重。
     * 通过持续平稳的弧线位移体现清晰流动感，避免大幅闪烁替代移动。
     */
    fun sampleInto(
        elapsedSec: Float,
        outNormX: FloatArray,
        outNormY: FloatArray,
        outAlpha: FloatArray
    ) {
        val count = specs.size
        for (i in 0 until count) {
            val s = specs[i]
            val rawX = s.baseX + elapsedSec * s.speedX
            val wrapped = ((rawX % TRACK_WIDTH) + TRACK_WIDTH) % TRACK_WIDTH
            val normX = wrapped - TRACK_MARGIN
            val edgeFade = smoothstep(-0.02f, 0.11f, normX) * smoothstep(1.02f, 0.89f, normX)

            val arcAngle = TWO_PI * (normX * s.arcFreq + elapsedSec * s.verticalFreq) + s.phase
            val normY = (s.baseY + s.arcAmp * sin(arcAngle)).coerceIn(0.10f, 0.90f)

            // 仅保留微弱呼吸过渡，保持稳定光点流动而不闪烁
            val steadyGlow = 0.90f + 0.10f * sin(TWO_PI * s.shimmerFreq * elapsedSec + s.shimmerPhase)
            // 轻微柔和中心文本与图标带，保持导航文字清晰不遮挡
            val centerDistance = abs(normY - 0.50f)
            val legibilityAttenuation = 0.72f + 0.28f * smoothstep(0.08f, 0.36f, centerDistance)

            outNormX[i] = normX
            outNormY[i] = normY
            outAlpha[i] = (s.brightness * edgeFade * steadyGlow * legibilityAttenuation).coerceIn(0f, 1f)
        }
    }

    internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    internal const val MAX_NODE_LINKS = MAX_LINKS_PER_NODE
}

internal fun isSystemReduceMotionEnabled(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    }.getOrDefault(1f)
    return scale <= 0f || !ValueAnimator.areAnimatorsEnabled()
}

@Composable
internal fun rememberReduceMotionState(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reduceMotion by remember(context) { mutableStateOf(isSystemReduceMotionEnabled(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reduceMotion = isSystemReduceMotionEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return reduceMotion
}

/**
 * 暗黑模式底部导航专属背景层：深海军蓝玻璃底板 + 克制蓝边 + 持续流动蓝色粒子星链 + 柔和蓝色选中高光
 */
@Composable
internal fun DarkParticleDockBackdrop(
    animatedSelectedIndex: Float,
    tabCount: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var elapsedSec by remember { mutableFloatStateOf(NavParticleField.initialSnapshotTimeSec()) }
    LaunchedEffect(reduceMotion, isResumed) {
        if (reduceMotion || !isResumed) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val deltaSec = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    elapsedSec += deltaSec
                }
                lastNanos = frameNanos
            }
        }
    }

    val count = NavParticleField.specs.size
    val normXBuf = remember { FloatArray(count) }
    val normYBuf = remember { FloatArray(count) }
    val alphaBuf = remember { FloatArray(count) }
    val pxBuf = remember { FloatArray(count) }
    val pyBuf = remember { FloatArray(count) }
    val degreeBuf = remember { IntArray(count) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val capsuleCorner = CornerRadius(h / 2f, h / 2f)

        // 1. 深海军蓝 / 蓝黑玻璃底板渐变
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    DarkNavPalette.DockBgTop,
                    DarkNavPalette.DockBgMid,
                    DarkNavPalette.DockBgBottom
                )
            ),
            cornerRadius = capsuleCorner
        )

        // 顶部柔和蓝色玻璃反光层
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(DarkNavPalette.DockSheenTop, Color.Transparent),
                startY = 0f,
                endY = h * 0.55f
            ),
            cornerRadius = capsuleCorner
        )

        // 2. 选中入口的柔和蓝色高光（随 Tab 切换丝滑移动）
        if (tabCount > 0) {
            val innerHPadding = 6.dp.toPx()
            val innerVPadding = 5.dp.toPx()
            val rowWidth = (w - innerHPadding * 2f).coerceAtLeast(1f)
            val slotWidth = rowWidth / tabCount.toFloat()
            val pillHeight = (h - innerVPadding * 2f).coerceAtLeast(1f)
            val pillWidth = (slotWidth - 2.dp.toPx()).coerceAtLeast(pillHeight)
            val centerX = innerHPadding + (animatedSelectedIndex.coerceIn(0f, (tabCount - 1).toFloat()) + 0.5f) * slotWidth
            val centerY = h * 0.5f
            val pillLeft = centerX - pillWidth * 0.5f
            val pillCorner = CornerRadius(pillHeight / 2f, pillHeight / 2f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DarkNavPalette.PillGlowCenter,
                        DarkNavPalette.PillGlowMid,
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = pillWidth * 0.88f
                ),
                radius = pillWidth * 0.88f,
                center = Offset(centerX, centerY)
            )

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DarkNavPalette.PillBodyTop,
                        DarkNavPalette.PillBodyMid,
                        DarkNavPalette.PillBodyBottom
                    ),
                    startY = innerVPadding,
                    endY = innerVPadding + pillHeight
                ),
                topLeft = Offset(pillLeft, innerVPadding),
                size = Size(pillWidth, pillHeight),
                cornerRadius = pillCorner
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(DarkNavPalette.PillRimTop, DarkNavPalette.PillRimBottom),
                    start = Offset(pillLeft, innerVPadding),
                    end = Offset(pillLeft + pillWidth, innerVPadding + pillHeight)
                ),
                topLeft = Offset(pillLeft, innerVPadding),
                size = Size(pillWidth, pillHeight),
                cornerRadius = pillCorner,
                style = Stroke(width = 0.9.dp.toPx())
            )
        }

        // 3. 持续平稳流动的蓝色粒子与星链连线（零 GC 分配）
        NavParticleField.sampleInto(
            elapsedSec = elapsedSec,
            outNormX = normXBuf,
            outNormY = normYBuf,
            outAlpha = alphaBuf
        )
        for (i in 0 until count) {
            pxBuf[i] = normXBuf[i] * w
            pyBuf[i] = normYBuf[i] * h
            degreeBuf[i] = 0
        }

        val maxLinkDist = min(w * 0.165f, h * 1.25f)
        val maxLinkDistSq = maxLinkDist * maxLinkDist
        val linkStrokePx = 0.85.dp.toPx()

        for (i in 0 until count) {
            val specI = NavParticleField.specs[i]
            if (specI.isBokeh || alphaBuf[i] <= 0.04f || degreeBuf[i] >= NavParticleField.MAX_NODE_LINKS) {
                continue
            }
            for (j in i + 1 until count) {
                val specJ = NavParticleField.specs[j]
                if (specJ.isBokeh || alphaBuf[j] <= 0.04f || degreeBuf[j] >= NavParticleField.MAX_NODE_LINKS) {
                    continue
                }
                val dx = pxBuf[i] - pxBuf[j]
                val dy = pyBuf[i] - pyBuf[j]
                val distSq = dx * dx + dy * dy
                if (distSq < maxLinkDistSq && distSq > 1f) {
                    val dist = sqrt(distSq)
                    val falloff = 1f - (dist / maxLinkDist)
                    val lineAlpha = (falloff * falloff * 0.34f * min(alphaBuf[i], alphaBuf[j]))
                        .coerceIn(0f, 0.34f)
                    if (lineAlpha > 0.015f) {
                        drawLine(
                            color = DarkNavPalette.LinkBlue.copy(alpha = lineAlpha),
                            start = Offset(pxBuf[i], pyBuf[i]),
                            end = Offset(pxBuf[j], pyBuf[j]),
                            strokeWidth = linkStrokePx,
                            cap = StrokeCap.Round
                        )
                        degreeBuf[i]++
                        degreeBuf[j]++
                        if (degreeBuf[i] >= NavParticleField.MAX_NODE_LINKS) break
                    }
                }
            }
        }

        for (i in 0 until count) {
            val alpha = alphaBuf[i]
            if (alpha <= 0.02f) continue
            val spec = NavParticleField.specs[i]
            val pos = Offset(pxBuf[i], pyBuf[i])
            val radiusPx = spec.radiusDp.dp.toPx()
            val baseColor = NavParticleField.colorForMix(spec.hueMix)

            // 仅使用蓝/青蓝分层光晕与核心圆点，严格排除白色圆点
            drawCircle(
                color = baseColor.copy(alpha = alpha * if (spec.isBokeh) 0.18f else 0.14f),
                radius = radiusPx * 3.1f,
                center = pos
            )
            drawCircle(
                color = baseColor.copy(alpha = alpha * if (spec.isBokeh) 0.32f else 0.34f),
                radius = radiusPx * 1.80f,
                center = pos
            )
            if (!spec.isBokeh) {
                drawCircle(
                    color = baseColor.copy(alpha = (alpha * 0.90f).coerceIn(0f, 0.90f)),
                    radius = radiusPx,
                    center = pos
                )
            }
        }

        // 4. 导航胶囊外缘与内缘蓝色玻璃双层描边
        val bevelInset = 1.4.dp.toPx()
        val bevelHeight = (h - bevelInset * 2f).coerceAtLeast(1f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(DarkNavPalette.DockInnerBevelTop, DarkNavPalette.DockInnerBevelBottom)
            ),
            topLeft = Offset(bevelInset, bevelInset),
            size = Size((w - bevelInset * 2f).coerceAtLeast(1f), bevelHeight),
            cornerRadius = CornerRadius(bevelHeight / 2f, bevelHeight / 2f),
            style = Stroke(width = 0.75.dp.toPx())
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    DarkNavPalette.DockBorderTop,
                    DarkNavPalette.DockBorderMid,
                    DarkNavPalette.DockBorderBottom
                )
            ),
            cornerRadius = capsuleCorner,
            style = Stroke(width = 1.05.dp.toPx())
        )
    }
}
