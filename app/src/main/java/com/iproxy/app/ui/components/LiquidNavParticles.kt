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
    val twinkleFreq: Float,
    val twinklePhase: Float,
    val isBokeh: Boolean
)

internal object DarkNavPalette {
    val DockBgTop = Color(0xEC061120)
    val DockBgMid = Color(0xE00A192E)
    val DockBgBottom = Color(0xEE040B16)
    val DockSheenTop = Color(0x162E8CE6)
    val DockBorderTop = Color(0x5C48A3F5)
    val DockBorderMid = Color(0x3022B8E6)
    val DockBorderBottom = Color(0x1A0F2F57)
    val DockInnerBevelTop = Color(0x2E5BB5FF)
    val DockInnerBevelBottom = Color(0x121A4C8A)

    val PillGlowCenter = Color(0x4E2898EB)
    val PillGlowMid = Color(0x2815549E)
    val PillBodyTop = Color(0x5C2274BA)
    val PillBodyMid = Color(0x3D12477D)
    val PillBodyBottom = Color(0x4E092952)
    val PillRimTop = Color(0x7056B8F5)
    val PillRimBottom = Color(0x241E5EB8)

    val IconSelected = Color(0xFF62C4FF)
    val IconUnselected = Color(0xFF9BB6D6)
    val RippleBlue = Color(0xFF38BDF8)

    val ParticleBlue = Color(0xFF2D7FF9)
    val ParticleCyan = Color(0xFF22D3EE)
    val ParticleSky = Color(0xFF4CA8FF)
    val LinkBlue = Color(0xFF3898EC)
}

internal object NavParticleField {
    private const val TWO_PI = (PI * 2.0).toFloat()
    private const val TRACK_WIDTH = 1.16f
    private const val TRACK_MARGIN = 0.08f
    private const val DEFAULT_SNAPSHOT_SEC = 4.2f
    private const val MAX_LINKS_PER_NODE = 3

    // 26 颗克制的层次粒子（10 颗柔和背景景深光斑 + 16 颗中景低亮度微粒，兼顾流畅度与清晰度）
    val specs: List<NavParticleSpec> = listOf(
        // 深景柔和光斑层
        NavParticleSpec(0.06f, 0.34f, 0.018f, 0.09f, 1.1f, 0.08f, 0.4f, 3.0f, 0.30f, 0.20f, 0.35f, 0.2f, true),
        NavParticleSpec(0.17f, 0.68f, 0.022f, 0.11f, 0.9f, 0.07f, 1.8f, 2.7f, 0.28f, 0.55f, 0.30f, 1.1f, true),
        NavParticleSpec(0.29f, 0.28f, 0.016f, 0.08f, 1.2f, 0.06f, 3.1f, 3.2f, 0.26f, 0.80f, 0.28f, 2.4f, true),
        NavParticleSpec(0.41f, 0.72f, 0.020f, 0.10f, 1.0f, 0.09f, 4.2f, 2.5f, 0.32f, 0.35f, 0.33f, 3.5f, true),
        NavParticleSpec(0.53f, 0.36f, 0.019f, 0.09f, 1.1f, 0.07f, 5.0f, 2.9f, 0.27f, 0.65f, 0.29f, 4.1f, true),
        NavParticleSpec(0.64f, 0.65f, 0.017f, 0.12f, 0.8f, 0.08f, 0.9f, 3.1f, 0.25f, 0.15f, 0.31f, 5.2f, true),
        NavParticleSpec(0.76f, 0.30f, 0.021f, 0.08f, 1.0f, 0.06f, 2.3f, 2.6f, 0.29f, 0.75f, 0.34f, 0.8f, true),
        NavParticleSpec(0.88f, 0.70f, 0.018f, 0.10f, 1.1f, 0.08f, 3.7f, 2.8f, 0.26f, 0.45f, 0.26f, 1.9f, true),
        NavParticleSpec(0.95f, 0.46f, 0.015f, 0.07f, 1.2f, 0.07f, 5.5f, 2.4f, 0.28f, 0.90f, 0.32f, 2.8f, true),
        NavParticleSpec(0.11f, 0.52f, 0.020f, 0.09f, 0.9f, 0.06f, 4.8f, 2.6f, 0.27f, 0.60f, 0.27f, 4.6f, true),

        // 中景连线微粒层
        NavParticleSpec(0.03f, 0.24f, 0.032f, 0.12f, 1.3f, 0.11f, 0.6f, 1.45f, 0.72f, 0.85f, 0.55f, 0.3f, false),
        NavParticleSpec(0.10f, 0.76f, 0.028f, 0.11f, 1.1f, 0.10f, 2.1f, 1.25f, 0.60f, 0.30f, 0.48f, 1.4f, false),
        NavParticleSpec(0.18f, 0.42f, 0.036f, 0.13f, 1.4f, 0.12f, 3.6f, 1.65f, 0.82f, 0.65f, 0.62f, 2.5f, false),
        NavParticleSpec(0.25f, 0.64f, 0.030f, 0.10f, 1.2f, 0.09f, 5.1f, 1.30f, 0.58f, 0.15f, 0.50f, 3.7f, false),
        NavParticleSpec(0.32f, 0.22f, 0.034f, 0.12f, 1.3f, 0.11f, 1.2f, 1.55f, 0.76f, 0.95f, 0.58f, 4.9f, false),
        NavParticleSpec(0.39f, 0.50f, 0.027f, 0.14f, 1.0f, 0.10f, 2.8f, 1.15f, 0.52f, 0.50f, 0.44f, 0.9f, false),
        NavParticleSpec(0.46f, 0.80f, 0.033f, 0.11f, 1.2f, 0.12f, 4.3f, 1.60f, 0.78f, 0.72f, 0.60f, 2.1f, false),
        NavParticleSpec(0.53f, 0.26f, 0.029f, 0.12f, 1.4f, 0.09f, 5.8f, 1.35f, 0.64f, 0.25f, 0.52f, 3.3f, false),
        NavParticleSpec(0.60f, 0.58f, 0.035f, 0.10f, 1.1f, 0.11f, 0.9f, 1.70f, 0.84f, 0.88f, 0.64f, 4.4f, false),
        NavParticleSpec(0.67f, 0.34f, 0.026f, 0.13f, 1.3f, 0.08f, 2.5f, 1.20f, 0.55f, 0.40f, 0.46f, 5.6f, false),
        NavParticleSpec(0.74f, 0.74f, 0.031f, 0.11f, 1.2f, 0.10f, 3.9f, 1.50f, 0.70f, 0.60f, 0.54f, 1.1f, false),
        NavParticleSpec(0.81f, 0.22f, 0.037f, 0.12f, 1.4f, 0.12f, 5.3f, 1.65f, 0.80f, 0.92f, 0.61f, 2.3f, false),
        NavParticleSpec(0.87f, 0.48f, 0.028f, 0.13f, 1.0f, 0.09f, 1.6f, 1.25f, 0.56f, 0.22f, 0.49f, 3.5f, false),
        NavParticleSpec(0.92f, 0.78f, 0.033f, 0.10f, 1.2f, 0.11f, 3.0f, 1.55f, 0.74f, 0.78f, 0.57f, 4.7f, false),
        NavParticleSpec(0.97f, 0.30f, 0.029f, 0.12f, 1.3f, 0.10f, 4.5f, 1.35f, 0.62f, 0.48f, 0.51f, 5.9f, false),
        NavParticleSpec(0.22f, 0.82f, 0.031f, 0.09f, 1.1f, 0.08f, 1.9f, 1.40f, 0.66f, 0.70f, 0.53f, 0.6f, false)
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
     * 计算第 [index] 颗粒子在 [elapsedSec] 时刻的归一化坐标 (normX, normY) 与可见度权重，
     * 返回写入到预分配缓冲区以零 GC 分配运行于动画帧。
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
            val edgeFade = smoothstep(0f, 0.14f, normX) * smoothstep(1f, 0.86f, normX)

            val arcAngle = TWO_PI * (normX * s.arcFreq + elapsedSec * s.verticalFreq) + s.phase
            val normY = (s.baseY + s.arcAmp * sin(arcAngle)).coerceIn(0.10f, 0.90f)

            val twinkle = 0.74f + 0.26f * sin(TWO_PI * s.twinkleFreq * elapsedSec + s.twinklePhase)
            // 轻微降低正中间文本图标区域亮度以保持导航可读性，边缘粒子保持生动层次
            val centerDistance = kotlin.math.abs(normY - 0.52f)
            val legibilityAttenuation = 0.78f + 0.22f * smoothstep(0.08f, 0.38f, centerDistance)

            outNormX[i] = normX
            outNormY[i] = normY
            outAlpha[i] = (s.brightness * edgeFade * twinkle * legibilityAttenuation).coerceIn(0f, 1f)
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
 * 暗黑模式底部导航专属背景层：深海军蓝玻璃底板 + 克制蓝边 + 流动蓝色粒子星链 + 柔和蓝色选中高光
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

            // 底层大范围青蓝柔光
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DarkNavPalette.PillGlowCenter,
                        DarkNavPalette.PillGlowMid,
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = pillWidth * 0.85f
                ),
                radius = pillWidth * 0.85f,
                center = Offset(centerX, centerY)
            )

            // 胶囊内玻璃蓝高光本体与克制边缘
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

        // 3. 持续平滑的蓝色粒子流动与克制星链连线
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

        val maxLinkDist = min(w * 0.145f, h * 1.15f)
        val maxLinkDistSq = maxLinkDist * maxLinkDist
        val linkStrokePx = 0.75.dp.toPx()

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
                    val lineAlpha = (falloff * falloff * 0.24f * min(alphaBuf[i], alphaBuf[j]))
                        .coerceIn(0f, 0.24f)
                    if (lineAlpha > 0.01f) {
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

            // 仅使用蓝/青蓝分层光晕与核心圆点，不使用任何白色高亮圆点
            drawCircle(
                color = baseColor.copy(alpha = alpha * if (spec.isBokeh) 0.16f else 0.12f),
                radius = radiusPx * 3.0f,
                center = pos
            )
            drawCircle(
                color = baseColor.copy(alpha = alpha * if (spec.isBokeh) 0.30f else 0.28f),
                radius = radiusPx * 1.85f,
                center = pos
            )
            if (!spec.isBokeh) {
                drawCircle(
                    color = baseColor.copy(alpha = (alpha * 0.85f).coerceIn(0f, 0.85f)),
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
