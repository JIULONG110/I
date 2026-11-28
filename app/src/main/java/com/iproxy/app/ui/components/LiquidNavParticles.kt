package com.iproxy.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
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
    val DockBgTop = Color(0xF5142334)
    val DockBgMid = Color(0xF70E1A29)
    val DockBgBottom = Color(0xFA08111E)
    val DockSheenTop = Color(0x22CBEFFF)
    val DockBorderTop = Color(0x9C6CB4EC)
    val DockBorderMid = Color(0x78588DC0)
    val DockBorderBottom = Color(0x5C3B6B9B)
    val DockInnerBevelTop = Color(0x4768ACE0)
    val DockInnerBevelBottom = Color(0x291B4C84)

    val PillGlowCenter = Color(0x881F8EFF)
    val PillGlowMid = Color(0x4715549E)
    val PillBodyTop = Color(0x9E197FF0)
    val PillBodyMid = Color(0xAD0F5EC4)
    val PillBodyBottom = Color(0xBA074099)
    val PillRimTop = Color(0xCC82D7FF)
    val PillRimBottom = Color(0x754095E6)

    val IconSelected = Color(0xFFD6EEFF)
    val IconUnselected = Color(0xFFADBED1)
    val RippleBlue = Color(0xFF53CAFF)

    val ParticleBlue = Color(0xFF428CFF)
    val ParticleSky = Color(0xFF52BEFF)
    val ParticleCyan = Color(0xFF54E0FF)
    val ParticleCoreIce = Color(0xFFA8ECFF)
    val LinkBlue = Color(0xFF57B3FF)

    // 3D 实体玻璃容器调色板（激活/开启态 vs 常态/关闭态均保持纯正深海蓝青玻璃层次）
    val GlassActiveTop = Color(0xB8187DE9)
    val GlassActiveMid = Color(0xC70D53B5)
    val GlassActiveBottom = Color(0xD4073580)
    val GlassIdleTop = Color(0xEB152437)
    val GlassIdleMid = Color(0xF20B1525)
    val GlassIdleBottom = Color(0xFA06101D)
    val GlassRimActiveTop = Color(0xDF82D8FF)
    val GlassRimActiveBottom = Color(0x8A3B91E3)
    val GlassRimIdleTop = Color(0x876296CA)
    val GlassRimIdleBottom = Color(0x472E5682)
    val GlassInnerBevelActive = Color(0x5288DCFF)
    val GlassInnerBevelIdle = Color(0x335B91C6)
    val GlassMeniscusTop = Color(0x2BCBF0FF)
}

internal object NavParticleField {
    private const val TWO_PI = (PI * 2.0).toFloat()
    private const val TRACK_WIDTH = 1.16f
    private const val TRACK_MARGIN = 0.08f
    private const val DEFAULT_SNAPSHOT_SEC = 4.2f
    private const val MAX_LINKS_PER_NODE = 10
    private const val SPEC_COUNT = 104

    // 104 颗确定性层次粒子：均匀黄金分割布局 + 明显快速的水平星链流动 (~0.11..0.27/s)
    val specs: List<NavParticleSpec> = List(SPEC_COUNT) { idx ->
        fun hash(salt: Int): Float {
            var x = (idx + 1).toLong() * 747796405L + salt.toLong() * 2891336453L
            x = (x xor (x ushr 16)) * 2246822507L
            x = (x xor (x ushr 13)) * 3266489909L
            x = x xor (x ushr 16)
            return ((x and 0xFFFFFL).toFloat() / 0x100000L.toFloat()).coerceIn(0f, 0.9999f)
        }

        val isBokeh = idx % 8 == 0
        val u0 = ((idx * 0.61803398875f + 0.137f) % 1.0f)
        val jitterX = (hash(1) - 0.5f) * 0.04f
        val baseX = (u0 + jitterX).let { ((it % 1f) + 1f) % 1f }
        val baseY = 0.14f + 0.72f * ((idx * 0.38196601125f + 0.271f) % 1.0f)
        val speedX = if (isBokeh) {
            0.112f + 0.078f * hash(3)
        } else {
            0.132f + 0.142f * hash(3)
        }
        val radiusDp = if (isBokeh) {
            1.75f + 0.85f * hash(4)
        } else {
            0.65f + 1.55f * hash(4)
        }
        val brightness = if (isBokeh) {
            0.28f + 0.16f * hash(5)
        } else {
            0.34f + 0.64f * hash(5)
        }
        NavParticleSpec(
            baseX = baseX,
            baseY = baseY,
            speedX = speedX,
            arcAmp = 0.045f + 0.105f * hash(6),
            arcFreq = 0.85f + 0.58f * hash(7),
            verticalFreq = 0.12f + 0.16f * hash(8),
            phase = TWO_PI * hash(9),
            radiusDp = radiusDp,
            brightness = brightness,
            hueMix = hash(10),
            twinkleFreq = 0.42f + 0.22f * hash(11),
            twinklePhase = TWO_PI * hash(12),
            isBokeh = isBokeh
        )
    }

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
     * 计算粒子在 [elapsedSec] + [timeOffsetSec] 时刻的归一化坐标与透明度权重，零分配写入缓冲。
     * [xSpeedScale] 用于窄容器保持清晰一致的物理流动线速度（dp/s）。
     */
    fun sampleInto(
        elapsedSec: Float,
        outNormX: FloatArray,
        outNormY: FloatArray,
        outAlpha: FloatArray,
        particleCount: Int = specs.size,
        xSpeedScale: Float = 1f,
        timeOffsetSec: Float = 0f
    ) {
        val count = particleCount.coerceIn(0, specs.size)
        val t = elapsedSec + timeOffsetSec
        for (i in 0 until count) {
            val s = specs[i]
            val rawX = s.baseX + t * s.speedX * xSpeedScale
            val wrapped = ((rawX % TRACK_WIDTH) + TRACK_WIDTH) % TRACK_WIDTH
            val normX = wrapped - TRACK_MARGIN
            val edgeFade = smoothstep(-0.04f, 0.06f, normX) * smoothstep(1.04f, 0.94f, normX)

            val arcAngle = TWO_PI * (normX * s.arcFreq + t * s.verticalFreq) + s.phase
            val normY = (s.baseY + s.arcAmp * sin(arcAngle)).coerceIn(0.10f, 0.90f)

            val twinkle = 0.68f + 0.32f * sin(TWO_PI * s.twinkleFreq * t + s.twinklePhase)
            outNormX[i] = normX
            outNormY[i] = normY
            outAlpha[i] = (s.brightness * edgeFade * twinkle).coerceIn(0f, 1f)
        }
    }

    internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    internal const val MAX_NODE_LINKS = MAX_LINKS_PER_NODE
}

/**
 * 根级共享帧时钟：全屏所有玻璃控件与粒子画布复用单一 Lifecycle 协程驱动，低耗电且 60/120Hz 同步流畅
 */
internal val LocalSharedParticleClockSec = staticCompositionLocalOf<Float?> { null }

@Composable
internal fun ProvideParticleAnimationClock(content: @Composable () -> Unit) {
    val reduceMotion = rememberReduceMotionState()
    val elapsedSec = rememberStandaloneParticleClockSec(reduceMotion = reduceMotion)
    CompositionLocalProvider(LocalSharedParticleClockSec provides elapsedSec) {
        content()
    }
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

@Composable
private fun rememberStandaloneParticleClockSec(reduceMotion: Boolean): Float {
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
    return elapsedSec
}

@Composable
internal fun rememberParticleAnimationClockSec(reduceMotion: Boolean): Float {
    val shared = LocalSharedParticleClockSec.current
    if (shared != null) return shared
    return rememberStandaloneParticleClockSec(reduceMotion = reduceMotion)
}

internal fun DrawScope.drawBlueParticleConstellation(
    elapsedSec: Float,
    particleCount: Int,
    referenceWidthDp: Float,
    normXBuf: FloatArray,
    normYBuf: FloatArray,
    alphaBuf: FloatArray,
    pxBuf: FloatArray,
    pyBuf: FloatArray,
    degreeBuf: IntArray,
    linkMaxDistDp: Float = 46f,
    timeOffsetSec: Float = 0f,
    intensity: Float = 1f,
    radiusScale: Float = 1f
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val activeCount = particleCount.coerceIn(0, NavParticleField.specs.size)
    val speedScale = (referenceWidthDp.dp.toPx() / w).coerceIn(0.85f, 4.8f)
    NavParticleField.sampleInto(
        elapsedSec = elapsedSec,
        outNormX = normXBuf,
        outNormY = normYBuf,
        outAlpha = alphaBuf,
        particleCount = activeCount,
        xSpeedScale = speedScale,
        timeOffsetSec = timeOffsetSec
    )

    for (i in 0 until activeCount) {
        pxBuf[i] = normXBuf[i] * w
        pyBuf[i] = normYBuf[i] * h
        degreeBuf[i] = 0
    }

    val safeIntensity = intensity.coerceIn(0.15f, 1.35f)
    val maxLinkDist = linkMaxDistDp.dp.toPx()
    val maxLinkDistSq = maxLinkDist * maxLinkDist
    val linkStrokePx = (0.70.dp.toPx() * radiusScale).coerceAtLeast(0.6f)

    for (i in 0 until activeCount) {
        val specI = NavParticleField.specs[i]
        if (specI.isBokeh || alphaBuf[i] <= 0.03f || degreeBuf[i] >= NavParticleField.MAX_NODE_LINKS) {
            continue
        }
        for (j in i + 1 until activeCount) {
            val specJ = NavParticleField.specs[j]
            if (specJ.isBokeh || alphaBuf[j] <= 0.03f || degreeBuf[j] >= NavParticleField.MAX_NODE_LINKS) {
                continue
            }
            val dx = pxBuf[i] - pxBuf[j]
            val dy = pyBuf[i] - pyBuf[j]
            val distSq = dx * dx + dy * dy
            if (distSq < maxLinkDistSq && distSq > 1f) {
                val dist = sqrt(distSq)
                val falloff = 1f - (dist / maxLinkDist)
                val baseLine = falloff * 0.34f * (0.48f + 0.52f * min(alphaBuf[i], alphaBuf[j]))
                val lineAlpha = (baseLine * safeIntensity).coerceIn(0f, 0.42f)
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

    for (i in 0 until activeCount) {
        val rawAlpha = alphaBuf[i]
        if (rawAlpha <= 0.02f) continue
        val alpha = (rawAlpha * safeIntensity).coerceIn(0f, 1f)
        val spec = NavParticleField.specs[i]
        val pos = Offset(pxBuf[i], pyBuf[i])
        val radiusPx = spec.radiusDp.dp.toPx() * radiusScale
        val baseColor = NavParticleField.colorForMix(spec.hueMix)

        // 柔和蓝色外光晕（层次 1）
        drawCircle(
            color = baseColor.copy(alpha = (alpha * 0.22f).coerceIn(0f, 0.34f)),
            radius = radiusPx * 2.55f,
            center = pos
        )
        if (!spec.isBokeh) {
            // 亮蓝色星核（层次 2）
            drawCircle(
                color = baseColor.copy(alpha = (alpha * 0.94f).coerceIn(0f, 0.96f)),
                radius = radiusPx,
                center = pos
            )
            // 前景高光冰青中心点（层次 3）
            if (rawAlpha > 0.44f && safeIntensity >= 0.45f) {
                drawCircle(
                    color = DarkNavPalette.ParticleCoreIce.copy(alpha = (alpha * 0.85f).coerceIn(0f, 0.92f)),
                    radius = (radiusPx * 0.50f).coerceAtLeast(0.8f),
                    center = pos
                )
            }
        } else {
            // 大体积半透明景深 Bokeh 光斑
            drawCircle(
                color = baseColor.copy(alpha = (alpha * 0.46f).coerceIn(0f, 0.48f)),
                radius = radiusPx * 1.48f,
                center = pos
            )
        }
    }
}

/**
 * 绘制图 1 同款 3D 圆角实体玻璃容器质感：深邃玻璃底色 + 底部幽蓝焦散辉光 + 顶部弯月高光 + 内缩立体玻璃壁描边 + 外层蓝色发光边缘
 */
internal fun DrawScope.drawGlassCapsule3D(
    cornerRadiusDp: Dp,
    active: Boolean,
    elevated: Boolean = false,
    accentBorder: Color? = null,
    innerBevelInsetDp: Dp = 3.dp
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val r = cornerRadiusDp.toPx().coerceAtMost(min(w, h) * 0.5f)
    val cr = CornerRadius(r, r)

    // 1. 深海蓝玻璃渐变主体
    val fillBrush = if (active) {
        Brush.linearGradient(
            0.0f to DarkNavPalette.GlassActiveTop,
            0.55f to DarkNavPalette.GlassActiveMid,
            1.0f to DarkNavPalette.GlassActiveBottom
        )
    } else {
        Brush.linearGradient(
            0.0f to if (elevated) Color(0xFA1F3047) else DarkNavPalette.GlassIdleTop,
            0.60f to DarkNavPalette.GlassIdleMid,
            1.0f to DarkNavPalette.GlassIdleBottom
        )
    }
    drawRoundRect(brush = fillBrush, cornerRadius = cr)

    // 2. 内部蓝色焦散径向折射光晕
    val glowCenter = if (active) Offset(w * 0.5f, h * 0.52f) else Offset(w * 0.5f, h * 1.02f)
    val glowRadius = (if (active) size.maxDimension * 0.72f else size.width * 0.65f).coerceAtLeast(1f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                if (active) DarkNavPalette.GlassGlowRadialActive else DarkNavPalette.GlassGlowRadialIdle,
                Color.Transparent
            ),
            center = glowCenter,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = glowCenter
    )

    // 3. 顶部弯月玻璃折射高光带
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                if (active) Color(0x36D7F3FF) else DarkNavPalette.GlassMeniscusTop,
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.44f
        ),
        cornerRadius = cr
    )

    // 4. 内缩立体玻璃内壁描边（构建实体试管壁厚感）
    val inset = innerBevelInsetDp.toPx().coerceAtMost(min(w, h) * 0.18f)
    if (inset >= 1f && w - inset * 2f > 4f && h - inset * 2f > 4f) {
        val innerR = (r - inset * 0.75f).coerceAtLeast(1f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    if (active) DarkNavPalette.GlassInnerBevelActive else DarkNavPalette.GlassInnerBevelIdle,
                    Color.Transparent
                )
            ),
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2f, h - inset * 2f),
            cornerRadius = CornerRadius(innerR, innerR),
            style = Stroke(width = 0.85.dp.toPx())
        )
    }

    // 5. 外层 3D 立体蓝色边框
    val borderBrush = when {
        accentBorder != null -> Brush.linearGradient(
            listOf(accentBorder.copy(alpha = 0.94f), accentBorder.copy(alpha = 0.50f))
        )
        active -> Brush.linearGradient(
            listOf(DarkNavPalette.GlassRimActiveTop, DarkNavPalette.GlassRimActiveBottom)
        )
        else -> Brush.linearGradient(
            listOf(DarkNavPalette.GlassRimIdleTop, DarkNavPalette.GlassRimIdleBottom)
        )
    }
    drawRoundRect(
        brush = borderBrush,
        cornerRadius = cr,
        style = Stroke(width = if (active || accentBorder != null) 1.25.dp.toPx() else 1.dp.toPx())
    )
}

/**
 * 可复用蓝色星链粒子画布：所有功能控件（开/关态）均可接入，支持种子偏移与明暗强度调节
 */
@Composable
internal fun BlueConstellationParticlesCanvas(
    particleCount: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    referenceWidthDp: Float = 400f,
    linkMaxDistDp: Float = 46f,
    timeOffsetSec: Float = 0f,
    intensity: Float = 1f,
    radiusScale: Float = 1f
) {
    val count = particleCount.coerceIn(1, NavParticleField.specs.size)
    val displayedCount = if (reduceMotion) (count * 0.22f).toInt().coerceAtLeast(3) else count
    val elapsedSec = rememberParticleAnimationClockSec(reduceMotion = reduceMotion)

    val normXBuf = remember(displayedCount) { FloatArray(displayedCount) }
    val normYBuf = remember(displayedCount) { FloatArray(displayedCount) }
    val alphaBuf = remember(displayedCount) { FloatArray(displayedCount) }
    val pxBuf = remember(displayedCount) { FloatArray(displayedCount) }
    val pyBuf = remember(displayedCount) { FloatArray(displayedCount) }
    val degreeBuf = remember(displayedCount) { IntArray(displayedCount) }

    Canvas(modifier = modifier) {
        drawBlueParticleConstellation(
            elapsedSec = elapsedSec,
            particleCount = displayedCount,
            referenceWidthDp = referenceWidthDp,
            normXBuf = normXBuf,
            normYBuf = normYBuf,
            alphaBuf = alphaBuf,
            pxBuf = pxBuf,
            pyBuf = pyBuf,
            degreeBuf = degreeBuf,
            linkMaxDistDp = linkMaxDistDp,
            timeOffsetSec = timeOffsetSec,
            intensity = intensity,
            radiusScale = radiusScale
        )
    }
}

/**
 * 底部悬浮胶囊导航栏背景层：深海军蓝玻璃底板 + 内外双层微光描边 + 104 颗高速星链粒子 + 柔和蓝玻璃选中高光
 */
@Composable
internal fun DarkParticleDockBackdrop(
    animatedSelectedIndex: Float,
    tabCount: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val elapsedSec = rememberParticleAnimationClockSec(reduceMotion = reduceMotion)
    val count = if (reduceMotion) 18 else NavParticleField.specs.size

    val normXBuf = remember(count) { FloatArray(count) }
    val normYBuf = remember(count) { FloatArray(count) }
    val alphaBuf = remember(count) { FloatArray(count) }
    val pxBuf = remember(count) { FloatArray(count) }
    val pyBuf = remember(count) { FloatArray(count) }
    val degreeBuf = remember(count) { IntArray(count) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val capsuleCorner = CornerRadius(h / 2f, h / 2f)

        // 1. 深海军蓝玻璃主底板 (.nav-shell)
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

        // 顶部细腻高光反射 + 内层 4dp 实体玻璃管壁 (.nav-shell::before)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(DarkNavPalette.DockSheenTop, Color.Transparent),
                startY = 0f,
                endY = h * 0.48f
            ),
            cornerRadius = capsuleCorner
        )
        val innerInset = 4.dp.toPx()
        val innerH = (h - innerInset * 2f).coerceAtLeast(1f)
        val innerCorner = CornerRadius(innerH / 2f, innerH / 2f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x9E182A3E), Color(0x8008111D)),
                startY = innerInset,
                endY = innerInset + innerH
            ),
            topLeft = Offset(innerInset, innerInset),
            size = Size((w - innerInset * 2f).coerceAtLeast(1f), innerH),
            cornerRadius = innerCorner
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(DarkNavPalette.DockInnerBevelTop, DarkNavPalette.DockInnerBevelBottom)
            ),
            topLeft = Offset(innerInset, innerInset),
            size = Size((w - innerInset * 2f).coerceAtLeast(1f), innerH),
            cornerRadius = innerCorner,
            style = Stroke(width = 0.95.dp.toPx())
        )

        // 2. 选中入口的蓝色玻璃滑动胶囊高光（置于粒子星链下方，确保选区内外星链光效均通透清晰）
        if (tabCount > 0) {
            val innerPad = 5.dp.toPx()
            val rowWidth = (w - innerPad * 2f).coerceAtLeast(1f)
            val slotWidth = rowWidth / tabCount.toFloat()
            val pillHeight = (h - innerPad * 2f).coerceAtLeast(1f)
            val pillWidth = (slotWidth - 2.dp.toPx()).coerceAtLeast(pillHeight)
            val centerX = innerPad + (animatedSelectedIndex.coerceIn(0f, (tabCount - 1).toFloat()) + 0.5f) * slotWidth
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
                    radius = pillWidth * 0.85f
                ),
                radius = pillWidth * 0.85f,
                center = Offset(centerX, centerY)
            )

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        DarkNavPalette.PillBodyTop,
                        DarkNavPalette.PillBodyMid,
                        DarkNavPalette.PillBodyBottom
                    ),
                    start = Offset(pillLeft, innerPad),
                    end = Offset(pillLeft + pillWidth * 0.8f, innerPad + pillHeight)
                ),
                topLeft = Offset(pillLeft, innerPad),
                size = Size(pillWidth, pillHeight),
                cornerRadius = pillCorner
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(DarkNavPalette.PillRimTop, DarkNavPalette.PillRimBottom),
                    start = Offset(pillLeft, innerPad),
                    end = Offset(pillLeft + pillWidth, innerPad + pillHeight)
                ),
                topLeft = Offset(pillLeft, innerPad),
                size = Size(pillWidth, pillHeight),
                cornerRadius = pillCorner,
                style = Stroke(width = 1.1.dp.toPx())
            )
        }

        // 3. 104 颗持续高速流动蓝色粒子与星链连线（位于选中高光之上、图标文字之下）
        drawBlueParticleConstellation(
            elapsedSec = elapsedSec,
            particleCount = count,
            referenceWidthDp = 400f,
            normXBuf = normXBuf,
            normYBuf = normYBuf,
            alphaBuf = alphaBuf,
            pxBuf = pxBuf,
            pyBuf = pyBuf,
            degreeBuf = degreeBuf,
            linkMaxDistDp = 48f,
            intensity = 1.08f
        )

        // 4. 外缘蓝色玻璃主描边
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    DarkNavPalette.DockBorderTop,
                    DarkNavPalette.DockBorderMid,
                    DarkNavPalette.DockBorderBottom
                )
            ),
            cornerRadius = capsuleCorner,
            style = Stroke(width = 1.15.dp.toPx())
        )
    }
}
